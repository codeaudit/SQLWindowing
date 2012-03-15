package com.sap.hadoop.windowing.runtime.mr

import java.io.IOException;
import java.util.ArrayList;

import org.apache.hadoop.hive.serde2.Deserializer;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;

import com.sap.hadoop.windowing.functions.AbstractTableFunction;
import com.sap.hadoop.windowing.io.WindowingInput;
import com.sap.hadoop.windowing.query.Query;
import com.sap.hadoop.windowing.query.QuerySpec;
import com.sap.hadoop.windowing.runtime.IPartition;
import com.sap.hadoop.windowing.runtime.Partition;
import com.sap.hadoop.windowing.runtime.Row;
import com.sap.hadoop.windowing.runtime.WindowingShell;

/**
 * responsible for creating the output partition in the Map Phase.
 * @author harish.butani
 *
 */
class MapPhaseExecTaskBase extends MapBase
{
	WindowingShell wshell
	Query qry
	MapPhasePartition partition
	OutputCollector<Writable, Writable> output
	Reporter reporter
	
	public void configure(JobConf job) 
	{
		super.configure(job);
		String qryStr = job.get(Job.WINDOWING_QUERY_STRING);
		wshell = new WindowingShell(job, new MRTranslator(), new MRExecutor())
		
		QuerySpec qSpec = JobBase.getQuerySpec(job)
		qry = wshell.translate(qSpec)
	}
	
	public void map(Writable key, Writable value,
		OutputCollector<Writable, Writable> output, Reporter reporter)
		throws IOException {
		this.output = output
		this.reporter = reporter
		partition << value
	}
		
	public void close() throws IOException 
	{
		AbstractTableFunction tFunc = qry.tableFunction;
		AbstractTableFunction inpTFunc = qry.inputtableFunction
		
		inpTFunc.input = partition
		
		while(tFunc.hasNext())
		{
			IPartition p = tFunc.next();
			Row orow = p.getRowObject();
			
			
			ArrayList o = []
			for(r in p)
			{
				o.clear()
				tFunc.getMapPhaseOutputShape() { name, type ->
					o << orow[name]
				}
				Writable mOutWritable = qry.mapPhase.outputSerDe.serialize(o, qry.mapPhase.outputOI)
				map(null, mOutWritable)
				output.collect(wkey, mOutWritable);
			}
		}
	}
}

class MapPhasePartition extends Partition
{
	MapPhasePartition(Query qry)
	{
		super(qry, null, qry.mapPhase.inputOI, qry.mapPhase.inputDeserializer, null)
	}
	
	boolean belongs(Writable o)
	{
		return true
	}
}
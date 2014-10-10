/**
 * 
 */
package org.icatproject.ijp.lsfbatch;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Calendar;
import java.util.Set;

import org.icatproject.ijp.lsfbatch.Bjobs;
import org.icatproject.ijp.batch.exceptions.InternalException;

public class BjobsTest {

	@Test
	public void test() {

		// Expected output fields and example (using bjobs -w)
		// JOBID   USER     STAT  QUEUE     FROM_HOST           EXEC_HOST            JOB_NAME       SUBMIT_TIME
		// 157356  scarf334 DONE  scarf      ui3.scarf.rl.ac.uk cn223.scarf.rl.ac.uk test-batchfile Aug  5 14:34
		
		String dummyOutput = "JOBID   USER    STAT  QUEUE      FROM_HOST   EXEC_HOST   JOB_NAME   SUBMIT_TIME\n"
				+ "157356  scarf334 DONE  scarf      ui3.scarf.rl.ac.uk cn223.scarf.rl.ac.uk test-batchfile Aug  5 14:34\n"
				+ "157357  scarf334 PEND  scarf      ui3.scarf.rl.ac.uk cn223.scarf.rl.ac.uk test-batchfile Jul 15 09:06\n";
		String job1Id = "157356";
		String job2Id = "157357";
		
		Calendar cal = Calendar.getInstance();
		
		try {
			Bjobs bjobs = new Bjobs(dummyOutput);
			
			Set<String> jobIds = bjobs.getJobIds();
			assertEquals("There should be two jobs", jobIds.size(), 2);
			
			Bjobs.Job job1 = bjobs.getJob( job1Id );
			assertEquals("job1 userid should be scarf334", "scarf334", job1.getUserId() );
			assertEquals("job1 status should be DONE", "DONE", job1.getStatus());
			assertEquals("job1 queue should be scarf", "scarf", job1.getQueue());
			assertEquals("job1 from-host should be ui3", "ui3.scarf.rl.ac.uk", job1.getFromHost());
			assertEquals("job1 exec-host should be cn223", "cn223.scarf.rl.ac.uk", job1.getExecHost());
			assertEquals("job1 name should be test-batchfile", "test-batchfile", job1.getJobName());
			
			cal.setTime(job1.getSubmitTime());
			assertEquals("job1 submit Month should be 7", 7, cal.get(Calendar.MONTH));
			assertEquals("job1 submit Day should be 5", 5, cal.get(Calendar.DAY_OF_MONTH));
			assertEquals("job1 submit Hour should be 14", 14, cal.get(Calendar.HOUR_OF_DAY));
			assertEquals("job1 submit Minute should be 34", 34, cal.get(Calendar.MINUTE));
			
			Bjobs.Job job2 = bjobs.getJob( job2Id );
			assertEquals("job2 userid should be scarf334", "scarf334", job2.getUserId() );
			assertEquals("job2 status should be PEND", "PEND", job2.getStatus());
			assertEquals("job2 queue should be scarf", "scarf", job2.getQueue());
			assertEquals("job2 from-host should be ui3", "ui3.scarf.rl.ac.uk", job2.getFromHost());
			assertEquals("job2 exec-host should be cn223", "cn223.scarf.rl.ac.uk", job2.getExecHost());
			assertEquals("job2 name should be test-batchfile", "test-batchfile", job2.getJobName());
			
			cal.setTime(job2.getSubmitTime());
			assertEquals("job2 submit Month should be 6", 6, cal.get(Calendar.MONTH));
			assertEquals("job2 submit Day should be 15", 15, cal.get(Calendar.DAY_OF_MONTH));
			assertEquals("job2 submit Hour should be 9", 9, cal.get(Calendar.HOUR_OF_DAY));
			assertEquals("job2 submit Minute should be 6", 6, cal.get(Calendar.MINUTE));
						
		} catch (InternalException e) {
			fail("Internal exception: " + e.getMessage() );
		}
		
	}
	
	@Test
	public void noJobsTest(){
		
		// It turns out that when no jobs are found, "No job found" etc. appears on stderr, not stdout!
		// So we really need to test empty stdout.
		
		String dummyOutput1 = "No job found\n";
		String dummyOutput2 = "No unfinished job found\n";
		String dummyOutput3 = "";
		String dummyOutput4 = "\n";
		
		Bjobs bjobs;
		Set<String> jobIds;
		
		try {
			
			bjobs = new Bjobs(dummyOutput1);
			
			jobIds = bjobs.getJobIds();
			assertEquals("There should be no jobs from dummyOutput1", jobIds.size(), 0);
			
		} catch( InternalException e ){
			fail("Internal exception: " + e.getMessage());
		}
		
		try {
			
			bjobs = new Bjobs(dummyOutput2);
			
			jobIds = bjobs.getJobIds();
			assertEquals("There should be no jobs from dummyOutput2", jobIds.size(), 0);
			
		} catch( InternalException e ){
			fail("Internal exception: " + e.getMessage());
		}
		
		try {
			
			bjobs = new Bjobs(dummyOutput3);
			
			jobIds = bjobs.getJobIds();
			assertEquals("There should be no jobs from dummyOutput3", jobIds.size(), 0);
			
		} catch( InternalException e ){
			fail("Internal exception: " + e.getMessage());
		}
		
		try {
			
			bjobs = new Bjobs(dummyOutput4);
			
			jobIds = bjobs.getJobIds();
			assertEquals("There should be no jobs from dummyOutput4", jobIds.size(), 0);
			
		} catch( InternalException e ){
			fail("Internal exception: " + e.getMessage());
		}
	}

}

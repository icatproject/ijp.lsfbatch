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
			assertEquals(jobIds.size(), 2);
			
			Bjobs.Job job1 = bjobs.getJob( job1Id );
			assertEquals("scarf334", job1.getUserId() );
			assertEquals("DONE", job1.getStatus());
			assertEquals("scarf", job1.getQueue());
			assertEquals("ui3.scarf.rl.ac.uk", job1.getFromHost());
			assertEquals("cn223.scarf.rl.ac.uk", job1.getExecHost());
			assertEquals("test-batchfile", job1.getJobName());
			
			cal.setTime(job1.getSubmitTime());
			assertEquals(7, cal.get(Calendar.MONTH));
			assertEquals(5, cal.get(Calendar.DAY_OF_MONTH));
			assertEquals(14, cal.get(Calendar.HOUR_OF_DAY));
			assertEquals(34, cal.get(Calendar.MINUTE));
			
			Bjobs.Job job2 = bjobs.getJob( job2Id );
			assertEquals("scarf334", job2.getUserId() );
			assertEquals("PEND", job2.getStatus());
			assertEquals("scarf", job2.getQueue());
			assertEquals("ui3.scarf.rl.ac.uk", job2.getFromHost());
			assertEquals("cn223.scarf.rl.ac.uk", job2.getExecHost());
			assertEquals("test-batchfile", job2.getJobName());
			
			cal.setTime(job2.getSubmitTime());
			assertEquals(6, cal.get(Calendar.MONTH));
			assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
			assertEquals(9, cal.get(Calendar.HOUR_OF_DAY));
			assertEquals(6, cal.get(Calendar.MINUTE));
						
		} catch (InternalException e) {
			fail("Internal exception: " + e.getMessage() );
		}
		
	}
	
	@Test
	public void noJobsTest(){
		
		// It turns out that when no jobs are found, "No job found" etc. appears on stdout, not stdin!
		// So we really need to test empty stdin.
		
		String dummyOutput1 = "No job found\n";
		String dummyOutput2 = "No unfinished job found\n";
		String dummyOutput3 = "";
		String dummyOutput4 = "\n";
		
		Bjobs bjobs;
		Set<String> jobIds;
		
		try {
			
			bjobs = new Bjobs(dummyOutput1);
			
			jobIds = bjobs.getJobIds();
			assertEquals(jobIds.size(), 0);
			
		} catch( InternalException e ){
			fail("Internal exception: " + e.getMessage());
		}
		
		try {
			
			bjobs = new Bjobs(dummyOutput2);
			
			jobIds = bjobs.getJobIds();
			assertEquals(jobIds.size(), 0);
			
		} catch( InternalException e ){
			fail("Internal exception: " + e.getMessage());
		}
		
		try {
			
			bjobs = new Bjobs(dummyOutput3);
			
			jobIds = bjobs.getJobIds();
			assertEquals(jobIds.size(), 0);
			
		} catch( InternalException e ){
			fail("Internal exception: " + e.getMessage());
		}
		
		try {
			
			bjobs = new Bjobs(dummyOutput4);
			
			jobIds = bjobs.getJobIds();
			assertEquals(jobIds.size(), 0);
			
		} catch( InternalException e ){
			fail("Internal exception: " + e.getMessage());
		}
	}

}

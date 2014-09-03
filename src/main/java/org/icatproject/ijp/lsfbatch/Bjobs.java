package org.icatproject.ijp.lsfbatch;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.icatproject.ijp.lsfbatch.exceptions.InternalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses and stores the output from a Platform LSF bjobs command.
 * Assumes one-job-per-line format, as delivered by "bjobs -w".
 * 
 * @author Brian Ritchie
 *
 */
public class Bjobs {
	
	final static Logger logger = LoggerFactory.getLogger(Bjobs.class);

	protected Map<String,Bjobs.Job> bJobs;
	
	public static class Job {
		
		protected String jobId;
		protected String userId;
		protected String status;
		protected String queue;
		protected String fromHost;
		protected String execHost;
		protected String jobName;
		protected Date submitTime;

		public String getJobId() {
			return jobId;
		}
		public String getUserId() {
			return userId;
		}
		public String getStatus() {
			return status;
		}
		public String getQueue() {
			return queue;
		}
		public String getFromHost() {
			return fromHost;
		}
		public String getExecHost() {
			return execHost;
		}
		public String getJobName() {
			return jobName;
		}
		public Date getSubmitTime() {
			return submitTime;
		}
	}
	
	public Bjobs(){
		
		bJobs = new HashMap<String, Bjobs.Job>();
	}
	
	public Bjobs(String bJobsOutput) throws InternalException{
		
		this();
		parseBjobsOutput( bJobsOutput );
	}
	
	public Bjobs.Job getJob( String jobId ){
		
		return bJobs.get( jobId );
	}
	
	public Set<String> getJobIds(){
		
		return bJobs.keySet();
	}
	
	public Collection<Bjobs.Job> getJobs(){
		return bJobs.values();
	}

	private void parseBjobsOutput(String bJobsOutput) throws InternalException {
		
		// Expected output fields and example (using bjobs -aw)
		// JOBID   USER     STAT  QUEUE     FROM_HOST           EXEC_HOST            JOB_NAME       SUBMIT_TIME
		// 157356  scarf334 DONE  scarf      ui3.scarf.rl.ac.uk cn223.scarf.rl.ac.uk test-batchfile Aug  5 14:34
		//
		// If no jobs are found, the output is the single line "No job found".  Or sometimes "No unfinished job found" (if -a is not specified)
		//
		// Notes:
		// - the submit time does not specify the year!  Resulting Dates will be in 1970; we set the current year from Calendar
		// - we assume that the job name does not contain whitespace.
		
		Pattern outputRowPattern = Pattern.compile("(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(.*)");
		Pattern noOutputPattern = Pattern.compile("No.* job found");
		DateFormat df = new SimpleDateFormat("yyyy MMM d HH:mm");

		Scanner scanner = new Scanner(bJobsOutput);
		while( scanner.hasNextLine()){
			String line = scanner.nextLine();
			Matcher m = outputRowPattern.matcher(line);
			if( ! m.matches() ){
				
				// Watch out for "No job found" and "No unfinished job found"
				
				Matcher noJobsMatcher = noOutputPattern.matcher(line);
				
				if( ! noJobsMatcher.matches() ){
					// Perhaps this is a little severe - could just write off this line?
					scanner.close();
					throw new InternalException("Bjobs: Unable to parse line: " + line );
				}
			} else {
				String jobId = m.group(1);
				if( jobId == null ){
					
					// Do we ever expect blank lines? How about the last line?
					// Ignore this line.
					
				} else if( ! "JOBID".equals(jobId) ){  // Ignore header line
					
					// Construct a LsfJob from this line
					
					Bjobs.Job job = new Bjobs.Job();
					job.jobId = jobId;
					job.userId = m.group(2);
					job.status = m.group(3);
					job.queue = m.group(4);
					job.fromHost = m.group(5);
					job.execHost = m.group(6);
					job.jobName = m.group(7);
					
					try {
						// Add the current year!
						String dateStr = Calendar.getInstance().get(Calendar.YEAR) + " " + m.group(8);
						logger.debug("Bjobs.parseBjobs: date-string is now: '" + dateStr + "'");
						job.submitTime = df.parse(dateStr);
					} catch (ParseException e) {
						scanner.close();
						throw new InternalException("Bjobs: Unable to parse submit time: " + e.getMessage() );
					}
					
					// And add it to the map
					
					bJobs.put( jobId, job);
				}
			}
		}
		scanner.close();
	}

}

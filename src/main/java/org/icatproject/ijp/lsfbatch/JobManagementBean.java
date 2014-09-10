package org.icatproject.ijp.lsfbatch;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.namespace.QName;

import org.icatproject.ICAT;
import org.icatproject.ICATService;
import org.icatproject.IcatException_Exception;
import org.icatproject.ijp.lsfbatch.exceptions.ForbiddenException;
import org.icatproject.ijp.lsfbatch.exceptions.InternalException;
import org.icatproject.ijp.lsfbatch.exceptions.ParameterException;
import org.icatproject.ijp.lsfbatch.exceptions.SessionException;
import org.icatproject.utils.CheckedProperties;
import org.icatproject.utils.ShellCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Session Bean implementation to manage job status
 */
@Stateless
public class JobManagementBean {

	public enum OutputType {
		STANDARD_OUTPUT, ERROR_OUTPUT;
	}

	private ICAT icat;

	private String defaultFamily;
	private Map<String, List<String>> families = new HashMap<>();
	private LsfUserPool lsfUserPool;

	private Path batchFileDir;

	// per-LSF user subdirectory used to hold job-specific directories
	
	private final static String lsfUserOutputDir = "jobsOutput";
	private final static String chars = "abcdefghijklmnpqrstuvwxyz";

	@PostConstruct
	void init() {

		try {
			CheckedProperties props = new CheckedProperties();
			props.loadFromFile(Constants.PROPERTIES_FILEPATH);

			String familiesList = props.getString("families.list");
			for (String mnemonic : familiesList.split("\\s+")) {
				if (defaultFamily == null) {
					defaultFamily = mnemonic;
				}
				String key = "families." + mnemonic;
				String[] members = props.getProperty(key).split("\\s+");
				families.put(mnemonic, new ArrayList<>(Arrays.asList(members)));
				logger.debug("Family " + mnemonic + " contains " + families.get(mnemonic));
			}
			if (defaultFamily == null) {
				String msg = "No families defined";
				logger.error(msg);
				throw new IllegalStateException(msg);
			}

			lsfUserPool = LsfUserPool.getInstance();
			for( String family : families.keySet() ){
				for( String lsfUserId : families.get(family) ){
					// See if any unfinished Jobs are assigned to this id
					boolean isAssigned = false;
					for (LsfJob job : entityManager.createNamedQuery(LsfJob.FIND_BY_BATCHUSERNAME, LsfJob.class)
							.setParameter("batchusername", lsfUserId).getResultList()) {
						String status = job.getStatus();
						if( status != null && ! status.equals("Completed") ){
							isAssigned = true;
						}
					}
					logger.debug("adding Lsf User: family '" + family + "', id '" + lsfUserId + "', assigned=" + isAssigned);
					lsfUserPool.addLsfUser(family, lsfUserId, isAssigned);
				}
			}

			batchFileDir = props.getPath("batchFileDir");
			if (!batchFileDir.toFile().exists()) {
				String msg = "jobOutputDir " + batchFileDir + "does not exist";
				logger.error(msg);
				throw new IllegalStateException(msg);
			}
			batchFileDir = batchFileDir.toAbsolutePath();

			if (props.has("javax.net.ssl.trustStore")) {
				System.setProperty("javax.net.ssl.trustStore",
						props.getProperty("javax.net.ssl.trustStore"));
			}
			URL icatUrl = props.getURL("icat.url");
			icatUrl = new URL(icatUrl, "ICATService/ICAT?wsdl");
			QName qName = new QName("http://icatproject.org", "ICATService");
			ICATService service = new ICATService(icatUrl, qName);
			icat = service.getICATPort();

			logger.info("Set up lsfbatch with default family " + defaultFamily);
		} catch (Exception e) {
			String msg = e.getClass() + " reports " + e.getMessage();
			logger.error(msg);
			throw new IllegalStateException(msg);
		}

	}

	private final static Logger logger = LoggerFactory.getLogger(JobManagementBean.class);
	private final static Random random = new Random();

	@PersistenceContext(unitName = "lsfbatch")
	private EntityManager entityManager;

	@Schedule(minute = "*/1", hour = "*")
	public void updateJobsFromBjobs() {
		try {

			// Need to run bjobs for *each* (active) user in the pool; glassfish user should be able to do this, no need for ssh
			
			List<String> activePoolUsers = getActivePoolUsers();
			logger.debug("Active pool users: " + activePoolUsers );
			
			for( String poolUserId : getActivePoolUsers() ){
			
				ShellCommand sc = new ShellCommand("bjobs", "-aw", "-u", poolUserId );
				if (sc.isError()) {
					// Astonishingly, "No job found" counts as an error!
					if( ! ("No job found".equals(sc.getStderr().trim())) ){
						throw new InternalException("Unable to query jobs via bjobs: " + sc.getStderr());
					}
				}
				String bJobsOutput = sc.getStdout().trim();
				if (bJobsOutput.isEmpty()) {
					// See if any jobs have completed without being noticed
					// In practice, bjobs output is unlikely to be empty; but may be "No jobs found" or similar
					// (but that may be on stderr, see above)
					cleanUpJobs( poolUserId );
					continue;
				}
	
				Bjobs bJobs = new Bjobs( bJobsOutput );
				Collection<Bjobs.Job> jobs = bJobs.getJobs();
				if( jobs.size() == 0 ){
					// No jobs found for this pool user, so see if we missed any becoming Completed
					cleanUpJobs( poolUserId );
					continue;
				}
				
				// Even if we do have some Bjobs, they might all be Completed,
				// in which case we can free up this pool id
				
				// NOTE: we are only using cleanUpJobs() to set the status of unfound jobs (to Complete)
				// once bjobs returns no jobs at all. It's possible that some may disappear before that
				// (but hopefully this is unlikely).
				
				int uncompletedJobs = 0;
				
				for (Bjobs.Job bjob : bJobs.getJobs()) {
					String id = bjob.getJobId();
					String status = mapStatus(bjob.getStatus());
					
					if( ! "Completed".equals(status) ){
						uncompletedJobs++;
					}
					
					// ExecHost not recorded in LsfJob at present
					// String wn = bjob.getExecHost();
					// String workerNode = wn != null ? wn.split("/")[0] : "";
					
					LsfJob job = entityManager.find(LsfJob.class, id);
					if (job != null) {/* Log updates on portal jobs */
						if (!job.getStatus().equals(status) ) {
							logger.debug("Updating status of job '" + id + "' from '" + job.getStatus()
									+ "' to '" + status + "'");
							job.setStatus(status);
						}
					}
				}
				
				if( uncompletedJobs == 0 ){
					logger.debug("Userid " + poolUserId + " has no uncompleted jobs, so releasing it back to the pool");
					lsfUserPool.freeUser(poolUserId);
				}
			} // for activePoolUsers
		} catch (Exception e) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			e.printStackTrace(new PrintStream(baos));
			logger.error("Update of db jobs from bjobs failed (exception caught here). Class " + e.getClass() + " reports "
					+ e.getMessage() + baos.toString());
		}
	}

	private List<String> getActivePoolUsers() {
		List<String> poolUsers = new ArrayList<String>();
		for( List<String> members : families.values() ){
			poolUsers.addAll( members );
		}
		return poolUsers;
	}
	
	private void cleanUpJobs( String poolUserId ){
		for (LsfJob job : entityManager.createNamedQuery(LsfJob.FIND_BY_BATCHUSERNAME, LsfJob.class)
				.setParameter("batchusername", poolUserId).getResultList()) {
			if( ! "Completed".equals(job.getStatus()) ){
				logger.warn("Updating status of job '" + job.getId() + "' from '"
						+ job.getStatus() + "' to 'Completed' as not known to bjobs");
				job.setStatus("Completed");
			}
		}
		
		// And free up this userid back to the pool
	    // TODO What about any output from old jobs? Should it be (a) deleted or (b) moved to a safe place away from the poolUser?
		
		logger.debug("Userid " + poolUserId + " has no uncompleted jobs, so releasing it back to the pool (in cleanUpJobs)");
		lsfUserPool.freeUser(poolUserId);
	}

	public InputStream getJobOutput(String sessionId, String jobId, OutputType outputType)
			throws SessionException, ForbiddenException, InternalException, ParameterException {
		
		// In submitBatch, we specify the output files as %J.log and %J.err; %J will be replaced by the jobId.
		// So here we look for those files.  They may not exist until the job has completed.
		
		logger.info("getJobOutput called with sessionId:" + sessionId + " jobId:" + jobId
				+ " outputType:" + outputType);
		LsfJob job = getJob(sessionId, jobId);
		InputStream is;

		String jobFilename = job.getId() + "." + (outputType == OutputType.STANDARD_OUTPUT ? "log" : "err");
		
		// Path path = jobOutputDir.resolve(job.getDirectory()).resolve(jobFilename);

		Path outputParentPath = getUserJobsOutputPath( job.getBatchUsername() );
		Path path = outputParentPath.resolve(job.getDirectory()).resolve(jobFilename);

		if (!Files.exists(path)) {
			logger.debug("No " + outputType + " output (yet?) for job" + jobId);
			
			// In theory, we could use bpeek to get output of running job
			// However, bpeek output is not that useful (it concatenates stdout and stderr, albeit with markers).
			// Alternatively, bsub *appears* to use ~/.lsbatch/[0-9]*.<jobId>.{err,out,hostfile} to hold stdout/stderr during execution, and
			// these may be more useful to us.
			// But these files are removed when the job completes, so returning an InputStream on them may not be wise
			
			String userBase = Constants.SCARF_POOL_BASE;
			Path batchFolder = Paths.get(userBase).resolve(job.getBatchUsername()) .resolve(".lsbatch");
			final String outputFileEnding = jobId + "." + (outputType == OutputType.STANDARD_OUTPUT ? "out" : "err");
			File [] files = batchFolder.toFile().listFiles(new FilenameFilter() {
			    @Override
			    public boolean accept(File dir, String name) {
			        return name.endsWith(outputFileEnding);
			    }
			});
			
			if( files.length > 0 ){
				
				path = (files[0]).toPath();
				
			} else {
				
				throw new ParameterException("No output file of type " + outputType
						+ " available at the moment");
			}
		}
		if (Files.exists(path)) {
			logger.debug("Returning output for " + jobId);
			try {
				is = Files.newInputStream(path);
			} catch (IOException e) {
				throw new InternalException(e.getClass() + " reports " + e.getMessage());
			}
			return is;

		} else {
			throw new InternalException("No output file available at the moment");
		}
	}

	private Path getUserJobsOutputPath(String batchUsername) throws InternalException {
		String userBase = Constants.SCARF_POOL_BASE;
		
		Path outputParentPath = Paths.get(userBase).resolve(batchUsername).resolve(lsfUserOutputDir);
		if( !Files.exists(outputParentPath)){
			createUserJobsOutputDir( batchUsername );
			outputParentPath = Paths.get(userBase).resolve(batchUsername).resolve(lsfUserOutputDir);
		}
		return outputParentPath;
	}

	private void createUserJobsOutputDir(String batchUsername) throws InternalException {
		ShellCommand sc = new ShellCommand("ssh", "-i", getSshIdFileNameFor(batchUsername), batchUsername + "@localhost", "mkdir", lsfUserOutputDir);
		if (sc.isError()) {
			throw new InternalException("Unable to create jobs output folder for user " + batchUsername + ": " + sc.getStderr());
		}
		
	}

	public String submitBatch(String userName, String executable, List<String> parameters,
			String family) throws ParameterException, InternalException, SessionException {

		if (family == null) {
			family = defaultFamily;
		}
		List<String> members = families.get(family);
		if (members == null) {
			throw new ParameterException("Specified family " + family + " is not recognised");
		}
		
		String owner = assignLsfIdFrom( family );
		String idFileName = getSshIdFileNameFor( owner );
		
		String queueName = Constants.IJP_DEFAULT_QUEUE;
		
		// For now, use the executable as the job name.  May want to improve on this later.
		
		String jobName = executable;
		
		// Create a temporary directory for the (final) output files

		Path userJobsOutputPath = getUserJobsOutputPath(owner);

		String jobDirectoryName = createJobOutputDirectory(userJobsOutputPath, owner);
		
		/*
		 * The batch script is owned by glassfish, but should be readable by the LSF users.
		 */
		File batchScriptFile = null;
		do {
			char[] pw = new char[10];
			for (int i = 0; i < pw.length; i++) {
				pw[i] = chars.charAt(random.nextInt(chars.length()));
			}
			String batchScriptName = new String(pw) + ".sh";
			batchScriptFile = new File(batchFileDir.toString(), batchScriptName);
		} while (batchScriptFile.exists());

		createScript(batchScriptFile, jobName, executable, parameters);
		
		// Ask bsub to write log files to our temp directory; we need the absolute path
		
		String dirStr = userJobsOutputPath.toString() + File.separator + jobDirectoryName + File.separator;
		
		ShellCommand sc = new ShellCommand("ssh", "-i", idFileName, owner + "@localhost", "bsub", "-J", jobName, "-o", dirStr+"%J.log", "-e", dirStr+"%J.err", "-q", queueName,
				batchScriptFile.getAbsolutePath());
		if (sc.isError()) {
			throw new InternalException("Unable to submit job via bsub " + sc.getStderr());
		}
		String jobId = getJobId(sc.getStdout());
		
		if( jobId == null ){
			throw new InternalException("Unable to get JobId for submitted job" );
		}

		LsfJob job = new LsfJob();
		job.setId(jobId);
		job.setExecutable(executable);
		job.setBatchUsername(owner);
		job.setBatchfileName(batchScriptFile.getAbsolutePath());
		job.setDirectory(jobDirectoryName);
		job.setUsername(userName);
		job.setSubmitDate(new Date());
		
		// Get the initial status of the job from LSF
		job.setStatus( getStatus(job) );
		
		entityManager.persist(job);
		
		return jobId;

	}

	private String createJobOutputDirectory(Path userJobsOutputPath, String owner) throws InternalException {
		String jobDirectoryName;
		File jobDirectory = null;
		do {
			// Generate a random name, but redo if it does already exist
			char[] pw = new char[10];
			for (int i = 0; i < pw.length; i++) {
				pw[i] = chars.charAt(random.nextInt(chars.length()));
			}
			jobDirectoryName = new String(pw);
			jobDirectory = new File(userJobsOutputPath.toString(), jobDirectoryName);
		} while (jobDirectory.exists());
		
		// Now, we have to create the folder
		
		ShellCommand sc = new ShellCommand("ssh", "-i", getSshIdFileNameFor(owner), owner + "@localhost", "mkdir", jobDirectory.getAbsolutePath());
		if (sc.isError()) {
			throw new InternalException("Unable to create job output folder for user " + owner + ": " + sc.getStderr());
		}
		
		// The job directory name is all we need now.
		
		return jobDirectoryName;
	}

	private String getSshIdFileNameFor(String owner) {
		
		// Looks like this has to be an absolute path
		
		String userBase = Constants.SCARF_POOL_BASE;
		
		Path sshIdPath = Paths.get(userBase).resolve("glassfish").resolve(".ssh").resolve("id_rsa_"+owner);
		return sshIdPath.toString();
	}

	/**
	 * Assign an LSF user ID from the given family.
	 * Throws InternalException if there are no free user IDs available for that family.
	 * 
	 * @param username the ICAT user
	 * @param family
	 * @return
	 * @throws InternalException
	 */
	private String assignLsfIdFrom( String family ) throws InternalException{
		return lsfUserPool.getUserForFamily(family);
	}
	
	private String getJobId(String stdout) throws InternalException {
		/* 
		 * bsub output format is "LsfJob <jobId> is submitted to queue <queueName>.",
		 * (including the angle brackets).
		 */
		Pattern p = Pattern.compile("Job <(\\d+)> is submitted to queue <(.+)>\\.");
		Matcher m = p.matcher(stdout.trim());
		if( ! m.matches() ){
			logger.debug("Unable to get JobId for submitted job: bsub output was: " + stdout);
			throw new InternalException("Unable to get JobId for submitted job");
		}
		return m.group(1);
	}

	private void createScript(File batchScriptFile, String jobName, String executable, List<String> parameters) throws InternalException {

		List<String> finalParameters = new ArrayList<String>();
		finalParameters.addAll(parameters);

		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(batchScriptFile));
			writeln(bw, "#!/bin/sh");
			writeln(bw, "echo $(date) - " + jobName + " starting");
			String line = executable + " "
					+ JobManagementBean.escaped(finalParameters);
			logger.debug("Exec line for " + jobName + ": " + line);
			writeln(bw, line);
			writeln(bw, "rc=$?");
			writeln(bw, "echo $(date) - " + executable + " ending with code $rc");
			
			// TODO Add 'rm -rf *' to enforce cleanup?
			
		} catch (IOException e) {
			throw new InternalException("Exception creating batch script: " + e.getMessage());
		} finally {
			if (bw != null) {
				try {
					bw.close();
				} catch (IOException e) {
					// Ignore it
				}
			}
		}
		// File.setExecutable() only does it for the current user, we need it to be globally executable
		// batchScriptFile.setExecutable(true);
		setFileGloballyExecutable(batchScriptFile);
	}
	
	private void setFileGloballyExecutable(File file) throws InternalException{
		
		Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
        //add owners permission
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        //add group permissions
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
        //add others permissions
        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);
		try {
			Files.setPosixFilePermissions(file.toPath(), perms);
		} catch (IOException e) {
			throw new InternalException("Unable to set permissions on batch script: " + e.getMessage() );
		}
		
	}

	private void writeln(BufferedWriter bw, String string) throws IOException {
		bw.write(string);
		bw.newLine();
		logger.debug("Script line: " + string);
	}

	private static String sq = "\"'\"";

	static String escaped(List<String> parameters) {
		StringBuilder sb = new StringBuilder();
		for (String parameter : parameters) {
			if (sb.length() != 0) {
				sb.append(" ");
			}
			int offset = 0;
			while (true) {
				int quote = parameter.indexOf('\'', offset);
				if (quote == offset) {
					sb.append(sq);
				} else if (quote > offset) {
					sb.append("'" + parameter.substring(offset, quote) + "'" + sq);
				} else if (offset != parameter.length()) {
					sb.append("'" + parameter.substring(offset) + "'");
					break;
				} else {
					break;
				}
				offset = quote + 1;
			}
		}
		return sb.toString();
	}

	public String submitInteractive(String userName, String executable, List<String> parameters,
			String family) throws InternalException, ParameterException {
		throw new ParameterException("Interactive jobs are not currently supported by LsfBatch");
		// TODO must improve this ...
		// Path interactiveScriptFile = createScript(parameters, executable, null);
		// Account account = machineEJB.prepareMachine(userName, executable, parameters,
		// interactiveScriptFile);
		// return account.getUserName() + " " + account.getPassword() + " " + account.getHost();
	}

	private String getUserName(String sessionId) throws SessionException, ParameterException {
		try {
			checkCredentials(sessionId);
			return icat.getUserName(sessionId);
		} catch (IcatException_Exception e) {
			throw new SessionException("IcatException " + e.getFaultInfo().getType() + " "
					+ e.getMessage());
		}
	}

	public String listStatus(String sessionId) throws SessionException, ParameterException,
			InternalException {
		logger.info("listStatus called with sessionId:" + sessionId);

		String username = getUserName(sessionId);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator gen = Json.createGenerator(baos).writeStartArray();
		Map<String, Map<String, String>> jobs = new HashMap<>();
		for (LsfJob job : entityManager.createNamedQuery(LsfJob.FIND_BY_USERNAME, LsfJob.class)
				.setParameter("username", username).getResultList()) {

			String jobId = job.getId();
			String owner = job.getBatchUsername();
			Map<String, String> jobsByOwner = jobs.get(owner);
			if (jobsByOwner == null) {
				
				// First time we've needed jobs for this owner, so get them all and cache for future reference
				// So we only run bjobs for each LSF user once
				
				jobsByOwner = new HashMap<>();
				jobs.put(owner, jobsByOwner);
				String idFileName = getSshIdFileNameFor( owner );

				ShellCommand sc = new ShellCommand("ssh", "-i", idFileName, owner + "@localhost", "bjobs", "-aw");
				if (sc.isError()) {
					throw new InternalException("Unable to query jobs for userid " + owner
							+ ") via bjobs: " + sc.getStderr());
				}
				
				Bjobs bjobs = new Bjobs( sc.getStdout() );
				
				for( Bjobs.Job bjob : bjobs.getJobs() ){
					
					// Some of these jobs may not belong to username, but we only retrieve by username's jobs below
					jobsByOwner.put( bjob.getJobId(), mapStatus(bjob.getStatus() ));
				}
				
				logger.debug("Built list of jobs for " + owner + ": " + jobsByOwner);
			}
			String status = jobsByOwner.get(jobId);
			if (status == null) {
				status = "Completed";
			}
			gen.writeStartObject().write("Id", job.getId()).write("Status", status)
					.write("Executable", job.getExecutable())
					.write("Date of submission", job.getSubmitDate().toString()).writeEnd();
			
			// Update Job status too
			job.setStatus(status);
		}
		gen.writeEnd().close();
		return baos.toString();
	}

	public String getStatus(String jobId, String sessionId) throws SessionException,
			ForbiddenException, ParameterException, InternalException {
		logger.info("getStatus called with sessionId:" + sessionId + " jobId:" + jobId);
		LsfJob job = getJob(sessionId, jobId);
		
		String status = getStatus( job );
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator gen = Json.createGenerator(baos);
		gen.writeStartObject().write("Id", jobId).write("Status", status)
				.write("Executable", job.getExecutable())
				.write("Date of submission", job.getSubmitDate().toString());
		gen.writeEnd().close();
		return baos.toString();
	}
	
	private String getStatus( LsfJob job ) throws InternalException{
		
		String jobId = job.getId();
		String owner = job.getBatchUsername();
		logger.debug("job " + jobId + " is being run by " + owner);

		String idFileName = getSshIdFileNameFor( owner );

		ShellCommand sc = new ShellCommand("ssh", "-i", idFileName, owner + "@localhost", "bjobs", "-aw", jobId);
		if (sc.isError()) {
			// Astonishingly, "No job found" counts as an error!
			if( ! "No job found".equals(sc.getStderr())){
				throw new InternalException("Unable to query job (id " + jobId
						+ ") via bjobs: " + sc.getStderr());
			}
		}
		
		Bjobs bjobs = new Bjobs( sc.getStdout() );
		Bjobs.Job bjob = bjobs.getJob(jobId);
		if( bjob == null ){
			throw new InternalException("Unable to find job (id " + jobId + ") in bjobs" );
		}
		
		return mapStatus( bjob.getStatus() );
	}

	/**
	 * Convert the "low-level" status values returned by bjobs into broad status values that we report back
	 * 
	 * @param status the bjobs status value
	 * @return mapped status value - one of Completed, Executing, Queued, Held, Unknown
	 */
	private String mapStatus(String status) {

		String outStatus = "Unknown";
		
		if( "PEND".equals(status) || "WAIT".equals(status) ){
			outStatus = "Queued";
		} else if( "DONE".equals(status) || "EXIT".equals(status) ){
			outStatus = "Completed";
		} else if( "RUN".equals(status) ){
			outStatus = "Executing";
		} else if( "PSUSP".equals(status) || "USUSP".equals(status) || "SSUSP".equals(status) ){
			outStatus = "Held";
		} else if( "UNKWN".equals(status) || "ZOMBI".equals(status) ){
			// These are the "known unknowns" :-)
			outStatus = "Unknown";
		}
		return outStatus;
	}

	private LsfJob getJob(String sessionId, String jobId) throws SessionException, ForbiddenException,
			ParameterException {
		String username = getUserName(sessionId);
		if (jobId == null) {
			throw new ParameterException("No jobId was specified");
		}
		LsfJob job = entityManager.find(LsfJob.class, jobId);
		if (job == null || !job.getUsername().equals(username)) {
			throw new ForbiddenException("Job does not belong to you");
		}
		return job;
	}

	public void delete(String sessionId, String jobId) throws SessionException, ForbiddenException,
			InternalException, ParameterException {
		
		logger.info("delete called with sessionId:" + sessionId + " jobId:" + jobId);
		LsfJob job = getJob(sessionId, jobId);
		String owner = job.getBatchUsername();
		logger.debug("job " + jobId + " is being run by " + owner);
		
		// Get the status of this job.
		
		String idFileName = getSshIdFileNameFor( owner );

		ShellCommand sc = new ShellCommand("ssh", "-i", idFileName, owner + "@localhost", "bjobs", "-aw", jobId);
		if (sc.isError()) {
			throw new InternalException("Unable to query job (id " + jobId
					+ ") via bjobs: " + sc.getStderr());
		}
		
		Bjobs bjobs = new Bjobs( sc.getStdout() );
		Bjobs.Job bjob = bjobs.getJob(jobId);
		if( bjob == null ){
			throw new InternalException("Unable to find job (id " + jobId + ") in bjobs" );
		}
		
		String status = mapStatus( bjob.getStatus() );
		logger.debug("Status is " + status);
		if (!status.equals("Completed")) {
			// TODO what about Unknown jobs?
			throw new ParameterException("LsfJob " + jobId + " is " + status);
		}

		entityManager.remove(job);
		
		// NOTE: we assume each owner has at most one current job
		// owner may already be free, if we spot when the job completes
		
		lsfUserPool.freeUser(owner);

		try {
			Path dir = getUserJobsOutputPath(owner).resolve(job.getDirectory());
			
			// And get the owner to remove everything under there.
			
			sc = new ShellCommand("ssh", "-i", getSshIdFileNameFor(owner), owner + "@localhost", "rm", "-rf", dir.toString());
			if (sc.isError()) {
				throw new InternalException("Unable to delete job output folder for user " + owner + ": " + sc.getStderr());
			}
						
			File[] files = dir.toFile().listFiles();
			if (files != null) {
				for (File f : dir.toFile().listFiles()) {
					Files.delete(f.toPath());
				}
				Files.delete(dir);
				logger.debug("Directory " + dir + " has been deleted");
			}
		} catch (IOException e) {
			throw new InternalException("Unable to delete jobOutputDirectory "
					+ job.getDirectory());
		}

	}

	public void cancel(String sessionId, String jobId) throws SessionException, ForbiddenException,
			InternalException, ParameterException {
		
		logger.info("cancel called with sessionId:" + sessionId + " jobId:" + jobId);
		LsfJob job = getJob(sessionId, jobId);
		String owner = job.getBatchUsername();
		logger.debug("job " + jobId + " is being run by " + owner);
		String idFileName = getSshIdFileNameFor( owner );
		ShellCommand sc = new ShellCommand("ssh", "-i", idFileName, owner + "@localhost", "bkill", job.getId());
		if (sc.isError() && !sc.getStderr().startsWith("Warning")) {
			throw new ParameterException("Unable to cancel job " + job.getId() + ": " + sc.getStderr());
		}
	}

	private void checkCredentials(String sessionId) throws ParameterException {
		if (sessionId == null) {
			throw new ParameterException("No sessionId was specified");
		}
	}

	public String submit(String sessionId, String executable, List<String> parameters,
			String family, boolean interactive) throws InternalException, SessionException,
			ParameterException {
		logger.info("submit called with sessionId:" + sessionId + " executable:" + executable
				+ " parameters:" + parameters + " family:" + family + " :" + " interactive:"
				+ interactive);
		String userName = getUserName(sessionId);
		String jobId;
		if (interactive) {
			jobId = submitInteractive(userName, executable, parameters, family);
		} else {
			jobId = submitBatch(userName, executable, parameters, family);
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator gen = Json.createGenerator(baos);
		gen.writeStartObject().write("jobId", jobId).writeEnd().close();
		return baos.toString();
	}

	public String estimate(String sessionId, String executable, List<String> parameters,
			String family, boolean interactive) throws SessionException, ParameterException {
		String userName = getUserName(sessionId);

		int time;
		if (interactive) {
			time = estimateInteractive(userName, executable, parameters, family);
		} else {
			time = estimateBatch(userName, executable, parameters, family);
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator gen = Json.createGenerator(baos);
		gen.writeStartObject().write("time", time).writeEnd().close();
		return baos.toString();
	}

	private int estimateBatch(String userName, String executable, List<String> parameters,
			String family) {
		return 0;
	}

	private int estimateInteractive(String userName, String executable, List<String> parameters,
			String family) throws SessionException, ParameterException {
		throw new ParameterException("Interactive jobs are not currently supported by lsfbatch");
	}

}

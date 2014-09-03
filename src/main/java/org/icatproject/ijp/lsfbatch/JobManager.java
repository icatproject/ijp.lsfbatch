package org.icatproject.ijp.lsfbatch;

import java.io.InputStream;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.icatproject.ijp.lsfbatch.JobManagementBean.OutputType;
import org.icatproject.ijp.lsfbatch.exceptions.ForbiddenException;
import org.icatproject.ijp.lsfbatch.exceptions.InternalException;
import org.icatproject.ijp.lsfbatch.exceptions.ParameterException;
import org.icatproject.ijp.lsfbatch.exceptions.SessionException;

@Stateless
@Path("")
public class JobManager {

	@EJB
	private JobManagementBean jobManagementBean;

	@POST
	@Path("cancel/{jobId}")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	/**
	 * Cancel the specified job if permitted to do so
	 *  
	 * @param jobId as returned by the call to submit
	 * @param sessionId the icatSession id of the submitter
	 * 
	 * @throws SessionException
	 * @throws ForbiddenException
	 * @throws InternalException
	 */
	public void cancel(@PathParam("jobId") String jobId, @FormParam("sessionId") String sessionId)
			throws SessionException, ForbiddenException, InternalException, ParameterException {
		jobManagementBean.cancel(sessionId, jobId);
	}

	@DELETE
	@Path("delete/{jobId}")
	/**
	 * Delete all information on the specified job if permitted to do so and if it has completed
	 * 
	 * @param jobId as returned by the call to submit
	 * @param sessionId the icatSession id of the submitter
	 * 
	 * @throws SessionException
	 * @throws ForbiddenException
	 * @throws InternalException
	 * @throws ParameterException
	 */
	public void delete(@PathParam("jobId") String jobId, @QueryParam("sessionId") String sessionId)
			throws SessionException, ForbiddenException, InternalException, ParameterException {
		jobManagementBean.delete(sessionId, jobId);
	}

	@GET
	@Path("estimate")
	/**
	 * Return an estimate of the time to complete a batch job or to start an interactive one. 
	 * The parameters are indentical to those of submit. 
	 * 
	 * @param sessionId the icatSession id of the submitter
	 * @param executable the executable name
	 * @param parameters the executables parameters
	 * @param interactive true if interactive else false
	 * @param family the name of the family. A family identifies a group of user 
	 *        accounts. If omitted the default family can be used.
	 *  
	 * @return The time estimate in minutes relative to the current time. As an integer this can 
	 *         accommodate a delay of around sixty years.
	 * 
	 * @throws InternalException
	 * @throws SessionException
	 * @throws ParameterException
	 */
	public String estimate(@QueryParam("sessionId") String sessionId,
			@QueryParam("executable") String executable,
			@QueryParam("parameter") List<String> parameters,
			@QueryParam("interactive") Boolean interactive, @QueryParam("family") String family)
			throws InternalException, SessionException, ParameterException {
		return jobManagementBean.estimate(sessionId, executable, parameters, family,
				interactive != null && interactive);
	}

	@GET
	@Path("error/{jobId}")
	/**
	 * Stream the contents of the jobs standard standard error. If the job has not  
	 * finished running the output will be incomplete.
	 * 
	 * @param jobId as returned by the call to submit
	 * @param sessionId the icatSession id of the submitter
	 * 
	 * @return stream
	 * 
	 * @throws SessionException
	 * @throws ForbiddenException
	 * @throws InternalException
	 */
	public InputStream getError(@PathParam("jobId") String jobId,
			@QueryParam("sessionId") String sessionId) throws SessionException, ForbiddenException,
			InternalException, ParameterException {
		return jobManagementBean.getJobOutput(sessionId, jobId, OutputType.ERROR_OUTPUT);
	}

	@GET
	@Path("output/{jobId}")
	/**
	 * Stream the contents of the jobs standard standard output. If the job has not  
	 * finished running the output will be incomplete.
	 * 
	 * @param jobId as returned by the call to submit
	 * @param sessionId the icatSession id of the submitter
	 * 
	 * @return stream
	 * 
	 * @throws SessionException
	 * @throws ForbiddenException
	 * @throws InternalException
	 * @throws ParameterException
	 */
	public InputStream getOutput(@PathParam("jobId") String jobId,
			@QueryParam("sessionId") String sessionId) throws SessionException, ForbiddenException,
			InternalException, ParameterException {
		return jobManagementBean.getJobOutput(sessionId, jobId, OutputType.STANDARD_OUTPUT);

	}

	@GET
	@Path("status")
	/**
	 * Get the list of statuses of known jobs that may be queried by the user identified by the sessionId
	 * 
	 * @param sessionId
	 * 
	 * @return list of statuses
	 * 
	 * @throws SessionException
	 * @throws ParameterException
	 * @throws InternalException
	 */
	public String getStatus(@QueryParam("sessionId") String sessionId) throws SessionException,
			ParameterException, InternalException {
		return jobManagementBean.listStatus(sessionId);
	}

	@GET
	@Path("status/{jobId}")
	@Produces(MediaType.APPLICATION_JSON)
	/**
	 * Get the status of a specific job
	 *  
	 * @param jobId as returned by the call to submit
	 * @param sessionId the icatSession id of the submitter
	 * 
	 * @return the status as a json string
	 * 
	 * @throws SessionException
	 * @throws ForbiddenException
	 * @throws ParameterException
	 * @throws InternalException
	 */
	public String getStatus(@PathParam("jobId") String jobId,
			@QueryParam("sessionId") String sessionId) throws SessionException, ForbiddenException,
			ParameterException, InternalException {
		return jobManagementBean.getStatus(jobId, sessionId);
	}

	@POST
	@Path("submit")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	/**
	 * Submit a job
	 * 
	 * @param sessionId the icatSession id of the submitter
	 * @param executable the executable name
	 * @param parameters the executables parameters
	 * @param interactive true if interactive else false
	 * @param family the name of the family. A family identifies a group of user accounts. If omitted the default family can be used.
	 *  
	 * @return The job id - this could be the id assigned by the underlying batch system.
	 * 
	 * @throws InternalException
	 * @throws SessionException
	 * @throws ParameterException
	 */
	public String submit(@FormParam("sessionId") String sessionId,
			@FormParam("executable") String executable,
			@FormParam("parameter") List<String> parameters,
			@FormParam("interactive") Boolean interactive, @FormParam("family") String family)
			throws InternalException, SessionException, ParameterException {
		return jobManagementBean.submit(sessionId, executable, parameters, family,
				interactive != null && interactive);
	}

}
package org.icatproject.ijp.lsfbatch;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@SuppressWarnings("serial")
@Entity
@NamedQueries({
	@NamedQuery(name = "LsfJob.FIND_BY_USERNAME", query = "SELECT j FROM LsfJob j WHERE j.username = :username ORDER BY j.submitDate DESC"),
	@NamedQuery(name = "LsfJob.FIND_BY_BATCHUSERNAME", query = "SELECT j FROM LsfJob j WHERE j.batchUsername = :batchusername ORDER BY j.submitDate DESC") })
public class LsfJob implements Serializable {

	public final static String FIND_BY_USERNAME = "LsfJob.FIND_BY_USERNAME";
	public final static String FIND_BY_BATCHUSERNAME = "LsfJob.FIND_BY_BATCHUSERNAME";

	private String batchUsername;
	private String batchfileName;

	private String directory;
	private String executable;

	@Id
	private String id;

	@Temporal(TemporalType.TIMESTAMP)
	private Date submitDate;

	private String username;
	
	private String status;

	public LsfJob() {
	}

	public String getBatchUsername() {
		return batchUsername;
	}
	
	public String getBatchfileName(){
		return batchfileName;
	}

	public String getDirectory() {
		return directory;
	}

	public String getExecutable() {
		return executable;
	}

	public String getId() {
		return id;
	}

	public Date getSubmitDate() {
		return submitDate;
	}

	public String getUsername() {
		return username;
	}
	
	public String getStatus(){
		return status;
	}

	public void setBatchUsername(String batchUsername) {
		this.batchUsername = batchUsername;
	}
	
	public void setBatchfileName(String batchfileName){
		this.batchfileName = batchfileName;
	}

	public void setDirectory(String directory) {
		this.directory = directory;
	}

	public void setExecutable(String executable) {
		this.executable = executable;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setSubmitDate(Date submitDate) {
		this.submitDate = submitDate;
	}

	public void setUsername(String username) {
		this.username = username;
	}
	
	public void setStatus(String status){
		this.status = status;
	}
}

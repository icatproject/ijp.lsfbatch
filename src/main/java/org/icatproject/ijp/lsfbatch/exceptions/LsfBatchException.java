package org.icatproject.ijp.lsfbatch.exceptions;

@SuppressWarnings("serial")
public class LsfBatchException extends Exception {

	private int httpStatusCode;

	public LsfBatchException(int httpStatusCode, String message) {
		super(message);
		this.httpStatusCode = httpStatusCode;
	}

	public int getHttpStatusCode() {
		return httpStatusCode;
	}

}

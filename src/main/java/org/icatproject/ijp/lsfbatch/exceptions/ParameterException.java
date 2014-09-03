package org.icatproject.ijp.lsfbatch.exceptions;

import java.net.HttpURLConnection;

@SuppressWarnings("serial")
public class ParameterException extends LsfBatchException {

	public ParameterException(String message) {
		super(HttpURLConnection.HTTP_BAD_REQUEST, message);
	}

}

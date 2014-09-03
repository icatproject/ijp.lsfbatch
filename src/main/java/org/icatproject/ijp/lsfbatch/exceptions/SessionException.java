package org.icatproject.ijp.lsfbatch.exceptions;

import java.net.HttpURLConnection;

@SuppressWarnings("serial")
public class SessionException extends LsfBatchException {

	public SessionException(String message) {
		super(HttpURLConnection.HTTP_FORBIDDEN, message);
	}

}

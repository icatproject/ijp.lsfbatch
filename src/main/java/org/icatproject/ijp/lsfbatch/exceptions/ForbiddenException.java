package org.icatproject.ijp.lsfbatch.exceptions;

import java.net.HttpURLConnection;

@SuppressWarnings("serial")
public class ForbiddenException extends LsfBatchException {

	public ForbiddenException(String message) {
		super(HttpURLConnection.HTTP_FORBIDDEN, message);
	}

}

package org.icatproject.ijp.lsfbatch.exceptions;

import java.net.HttpURLConnection;

@SuppressWarnings("serial")
public class InternalException extends LsfBatchException {

	public InternalException(String message) {
		super(HttpURLConnection.HTTP_INTERNAL_ERROR, message);
	}

}

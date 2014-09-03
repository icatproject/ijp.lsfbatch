package org.icatproject.ijp.lsfbatch.exceptions;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.apache.log4j.Logger;

@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

	private static Logger logger = Logger.getLogger(WebApplicationExceptionMapper.class);

	@Override
	public Response toResponse(WebApplicationException e) {
		logger.info("Processing: " + e.getClass() + " " + e.getMessage());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator gen = Json.createGenerator(baos);
		gen.writeStartObject().write("code", "InternalException")
				.write("message", e.getClass() + " " + e.getMessage()).writeEnd().close();
		return Response.status(HttpURLConnection.HTTP_INTERNAL_ERROR).entity(baos.toString())
				.build();
	}
}
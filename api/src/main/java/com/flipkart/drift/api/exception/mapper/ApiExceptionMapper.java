package com.flipkart.drift.api.exception.mapper;

import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.sdk.model.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Override
    public Response toResponse(ApiException exception) {
        ErrorResponse errorResponse = new ErrorResponse(MDC.get("id"), exception.getClass(), exception.getMessage(),
                exception.getDescription(), exception.getStatus().getStatusCode());
        log.error("Caught Exception, response {}", errorResponse, exception);
        return Response.status(exception.getStatus())
                .entity(errorResponse).type(MediaType.APPLICATION_JSON_TYPE).build();
    }
}





package com.flipkart.drift.api.exception;

import com.flipkart.drift.commons.model.client.response.ErrorResponse;
import io.dropwizard.jersey.validation.ConstraintMessage;
import io.dropwizard.jersey.validation.JerseyViolationException;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.model.Invocable;
import org.slf4j.MDC;

import javax.validation.ConstraintViolation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class JerseyViolationInformativeExceptionMapper implements ExceptionMapper<JerseyViolationException> {

    @Override
    public Response toResponse(JerseyViolationException exception) {
        Set<ConstraintViolation<?>> violations = exception.getConstraintViolations();
        Invocable invocable = exception.getInvocable();
        List<String> errors = exception.getConstraintViolations().stream().map(violation -> ConstraintMessage.getMessage(violation, invocable)).toList();
        int status = ConstraintMessage.determineStatus(violations, invocable);
        ErrorResponse errorResponse = new ErrorResponse(MDC.get("id"), exception.getClass(), errors.toString(),
                violations.toString(), status);
        return Response.status(Response.Status.PRECONDITION_FAILED).type(MediaType.APPLICATION_JSON_TYPE).entity(errorResponse).build();

    }
}





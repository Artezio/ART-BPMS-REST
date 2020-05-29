package com.artezio.ws.rs;

import com.artezio.bpm.rest.exception.RestException;
import com.artezio.bpm.services.exceptions.NotAuthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.camunda.bpm.engine.exception.DeploymentResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;
import java.util.HashMap;
import java.util.Map;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

@Provider
public class ExceptionMapper implements javax.ws.rs.ext.ExceptionMapper<Throwable> {

    private final static String JSON_ATTRIBUTE_ERROR_MESSAGE = "errorMessage";
    private final static String JSON_ATTRIBUTE_CAUSE = "cause";

    @Autowired
    private HttpServletResponse response;

    @SuppressWarnings("serial")
    private final static Map<Class<?>, Status> EXCEPTION_HTTP_STATUS = new HashMap<>() {
        {
            put(AccessDeniedException.class, Status.FORBIDDEN);
            put(NotAuthorizedException.class, Status.FORBIDDEN);
            put(DeploymentResourceNotFoundException.class, Status.NOT_FOUND);
        }
    };

    @Override
    public Response toResponse(Throwable exception) {
        Status status = findResponseStatus(exception);
        response.setContentType(MediaType.APPLICATION_JSON);
        response.setHeader("Content-Disposition", "");
        return Response
                .status(status)
                .entity(getErrorMessage(exception))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    protected static Status findResponseStatus(Throwable exception) {
        return EXCEPTION_HTTP_STATUS.entrySet().stream()
                .filter(e -> e.getKey().isInstance(exception))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> exception.getClass().isInstance(RestException.class)
                        ? ((RestException) exception).getStatus()
                        : INTERNAL_SERVER_ERROR);
    }

    private JsonNode getErrorMessage(Throwable exception) {
        ObjectNode currentJsonError = JsonNodeFactory.instance.objectNode();
        ObjectNode resultJsonError = currentJsonError;
        while (exception != null) {
            currentJsonError.put(JSON_ATTRIBUTE_ERROR_MESSAGE, exception.getMessage());
            currentJsonError.set(JSON_ATTRIBUTE_CAUSE, JsonNodeFactory.instance.objectNode());
            exception = exception.getCause();
            currentJsonError = (ObjectNode) currentJsonError.findValue(JSON_ATTRIBUTE_CAUSE);
        }
        return resultJsonError;
    }

}

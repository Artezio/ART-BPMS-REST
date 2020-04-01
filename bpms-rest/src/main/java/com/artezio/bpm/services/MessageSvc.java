package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.VariableValueRepresentation;
import com.artezio.bpm.rest.dto.message.CorrelationMessageRepresentation;
import com.artezio.bpm.rest.dto.message.MessageCorrelationResultRepresentation;
import com.artezio.bpm.rest.exception.InvalidRequestException;
import com.artezio.bpm.rest.exception.RestException;
import com.artezio.logging.Log;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.camunda.bpm.engine.MismatchingMessageCorrelationException;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.MessageCorrelationBuilder;
import org.camunda.bpm.engine.runtime.MessageCorrelationResult;

import javax.annotation.security.PermitAll;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.artezio.logging.Log.Level.CONFIG;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Stateless
@Path("/message")
public class MessageSvc {

    private static final String PROCESS_ENGINE_NAME = "default";

    @Inject
    private RuntimeService runtimeService;

    @POST
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            description = "A signal is an event of global scope (broadcast semantics) and is delivered to all active handlers.",
            externalDocs = @ExternalDocumentation(url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/message-service-api-docs.md"),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Request successful. The property resultEnabled in the request body was true.",
                            content = @Content(mediaType = APPLICATION_JSON)
                    ),
                    @ApiResponse(
                            responseCode = "204",
                            description = "Request successful. The property resultEnabled in the request body was false (Default)"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "If no messageName was supplied. If both tenantId and withoutTenantId are supplied. If the message has not been correlated to exactly one entity (execution or process definition), or the variable value or type is invalid, for example if the value could not be parsed to an Integer value or the passed variable type is not supported.",
                            content = @Content(mediaType = APPLICATION_JSON)
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Sending a message", afterExecuteMessage = "Message is sent")
    public Response deliverMessage(CorrelationMessageRepresentation correlationMessageRepresentation) {
        if (correlationMessageRepresentation.getMessageName() == null) {
            throw new InvalidRequestException(BAD_REQUEST, "No message name supplied");
        }
        if (correlationMessageRepresentation.getTenantId() != null && correlationMessageRepresentation.isWithoutTenantId()) {
            throw new InvalidRequestException(BAD_REQUEST, "Parameter 'tenantId' cannot be used together with parameter 'withoutTenantId'");
        }

        List<MessageCorrelationResultRepresentation> resultRepresentations = new ArrayList<>();
        try {
            MessageCorrelationBuilder correlation = createMessageCorrelationBuilder(correlationMessageRepresentation);
            if (!correlationMessageRepresentation.isAll()) {
                MessageCorrelationResult result = correlation.correlateWithResult();
                resultRepresentations.add(MessageCorrelationResultRepresentation.fromMessageCorrelationResult(result));
            } else {
                correlation.correlateAllWithResult().forEach(messageCorrelationResult ->
                        resultRepresentations.add(MessageCorrelationResultRepresentation.fromMessageCorrelationResult(messageCorrelationResult)));
            }
        } catch (RestException e) {
            String errorMessage = String.format("Cannot deliver message: %s", e.getMessage());
            throw new InvalidRequestException(e.getStatus(), e, errorMessage);

        } catch (MismatchingMessageCorrelationException e) {
            throw new RestException(BAD_REQUEST, e);
        }
        return createResponse(resultRepresentations, correlationMessageRepresentation);
    }


    protected Response createResponse(List<MessageCorrelationResultRepresentation> resultRepresentations, CorrelationMessageRepresentation messageRepresentation) {
        Response.ResponseBuilder response = Response.noContent();
        if (messageRepresentation.isResultEnabled()) {
            response = Response.ok(resultRepresentations, MediaType.APPLICATION_JSON);
        }
        return response.build();
    }

    protected MessageCorrelationBuilder createMessageCorrelationBuilder(CorrelationMessageRepresentation messageRepresentation) {
        ProcessEngine processEngine = getProcessEngine();
        Map<String, Object> correlationKeys = VariableValueRepresentation.toMap(messageRepresentation.getCorrelationKeys(), processEngine);
        Map<String, Object> localCorrelationKeys = VariableValueRepresentation.toMap(messageRepresentation.getLocalCorrelationKeys(), processEngine);
        Map<String, Object> processVariables = VariableValueRepresentation.toMap(messageRepresentation.getProcessVariables(), processEngine);
        Map<String, Object> processVariablesLocal = VariableValueRepresentation.toMap(messageRepresentation.getProcessVariablesLocal(), processEngine);

        MessageCorrelationBuilder builder = runtimeService
                .createMessageCorrelation(messageRepresentation.getMessageName());

        if (processVariables != null) {
            builder.setVariables(processVariables);
        }
        if (processVariablesLocal != null) {
            builder.setVariablesLocal(processVariablesLocal);
        }
        if (messageRepresentation.getBusinessKey() != null) {
            builder.processInstanceBusinessKey(messageRepresentation.getBusinessKey());
        }
        if (correlationKeys != null && !correlationKeys.isEmpty()) {
            correlationKeys.forEach(builder::processInstanceVariableEquals);
        }
        if (localCorrelationKeys != null && !localCorrelationKeys.isEmpty()) {
            localCorrelationKeys.forEach(builder::localVariableEquals);
        }
        if (messageRepresentation.getTenantId() != null) {
            builder.tenantId(messageRepresentation.getTenantId());
        } else if (messageRepresentation.isWithoutTenantId()) {
            builder.withoutTenantId();
        }

        String processInstanceId = messageRepresentation.getProcessInstanceId();
        if (processInstanceId != null) {
            builder.processInstanceId(processInstanceId);
        }

        return builder;
    }

    private ProcessEngine getProcessEngine() {
        return ProcessEngines.getProcessEngine(PROCESS_ENGINE_NAME);
    }

}

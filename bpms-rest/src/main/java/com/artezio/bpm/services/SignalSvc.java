package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.SignalRepresentation;
import com.artezio.bpm.rest.dto.VariableValueRepresentation;
import com.artezio.bpm.rest.exception.InvalidRequestException;
import com.artezio.logging.Log;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.RolesAllowed;
import java.util.Map;

import static com.artezio.logging.Log.Level.CONFIG;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/signal")
@Transactional
public class SignalSvc {

    private static final String PROCESS_ENGINE_NAME = "default";

    private final RuntimeService runtimeService;

    @Autowired
    public SignalSvc(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @RolesAllowed("NotificationsReceiver")
    @Operation(
            description = "A signal is an event of global scope (broadcast semantics) and is delivered to all active handlers.",
            externalDocs = @ExternalDocumentation(url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/signal-service-api-docs.md"),
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "Request successful."
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "* If no name was given\n" +
                                          "* If the variable value or type is invalid, for example if the value could not be parsed to an integer value or the passed variable type is not supported\n" +
                                          "* If a tenant id and an execution id is specified",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE)
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "If the user is not allowed to throw a signal event",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE)
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "If a single execution is specified and no such execution exists or has not subscribed to the signal",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE)
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Sending a signal", afterExecuteMessage = "Signal is sent")
    public void throwSignal(SignalRepresentation signalRepresentation) {
        String name = signalRepresentation.getName();
        if (name == null) {
            throw new InvalidRequestException(BAD_REQUEST, "No signal name given");
        }

        SignalEventReceivedBuilder signalEvent = createSignalEventReceivedBuilder(signalRepresentation);
        signalEvent.send();
    }

    protected SignalEventReceivedBuilder createSignalEventReceivedBuilder(SignalRepresentation signalRepresentation) {
        String name = signalRepresentation.getName();
        SignalEventReceivedBuilder signalEvent = runtimeService.createSignalEvent(name);

        String executionId = signalRepresentation.getExecutionId();
        if (executionId != null) {
            signalEvent.executionId(executionId);
        }

        Map<String, VariableValueRepresentation> variablesDto = signalRepresentation.getVariables();
        if (variablesDto != null) {
            Map<String, Object> variables = VariableValueRepresentation.toMap(variablesDto, getProcessEngine());
            signalEvent.setVariables(variables);
        }

        String tenantId = signalRepresentation.getTenantId();
        if (tenantId != null) {
            signalEvent.tenantId(tenantId);
        }

        boolean isWithoutTenantId = signalRepresentation.isWithoutTenantId();
        if (isWithoutTenantId) {
            signalEvent.withoutTenantId();
        }

        return signalEvent;
    }

    private ProcessEngine getProcessEngine() {
        return ProcessEngines.getProcessEngine(PROCESS_ENGINE_NAME);
    }

}

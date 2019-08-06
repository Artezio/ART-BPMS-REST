package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.SignalRepresentation;
import com.artezio.bpm.rest.dto.VariableValueRepresentation;
import com.artezio.bpm.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Stateless
@Path("/signal")
@Produces(MediaType.APPLICATION_JSON)
public class SignalSvc {

    private static final String PROCESS_ENGINE_NAME = "default";

    @Inject
    private RuntimeService runtimeService;

    @POST
    @RolesAllowed("BPMSAdmin")
    @Consumes(MediaType.APPLICATION_JSON)
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

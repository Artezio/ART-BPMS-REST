package com.artezio.bpm.rest.dto.runtime;

import org.camunda.bpm.engine.runtime.Execution;

public class ExecutionRepresentation {

    private String id;
    private String processInstanceId;
    private boolean ended;
    private String tenantId;

    public static ExecutionRepresentation fromExecution(Execution execution) {
        ExecutionRepresentation representation = new ExecutionRepresentation();
        representation.id = execution.getId();
        representation.processInstanceId = execution.getProcessInstanceId();
        representation.ended = execution.isEnded();
        representation.tenantId = execution.getTenantId();

        return representation;
    }

    public String getId() {
        return id;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public boolean isEnded() {
        return ended;
    }

    public String getTenantId() {
        return tenantId;
    }

}
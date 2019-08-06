package com.artezio.bpm.rest.dto;

import java.util.Map;

public class SignalRepresentation {

    private String name;
    private String executionId;
    private Map<String, VariableValueRepresentation> variables;
    private String tenantId;
    private boolean withoutTenantId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Map<String, VariableValueRepresentation> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, VariableValueRepresentation> variables) {
        this.variables = variables;
    }

    public boolean isWithoutTenantId() {
        return withoutTenantId;
    }

    public void setWithoutTenantId(boolean withoutTenantId) {
        this.withoutTenantId = withoutTenantId;
    }

}
package com.artezio.bpm.rest.dto.message;

import com.artezio.bpm.rest.dto.VariableValueRepresentation;

import java.util.Map;

public class CorrelationMessageRepresentation {

    private String messageName;
    private String businessKey;
    private Map<String, VariableValueRepresentation> correlationKeys;
    private Map<String, VariableValueRepresentation> localCorrelationKeys;
    private Map<String, VariableValueRepresentation> processVariables;
    private Map<String, VariableValueRepresentation> processVariablesLocal;
    private String tenantId;
    private boolean withoutTenantId;
    private String processInstanceId;

    private boolean all = false;
    private boolean resultEnabled = false;

    public String getMessageName() {
        return messageName;
    }

    public void setMessageName(String messageName) {
        this.messageName = messageName;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public Map<String, VariableValueRepresentation> getCorrelationKeys() {
        return correlationKeys;
    }

    public void setCorrelationKeys(Map<String, VariableValueRepresentation> correlationKeys) {
        this.correlationKeys = correlationKeys;
    }

    public Map<String, VariableValueRepresentation> getLocalCorrelationKeys() {
        return localCorrelationKeys;
    }

    public void setLocalCorrelationKeys(Map<String, VariableValueRepresentation> localCorrelationKeys) {
        this.localCorrelationKeys = localCorrelationKeys;
    }

    public Map<String, VariableValueRepresentation> getProcessVariables() {
        return processVariables;
    }

    public void setProcessVariables(Map<String, VariableValueRepresentation> processVariables) {
        this.processVariables = processVariables;
    }

    public Map<String, VariableValueRepresentation> getProcessVariablesLocal() {
        return processVariablesLocal;
    }

    public void setProcessVariablesLocal(Map<String, VariableValueRepresentation> processVariablesLocal) {
        this.processVariablesLocal = processVariablesLocal;
    }

    public boolean isAll() {
        return all;
    }

    public void setAll(boolean all) {
        this.all = all;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isWithoutTenantId() {
        return withoutTenantId;
    }

    public void setWithoutTenantId(boolean withoutTenantId) {
        this.withoutTenantId = withoutTenantId;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public boolean isResultEnabled() {
        return resultEnabled;
    }

    public void setResultEnabled(boolean resultEnabled) {
        this.resultEnabled = resultEnabled;
    }
}

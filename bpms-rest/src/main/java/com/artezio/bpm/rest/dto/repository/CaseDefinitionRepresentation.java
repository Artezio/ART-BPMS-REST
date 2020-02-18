package com.artezio.bpm.rest.dto.repository;

import org.camunda.bpm.engine.repository.CaseDefinition;

public class CaseDefinitionRepresentation {
    protected String key;
    protected int version;
    protected String deploymentId;
    protected String resourceName;
    protected String diagramResourceName;
    protected String tenantId;

    public CaseDefinitionRepresentation() {}

    public CaseDefinitionRepresentation(CaseDefinition caseDefinition) {
        setKey(caseDefinition.getKey());
        setDeploymentId(caseDefinition.getDeploymentId());
        setDiagramResourceName(caseDefinition.getDiagramResourceName());
        setResourceName(caseDefinition.getResourceName());
        setTenantId(caseDefinition.getTenantId());
    }

    public static CaseDefinitionRepresentation fromCaseDefinition(CaseDefinition caseDefinition) {
        return new CaseDefinitionRepresentation(caseDefinition);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getDiagramResourceName() {
        return diagramResourceName;
    }

    public void setDiagramResourceName(String diagramResourceName) {
        this.diagramResourceName = diagramResourceName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}

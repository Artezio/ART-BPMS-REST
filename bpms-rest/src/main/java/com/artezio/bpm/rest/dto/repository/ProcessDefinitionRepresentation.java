package com.artezio.bpm.rest.dto.repository;

import org.camunda.bpm.engine.repository.ProcessDefinition;

public class ProcessDefinitionRepresentation {

    protected String id;
    protected String key;
    protected String category;
    protected String description;
    protected String name;
    protected int version;
    protected String resource;
    protected String deploymentId;
    protected String diagram;
    protected boolean suspended;
    protected String tenantId;
    protected String versionTag;
    protected Integer historyTimeToLive;
    protected boolean isStartableInTasklist;
    protected boolean hasStartFormKey;

    public String getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    public String getResource() {
        return resource;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public String getDiagram() {
        return diagram;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public Integer getHistoryTimeToLive() {
        return historyTimeToLive;
    }

    public boolean isStartableInTasklist() {
        return isStartableInTasklist;
    }

    public boolean isHasStartFormKey() {
        return hasStartFormKey;
    }

    public static ProcessDefinitionRepresentation fromProcessDefinition(ProcessDefinition definition) {
        ProcessDefinitionRepresentation dto = new ProcessDefinitionRepresentation();
        dto.id = definition.getId();
        dto.key = definition.getKey();
        dto.category = definition.getCategory();
        dto.description = definition.getDescription();
        dto.name = definition.getName();
        dto.version = definition.getVersion();
        dto.resource = definition.getResourceName();
        dto.deploymentId = definition.getDeploymentId();
        dto.diagram = definition.getDiagramResourceName();
        dto.suspended = definition.isSuspended();
        dto.tenantId = definition.getTenantId();
        dto.versionTag = definition.getVersionTag();
        dto.historyTimeToLive = definition.getHistoryTimeToLive();
        dto.isStartableInTasklist = definition.isStartableInTasklist();
        dto.hasStartFormKey = definition.hasStartFormKey();
        return dto;
    }

}

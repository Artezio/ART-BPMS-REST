package com.artezio.bpm.rest.dto.repository;

import java.util.*;

import org.camunda.bpm.engine.repository.*;

public class DeploymentRepresentation {

    protected String id;
    protected String name;
    protected String source;
    protected Date deploymentTime;
    protected String tenantId;

    public DeploymentRepresentation() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSource() {
        return source;
    }

    public Date getDeploymentTime() {
        return deploymentTime;
    }

    public String getTenantId() {
        return tenantId;
    }

    public static DeploymentRepresentation fromDeployment(Deployment deployment) {
        DeploymentRepresentation dto = new DeploymentRepresentation();
        dto.id = deployment.getId();
        dto.name = deployment.getName();
        dto.source = deployment.getSource();
        dto.deploymentTime = deployment.getDeploymentTime();
        dto.tenantId = deployment.getTenantId();
        return dto;
    }

}
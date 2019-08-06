package com.artezio.bpm.rest.dto.task;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.task.Task;

import java.util.Date;

public class TaskRepresentation {

    private String id;
    private String name;
    private String assignee;
    private Date created;
    private Date due;
    private Date followUp;
    private String delegationState;
    private String description;
    private String executionId;
    private String owner;
    private String parentTaskId;
    private int priority;
    private String processDefinitionId;
    private String processInstanceId;
    private String taskDefinitionKey;
    private String caseExecutionId;
    private String caseInstanceId;
    private String caseDefinitionId;
    private boolean suspended;
    private String formKey;
    private String tenantId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public Date getCreated() {
        return created;
    }

    public Date getDue() {
        return due;
    }

    public void setDue(Date due) {
        this.due = due;
    }

    public String getDelegationState() {
        return delegationState;
    }

    public void setDelegationState(String delegationState) {
        this.delegationState = delegationState;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public String getTaskDefinitionKey() {
        return taskDefinitionKey;
    }

    public Date getFollowUp() {
        return followUp;
    }

    public void setFollowUp(Date followUp) {
        this.followUp = followUp;
    }

    public String getCaseDefinitionId() {
        return caseDefinitionId;
    }

    public String getCaseExecutionId() {
        return caseExecutionId;
    }

    public String getCaseInstanceId() {
        return caseInstanceId;
    }

    public void setCaseInstanceId(String caseInstanceId) {
        this.caseInstanceId = caseInstanceId;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public String getFormKey() {
        return formKey;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public static TaskRepresentation fromEntity(Task task) {
        if (task == null) {
            return null;
        }
        TaskRepresentation dto = new TaskRepresentation();
        dto.id = task.getId();
        dto.name = task.getName();
        dto.assignee = task.getAssignee();
        dto.created = task.getCreateTime();
        dto.due = task.getDueDate();
        dto.followUp = task.getFollowUpDate();

        if (task.getDelegationState() != null) {
            dto.delegationState = task.getDelegationState().toString();
        }

        dto.description = task.getDescription();
        dto.executionId = task.getExecutionId();
        dto.owner = task.getOwner();
        dto.parentTaskId = task.getParentTaskId();
        dto.priority = task.getPriority();
        dto.processDefinitionId = task.getProcessDefinitionId();
        dto.processInstanceId = task.getProcessInstanceId();
        dto.taskDefinitionKey = task.getTaskDefinitionKey();
        dto.caseDefinitionId = task.getCaseDefinitionId();
        dto.caseExecutionId = task.getCaseExecutionId();
        dto.caseInstanceId = task.getCaseInstanceId();
        dto.suspended = task.isSuspended();
        dto.tenantId = task.getTenantId();

        try {
            dto.formKey = task.getFormKey();
        } catch (BadUserRequestException e) {
            // ignore (initializeFormKeys was not called)
        }
        return dto;
    }

}

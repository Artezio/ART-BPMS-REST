package com.artezio.bpm.rest.query.task;

import io.swagger.v3.oas.annotations.Hidden;

import java.util.Date;

public class TaskQueryParams {

    @Hidden
    private Date dueDate;
    @Hidden
    private String dueDateExpression;
    @Hidden
    private Date dueAfter;
    @Hidden
    private String dueAfterExpression;
    @Hidden
    private Date dueBefore;
    @Hidden
    private String dueBeforeExpression;
    @Hidden
    private Date followUpDate;
    @Hidden
    private String followUpDateExpression;
    @Hidden
    private Date followUpAfter;
    @Hidden
    private String followUpAfterExpression;
    @Hidden
    private Date followUpBefore;
    @Hidden
    private String followUpBeforeExpression;
    @Hidden
    private Date followUpBeforeOrNotExistent;
    @Hidden
    private String followUpBeforeOrNotExistentExpression;

    public Date getDueDate() {
        return dueDate;
    }

    public void setDueDate(Date dueDate) {
        this.dueDate = dueDate;
    }

    public String getDueDateExpression() {
        return dueDateExpression;
    }

    public void setDueDateExpression(String dueDateExpression) {
        this.dueDateExpression = dueDateExpression;
    }

    public Date getDueAfter() {
        return dueAfter;
    }

    public void setDueAfter(Date dueAfter) {
        this.dueAfter = dueAfter;
    }

    public String getDueAfterExpression() {
        return dueAfterExpression;
    }

    public void setDueAfterExpression(String dueAfterExpression) {
        this.dueAfterExpression = dueAfterExpression;
    }

    public Date getDueBefore() {
        return dueBefore;
    }

    public void setDueBefore(Date dueBefore) {
        this.dueBefore = dueBefore;
    }

    public String getDueBeforeExpression() {
        return dueBeforeExpression;
    }

    public void setDueBeforeExpression(String dueBeforeExpression) {
        this.dueBeforeExpression = dueBeforeExpression;
    }

    public Date getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(Date followUpDate) {
        this.followUpDate = followUpDate;
    }

    public String getFollowUpDateExpression() {
        return followUpDateExpression;
    }

    public void setFollowUpDateExpression(String followUpDateExpression) {
        this.followUpDateExpression = followUpDateExpression;
    }

    public Date getFollowUpAfter() {
        return followUpAfter;
    }

    public void setFollowUpAfter(Date followUpAfter) {
        this.followUpAfter = followUpAfter;
    }

    public String getFollowUpAfterExpression() {
        return followUpAfterExpression;
    }

    public void setFollowUpAfterExpression(String followUpAfterExpression) {
        this.followUpAfterExpression = followUpAfterExpression;
    }

    public Date getFollowUpBefore() {
        return followUpBefore;
    }

    public void setFollowUpBefore(Date followUpBefore) {
        this.followUpBefore = followUpBefore;
    }

    public String getFollowUpBeforeExpression() {
        return followUpBeforeExpression;
    }

    public void setFollowUpBeforeExpression(String followUpBeforeExpression) {
        this.followUpBeforeExpression = followUpBeforeExpression;
    }

    public Date getFollowUpBeforeOrNotExistent() {
        return followUpBeforeOrNotExistent;
    }

    public void setFollowUpBeforeOrNotExistent(Date followUpBeforeOrNotExistent) {
        this.followUpBeforeOrNotExistent = followUpBeforeOrNotExistent;
    }

    public String getFollowUpBeforeOrNotExistentExpression() {
        return followUpBeforeOrNotExistentExpression;
    }

    public void setFollowUpBeforeOrNotExistentExpression(String followUpBeforeOrNotExistentExpression) {
        this.followUpBeforeOrNotExistentExpression = followUpBeforeOrNotExistentExpression;
    }
}

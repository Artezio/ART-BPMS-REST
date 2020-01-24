package com.artezio.forms;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

public interface FormClient {
    String getFormWithData(String formPath, String deploymentId, ObjectNode variables);
    String dryValidationAndCleanup(String formPath, String deploymentId, ObjectNode submittedVariables, ObjectNode taskVariables);
    boolean shouldProcessSubmission(String formPath, String deploymentId, String submissionState);
    List<String> getFormVariableNames(String formPath, String deploymentId);
}

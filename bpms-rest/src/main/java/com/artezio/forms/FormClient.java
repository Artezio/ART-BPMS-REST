package com.artezio.forms;

import java.util.List;
import java.util.Map;

public interface FormClient {

    String getFormWithData(String deploymentId, String formPath, Map<String, Object> variables);
    String dryValidationAndCleanup(String deploymentId, String formPath, Map<String, Object> variables);//TODO remove
    boolean shouldProcessSubmission(String deploymentId, String formPath, String submissionState);

    String dryValidationAndCleanup(String deploymentId, String formKey, Map<String, Object> submittedData, Map<String, Object> currentData);
    List<String> getFormVariableNames(String deploymentId, String formKey);

}

package com.artezio.forms;

import java.util.Map;

public interface FormClient {

    String getFormWithData(String deploymentId, String formPath, Map<String, Object> variables);
    String dryValidationAndCleanup(String deploymentId, String formPath, Map<String, Object> variables);
    boolean shouldProcessSubmission(String deploymentId, String formPath, String submissionState);

}

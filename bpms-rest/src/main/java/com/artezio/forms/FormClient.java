package com.artezio.forms;

import java.util.List;
import java.util.Map;

public interface FormClient {

    String getFormWithData(String formKey, Map<String, Object> formVariables);
    void uploadForm(String formDefinition);
    boolean shouldProcessSubmittedData(String formKey, String submissionState);
    String dryValidationAndCleanup(String formKey, Map<String, Object> submittedData, Map<String, Object> currentData);    
    List<String> getFormVariableNames(String formKey);
    String getFormId(String formKey);

}

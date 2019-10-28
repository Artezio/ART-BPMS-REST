package com.artezio.forms;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface FormClient {

    String getFormWithData(String formPath, Map<String, Object> variables);
    void uploadForm(String formDefinition);
    String dryValidationAndCleanup(String formPath, Map<String, Object> variables);
    JsonNode getFormDefinition(String formPath);
    boolean shouldProcessSubmittedData(String formPath, String submissionState);

}

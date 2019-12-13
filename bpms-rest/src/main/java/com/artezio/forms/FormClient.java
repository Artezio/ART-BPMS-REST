package com.artezio.forms;

import java.util.Map;

public interface FormClient {

    String getFormWithData(String formPath, Map<String, Object> variables);
    String dryValidationAndCleanup(String formPath, Map<String, Object> variables);
    boolean shouldProcessSubmittedData(String formPath, String submissionState);

}

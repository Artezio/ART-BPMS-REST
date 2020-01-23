package com.artezio.forms;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

public interface FormClient {
    String getFormWithData(String formPath, String versionId, ObjectNode variables);
    String dryValidationAndCleanup(String formPath, String versionId, ObjectNode variables);
    boolean shouldProcessSubmission(String formPath, String versionId, String submissionState);
}

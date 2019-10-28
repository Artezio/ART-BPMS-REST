package com.artezio.forms.formio.exceptions;

public class FormValidationException extends RuntimeException {
    public FormValidationException(String validationErrorDetails) {
        super("Form validation hasn't been passed. Cause: " + validationErrorDetails);
    }
}

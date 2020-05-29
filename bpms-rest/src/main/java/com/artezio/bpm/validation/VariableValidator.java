package com.artezio.bpm.validation;

import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import javax.inject.Inject;
import javax.validation.ValidationException;
import javax.validation.Validator;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequestScope
public class VariableValidator {

    private final Validator validator;

    @Inject
    public VariableValidator(Validator validator) {
        this.validator = validator;
    }

    @SuppressWarnings("unchecked")
    public void validate(Map<String, Object> variables) {
        variables.forEach((key, value) -> {
            if (value instanceof Collection) {
                ((Collection) value).forEach(this::validate);
            } else if (value instanceof Map) {
                ((Map) value).forEach((entryKey, entryValue) -> validate(entryValue));
            } else {
                validate(value);
            }
        });
    }

    private void validate(Object value) {
        String constraintViolationsMessage = validator.validate(value).stream()
                .sorted(Comparator.comparing(constraintViolation -> constraintViolation.getPropertyPath().toString()))
                .map(constraintViolation -> "field '" + constraintViolation.getPropertyPath().toString() + "' " + constraintViolation.getMessage())
                .collect(Collectors.joining(", "));
        if (!constraintViolationsMessage.isEmpty()) {
            throw new ValidationException(constraintViolationsMessage);
        }
    }

}

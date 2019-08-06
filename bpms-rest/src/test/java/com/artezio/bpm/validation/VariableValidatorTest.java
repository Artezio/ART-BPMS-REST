package com.artezio.bpm.validation;

import org.junit.Test;

import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

public class VariableValidatorTest {

    private VariableValidator variableValidator = new VariableValidator();
    private Validator validator;
    {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.usingContext().getValidator();
        Field validatorField;
        try {
            validatorField = variableValidator.getClass().getDeclaredField("validator");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Error while initializing VariableValidator", e);
        }
        setField(variableValidator, validatorField, validator);
    }

    @Test
    public void testValidate_VariableIsEntity_VariableIsValid() {
        TestEntity testEntity = new TestEntity("1234", 1234, "qwerty");
        Map<String, Object> variables = new HashMap<String, Object>() {{put("testEntity", testEntity);}};

        variableValidator.validate(variables);
    }

    @Test(expected = ValidationException.class)
    public void testValidate_VariableIsEntity_VariableIsNotValid() {
        TestEntity testEntity = new TestEntity("1234", null, "qwerty");
        Map<String, Object> variables = new HashMap<String, Object>() {{put("testEntity", testEntity);}};

        variableValidator.validate(variables);
    }

    @Test
    public void testValidate_VariableIsCollection_VariableIsValid() {
        TestEntity testEntity = new TestEntity("1234", 1234, "qwerty");
        TestEntity testEntity2 = new TestEntity("5678", 5678, "asdfgh");
        Map<String, Object> variables = new HashMap<String, Object>() {{put("testEntities", asList(testEntity, testEntity2));}};

        variableValidator.validate(variables);
    }

    @Test(expected = ValidationException.class)
    public void testValidate_VariableIsCollection_VariableIsNotValid() {
        TestEntity testEntity = new TestEntity("1234", 1234, "qwerty");
        TestEntity testEntity2 = new TestEntity(null, null, "asd67554fgh");
        Map<String, Object> variables = new HashMap<String, Object>() {{put("testEntities", asList(testEntity, testEntity2));}};

        variableValidator.validate(variables);
    }

    @Test
    public void testValidate_VariableIsMap_VariableIsValid() {
        TestEntity testEntity = new TestEntity("1234", 1234, "qwerty");
        TestEntity testEntity2 = new TestEntity("5678", 5678, "asdfgh");
        Map<String, Object> testEntities = new HashMap<String, Object>() {{put("testEntity", testEntity); put("testEntity2", testEntity2);}};
        Map<String, Object> variables = new HashMap<String, Object>() {{put("testEntities", testEntities);}};

        variableValidator.validate(variables);
    }

    @Test(expected = ValidationException.class)
    public void testValidate_VariableIsMap_VariableIsNotValid() {
        TestEntity testEntity = new TestEntity("1234", 1234, "qwerty");
        TestEntity testEntity2 = new TestEntity(null, null, "asd67554fgh");
        Map<String, Object> testEntities = new HashMap<String, Object>() {{put("testEntity", testEntity); put("testEntity2", testEntity2);}};
        Map<String, Object> variables = new HashMap<String, Object>() {{put("testEntities", testEntities);}};

        variableValidator.validate(variables);
    }

}

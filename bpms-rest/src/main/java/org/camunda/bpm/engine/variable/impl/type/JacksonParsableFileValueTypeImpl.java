package org.camunda.bpm.engine.variable.impl.type;

import org.camunda.bpm.engine.variable.type.ValueType;
import spinjar.com.fasterxml.jackson.annotation.JsonIgnore;
import spinjar.com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class JacksonParsableFileValueTypeImpl extends FileValueTypeImpl {

    @Override
    @JsonIgnore
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isPrimitiveValueType() {
        return true;
    }

    @Override
    @JsonIgnore
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isAbstract() {
        return super.isAbstract();
    }

    @Override
    @JsonIgnore
    @com.fasterxml.jackson.annotation.JsonIgnore
    public ValueType getParent() {
        return super.getParent();
    }

    @Override
    @JsonIgnore
    @com.fasterxml.jackson.annotation.JsonIgnore
    protected Boolean isTransient(Map<String, Object> valueInfo) {
        return super.isTransient(valueInfo);
    }

    @JsonProperty(value = "name")
    @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
    public void setName(String name) {
        this.name = name;
    }

}

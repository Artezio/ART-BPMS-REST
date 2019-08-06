package com.artezio.bpm.services;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelObject {

    public static class NestedEntity {
        private String stringField;
        private Integer integerField;

        public String getStringField() {
            return stringField;
        }

        public void setStringField(String stringField) {
            this.stringField = stringField;
        }

        public Integer getIntegerField() {
            return integerField;
        }

        public void setIntegerField(Integer integerField) {
            this.integerField = integerField;
        }

        @Override
        public String toString() {
            return "NestedEntity [stringField=" + stringField + ", integerField=" + integerField + "]";
        }
    }

    private String stringField;
    private Boolean booleanField;
    private List<String> stringListField;
    private Map<String, Object> mapField;
    private NestedEntity nestedEntityField;

    public String getStringField() {
        return stringField;
    }

    public void setStringField(String stringField) {
        this.stringField = stringField;
    }

    public Boolean getBooleanField() {
        return booleanField;
    }

    public void setBooleanField(Boolean booleanField) {
        this.booleanField = booleanField;
    }

    public List<String> getStringListField() {
        return stringListField;
    }

    public void setStringListField(List<String> stringListField) {
        this.stringListField = stringListField;
    }

    public Map<String, Object> getMapField() {
        return mapField;
    }

    public void setMapField(Map<String, Object> mapField) {
        this.mapField = mapField;
    }

    public NestedEntity getNestedEntityField() {
        return nestedEntityField;
    }

    public void setNestedEntityField(NestedEntity nestedEntityField) {
        this.nestedEntityField = nestedEntityField;
    }

    @Override
    public String toString() {
        return "ModelObject [stringField=" + stringField + ", booleanField=" + booleanField + ", stringListField="
                + stringListField + ", mapField=" + mapField + ", nestedEntityField=" + nestedEntityField + "]";
    }

}

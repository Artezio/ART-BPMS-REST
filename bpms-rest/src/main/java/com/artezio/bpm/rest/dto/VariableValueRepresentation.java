package com.artezio.bpm.rest.dto;

import com.artezio.bpm.rest.exception.InvalidRequestException;
import com.artezio.bpm.rest.exception.RestException;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.digest._apacheCommonsCodec.Base64;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.type.*;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.bpm.engine.variable.value.SerializableValue;
import org.camunda.bpm.engine.variable.value.TypedValue;
import spinjar.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

public class VariableValueRepresentation {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected String type;
    protected Object value;
    protected Map<String, Object> valueInfo;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Map<String, Object> getValueInfo() {
        return valueInfo;
    }

    public void setValueInfo(Map<String, Object> valueInfo) {
        this.valueInfo = valueInfo;
    }

    public TypedValue toTypedValue(ProcessEngine processEngine) {
        ValueTypeResolver valueTypeResolver = processEngine.getProcessEngineConfiguration().getValueTypeResolver();

        if (type == null) {
            if (valueInfo != null && valueInfo.get(ValueType.VALUE_INFO_TRANSIENT) instanceof Boolean) {
                return Variables.untypedValue(value, (Boolean) valueInfo.get(ValueType.VALUE_INFO_TRANSIENT));
            }
            return Variables.untypedValue(value);
        }

        ValueType valueType = valueTypeResolver.typeForName(fromRestApiTypeName(type));
        if (valueType == null) {
            throw new RestException(BAD_REQUEST, String.format("Unsupported value type '%s'", type));
        } else {
            if (valueType instanceof PrimitiveValueType) {
                PrimitiveValueType primitiveValueType = (PrimitiveValueType) valueType;
                Class<?> javaType = primitiveValueType.getJavaType();
                Object mappedValue = null;
                try {
                    if (value != null) {
                        if (javaType.isAssignableFrom(value.getClass())) {
                            mappedValue = value;
                        } else {
                            // use jackson to map the value to the requested java type
                            mappedValue = OBJECT_MAPPER.readValue("\"" + value + "\"", javaType);
                        }
                    }
                    return valueType.createValue(mappedValue, valueInfo);
                } catch (Exception e) {
                    throw new com.artezio.bpm.rest.exception.InvalidRequestException(BAD_REQUEST, e,
                            String.format("Cannot convert value '%s' of type '%s' to java type %s", value, type, javaType.getName()));
                }
            } else if (valueType instanceof SerializableValueType) {
                if (value != null && !(value instanceof String)) {
                    throw new InvalidRequestException(BAD_REQUEST, "Must provide 'null' or String value for value of SerializableValue type '" + type + "'.");
                }
                return ((SerializableValueType) valueType).createValueFromSerialized((String) value, valueInfo);
            } else if (valueType instanceof FileValueType) {

                if (value instanceof String) {
                    value = Base64.decodeBase64((String) value);
                }

                return valueType.createValue(value, valueInfo);
            } else {
                return valueType.createValue(value, valueInfo);
            }
        }

    }

    protected FileValue fileValueWithDecodedString(FileValue fileValue, String value) {
        return Variables.fileValue(fileValue.getFilename())
                .file(Base64.decodeBase64(value))
                .mimeType(fileValue.getMimeType())
                .encoding(fileValue.getEncoding())
                .create();
    }

    public static VariableMap toMap(Map<String, VariableValueRepresentation> variables, ProcessEngine processEngine) {
        if (variables == null) {
            return null;
        }

        VariableMap result = Variables.createVariables();
        variables.forEach((key, value) -> result.put(key, value.toTypedValue(processEngine)));

        return result;
    }

    public static VariableValueRepresentation fromTypedValue(TypedValue typedValue) {
        VariableValueRepresentation dto = new VariableValueRepresentation();
        fromTypedValue(dto, typedValue);
        return dto;
    }

    public static VariableValueRepresentation fromTypedValue(TypedValue typedValue, boolean preferSerializedValue) {
        VariableValueRepresentation dto = new VariableValueRepresentation();
        fromTypedValue(dto, typedValue, preferSerializedValue);
        return dto;
    }

    public static void fromTypedValue(VariableValueRepresentation dto, TypedValue typedValue) {
        fromTypedValue(dto, typedValue, false);
    }

    public static void fromTypedValue(VariableValueRepresentation dto, TypedValue typedValue, boolean preferSerializedValue) {

        ValueType type = typedValue.getType();
        if (type != null) {
            String typeName = type.getName();
            dto.setType(toRestApiTypeName(typeName));
            dto.setValueInfo(type.getValueInfo(typedValue));
        }

        if (typedValue instanceof SerializableValue) {
            SerializableValue serializableValue = (SerializableValue) typedValue;

            if (serializableValue.isDeserialized() && !preferSerializedValue) {
                dto.setValue(serializableValue.getValue());
            } else {
                dto.setValue(serializableValue.getValueSerialized());
            }

        } else if (typedValue instanceof FileValue) {
            //do not set the value for FileValues since we don't want to send megabytes over the network without explicit request
        } else {
            dto.setValue(typedValue.getValue());
        }

    }

    public static String toRestApiTypeName(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public static String fromRestApiTypeName(String name) {
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    public static Map<String, VariableValueRepresentation> fromVariableMap(VariableMap variables) {
        Map<String, VariableValueRepresentation> result = new HashMap<>();
        for (String name : variables.keySet()) {
            result.put(name, fromTypedValue(variables.getValueTyped(name)));
        }
        return result;
    }

    //TODO check if this is needed
//  public static VariableValueRepresentation fromFormPart(String type, FormPart binaryDataFormPart) {
//    VariableValueRepresentation dto = new VariableValueRepresentation();
//
//    dto.type = type;
//    dto.value = binaryDataFormPart.getBinaryContent();
//
//    if (ValueType.FILE.getName().equals(fromRestApiTypeName(type))) {
//
//      String contentType = binaryDataFormPart.getContentType();
//      if (contentType == null) {
//        contentType = MediaType.APPLICATION_OCTET_STREAM;
//      }
//
//      dto.valueInfo = new HashMap<String, Object>();
//      dto.valueInfo.put(FileValueType.VALUE_INFO_FILE_NAME, binaryDataFormPart.getFileName());
//      MimeType mimeType = null;
//      try {
//        mimeType = new MimeType(contentType);
//      } catch (MimeTypeParseException e) {
//        throw new RestException(Response.Status.BAD_REQUEST, "Invalid mime type given");
//      }
//
//      dto.valueInfo.put(FileValueType.VALUE_INFO_FILE_MIME_TYPE, mimeType.getBaseType());
//
//      String encoding = mimeType.getParameter("encoding");
//      if (encoding != null) {
//        dto.valueInfo.put(FileValueType.VALUE_INFO_FILE_ENCODING, encoding);
//      }
//
//      String transientString = mimeType.getParameter("transient");
//      boolean isTransient = Boolean.parseBoolean(transientString);
//      if (isTransient) {
//        dto.valueInfo.put(AbstractValueTypeImpl.VALUE_INFO_TRANSIENT, isTransient);
//      }
//    }
//
//    return dto;
//
//
//  }

}
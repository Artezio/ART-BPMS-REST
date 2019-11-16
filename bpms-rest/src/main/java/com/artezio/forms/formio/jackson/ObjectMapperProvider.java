package com.artezio.forms.formio.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.camunda.bpm.engine.variable.impl.value.FileValueImpl;
import org.camunda.bpm.engine.variable.value.FileValue;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

@Provider
public class ObjectMapperProvider implements ContextResolver<ObjectMapper> {

    private ObjectMapper objectMapper = createObjectMapper();

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return objectMapper;
    }

    ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        registerFileValueSerializers(objectMapper);
        return objectMapper;
    }

    public static void registerFileValueSerializers(ObjectMapper mapper) {
        SimpleModule module = new SimpleModule();
        module.addSerializer(FileValue.class, new FileValueSerializer<>(FileValue.class));
        module.addSerializer(FileValueImpl.class, new FileValueSerializer<>(FileValueImpl.class));
        module.addDeserializer(FileValue.class, new FileValueDeserializer<>(FileValue.class));
        module.addDeserializer(FileValueImpl.class, new FileValueDeserializer<>(FileValueImpl.class));
        mapper.registerModule(module);
    }

}

package com.artezio.camunda.spinjar.jackson;

import org.camunda.bpm.engine.variable.impl.value.FileValueImpl;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.spin.impl.json.jackson.format.JacksonJsonDataFormat;
import org.camunda.spin.spi.DataFormatConfigurator;

import javax.ws.rs.core.MediaType;

public class JacksonDataFormatConfigurator implements DataFormatConfigurator<JacksonJsonDataFormat> {

    public JacksonDataFormatConfigurator() {
    }

    @Override
    public void configure(JacksonJsonDataFormat dataFormat) {
        if (dataFormat.getName().equals(MediaType.APPLICATION_JSON)
                && dataFormat.getObjectMapper().getClass().equals(spinjar.com.fasterxml.jackson.databind.ObjectMapper.class)) {
            spinjar.com.fasterxml.jackson.databind.ObjectMapper objectMapper = dataFormat.getObjectMapper();
            registerSpinjarFileValueSerializers(objectMapper);
        }
    }

    public static void registerSpinjarFileValueSerializers(spinjar.com.fasterxml.jackson.databind.ObjectMapper mapper) {
        spinjar.com.fasterxml.jackson.databind.module.SimpleModule module = new spinjar.com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(FileValue.class, new com.artezio.camunda.spinjar.jackson.FileValueSerializer<>(FileValue.class));
        module.addSerializer(FileValueImpl.class, new com.artezio.camunda.spinjar.jackson.FileValueSerializer<>(FileValueImpl.class));
        module.addDeserializer(FileValue.class, new com.artezio.camunda.spinjar.jackson.FileValueDeserializer<>(FileValue.class));
        module.addDeserializer(FileValueImpl.class, new com.artezio.camunda.spinjar.jackson.FileValueDeserializer<>(FileValueImpl.class));
        mapper.registerModule(module);
    }

    @Override
    public Class<JacksonJsonDataFormat> getDataFormatClass() {
        return JacksonJsonDataFormat.class;
    }

}

package com.artezio.forms.formio.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.camunda.bpm.engine.variable.value.FileValue;

import java.io.IOException;

import static com.artezio.camunda.spinjar.jackson.FileValueSerializer.*;

public class FileValueSerializer<T extends FileValue> extends StdSerializer<T> {

    public FileValueSerializer(Class<T> exactClass) {
        super(exactClass);
    }

    @Override
    public void serialize(T file, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        jgen.writeStringField("type", getMimeType(file));
        jgen.writeStringField("name", file.getFilename());
        jgen.writeStringField("originalName", file.getFilename());
        jgen.writeStringField("url", getUrl(file));
        jgen.writeNumberField("size", file.getValue().available());
        jgen.writeStringField("storage", isExternalFile(file) ? "url" : "base64");
        jgen.writeEndObject();
    }

}
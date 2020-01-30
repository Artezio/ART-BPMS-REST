package com.artezio.camunda.spinjar.jackson;

import com.artezio.bpm.services.integration.FileStorage;
import com.artezio.bpm.services.integration.cdi.ConcreteImplementation;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.variable.value.FileValue;
import spinjar.com.fasterxml.jackson.core.JsonGenerator;
import spinjar.com.fasterxml.jackson.databind.SerializerProvider;
import spinjar.com.fasterxml.jackson.databind.ser.std.StdSerializer;

import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

public class FileValueSerializer<T extends FileValue> extends StdSerializer<T> {

    public FileValueSerializer(Class<T> exactClass) {
        super(exactClass);
    }

    @Override
    public void serialize(T file, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        String mimeType = Optional.ofNullable(file.getMimeType()).orElse(APPLICATION_OCTET_STREAM);
        String fileValue;
        if (!mimeType.startsWith(MediaType.BPM_FILE_VALUE)) {
            fileValue = getFileStorage().store(file.getValue());
        } else {
            mimeType = mimeType.substring(mimeType.indexOf('/'));
            fileValue = IOUtils.toString(file.getValue(), StandardCharsets.UTF_8);
        }
        jgen.writeStringField("url", fileValue);
        jgen.writeNumberField("size", fileValue.length());
        jgen.writeStringField("mimeType", mimeType);
        jgen.writeStringField("filename", file.getFilename());
        jgen.writeStringField("encoding", file.getEncoding());
        jgen.writeEndObject();
    }

    private FileStorage getFileStorage() {
        return CDI.current()
                .select(FileStorage.class)
                .select(new AnnotationLiteral<Default>() {
                }).get();
    }

}
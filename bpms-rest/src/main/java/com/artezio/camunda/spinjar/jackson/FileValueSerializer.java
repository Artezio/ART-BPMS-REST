package com.artezio.camunda.spinjar.jackson;

import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.camunda.bpm.engine.variable.value.FileValue;
import spinjar.com.fasterxml.jackson.core.JsonGenerator;
import spinjar.com.fasterxml.jackson.databind.SerializerProvider;
import spinjar.com.fasterxml.jackson.databind.ser.std.StdSerializer;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Optional;

public class FileValueSerializer<T extends FileValue> extends StdSerializer<T> {

    private static final String FILE_STORAGE_URL = System.getenv("FILE_STORAGE_URL");

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
        if (!isExternalFile(file)) {
            jgen.writeNumberField("size", file.getValue().available());
        }
        jgen.writeStringField("storage", isExternalFile(file) ? "url" : "base64");
        jgen.writeEndObject();
    }

    public static String getUrl(FileValue fileValue) {
        if (isExternalFile(fileValue)) {
            return getExternalFileUrl(fileValue);
        } else {
            return encodeFileToBase64Url(fileValue);
        }
    }

    public static String getMimeType(FileValue fileValue) {
        String mimeType = Optional.ofNullable(fileValue.getMimeType()).orElse(guessMimeType(fileValue));
        if (isExternalFile(fileValue)) {
            return mimeType.split("/", 2)[1];
        } else {
            return mimeType;
        }
    }

    public static boolean isExternalFile(FileValue fileValue) {
        return Optional.ofNullable(fileValue.getMimeType())
                .map(mimeType -> mimeType.startsWith(com.artezio.camunda.spinjar.jackson.MediaType.BPM_FILE_VALUE))
                .orElse(false);
    }

    private static String getExternalFileUrl(FileValue fileValue) {
        try {
            StringBuilder stringBuilder = new StringBuilder();
            String fileId = IOUtils.toString(fileValue.getValue(), Charset.forName("UTF-8"));
            if (FILE_STORAGE_URL != null) {
                stringBuilder.append(FILE_STORAGE_URL);
                if (!FILE_STORAGE_URL.endsWith("/")) {
                    stringBuilder.append("/");
                }
            }
            stringBuilder.append(fileId);
            return stringBuilder.toString();
        } catch (IOException e) {
            throw new RuntimeException("Could not read URL from FileValue", e);
        }
    }

    private static String encodeFileToBase64Url(FileValue fileValue) {
        try {
            return String.format("data:%s;base64,%s", getMimeType(fileValue), Base64.getMimeEncoder().encodeToString(IOUtils.toByteArray(fileValue.getValue())));
        } catch (IOException e) {
            throw new RuntimeException("Could not encode FileValue to Base64 string", e);
        }
    }

    private static String guessMimeType(FileValue fileValue) {
        try {
            String guessedContentType = new Tika().detect(fileValue.getValue());
            return Optional
                    .ofNullable(guessedContentType)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
        } catch (IOException e) {
            throw new RuntimeException("Could not read FileValue input stream for detecting its Mime type", e);
        }
    }
}
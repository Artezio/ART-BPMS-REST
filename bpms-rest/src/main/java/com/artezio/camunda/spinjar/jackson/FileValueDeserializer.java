package com.artezio.camunda.spinjar.jackson;

import com.artezio.bpm.services.integration.FileStorage;
import com.artezio.bpm.services.integration.cdi.ConcreteImplementation;
import com.artezio.bpm.utils.Base64Utils;
import spinjar.com.fasterxml.jackson.core.JsonParser;
import spinjar.com.fasterxml.jackson.core.JsonProcessingException;
import spinjar.com.fasterxml.jackson.databind.DeserializationContext;
import spinjar.com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.camunda.bpm.engine.variable.impl.value.builder.FileValueBuilderImpl;
import org.camunda.bpm.engine.variable.value.FileValue;
import spinjar.com.fasterxml.jackson.databind.JsonNode;

import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;
import java.io.IOException;
import java.nio.charset.Charset;

public class FileValueDeserializer <T extends FileValue> extends StdDeserializer<T> {

    public FileValueDeserializer(Class<?> exactClass) {
        super(exactClass);
    }

    @Override
    public T deserialize(JsonParser parser, DeserializationContext context) throws IOException, JsonProcessingException {
        JsonNode node = parser.getCodec().readTree(parser);
        String url = node.get("url").asText();
        return (T)new FileValueBuilderImpl(node.get("originalName").asText())
                .encoding("UTF-8")
                .mimeType(wrapMimeType(node))
                .file(getFileContent(url))
                .create();
    }

    private String wrapMimeType(JsonNode node) {
        String mimeType = node.get("type").asText();
        String url = node.get("url").asText();
        if (Base64Utils.isBase64DataUrl(url)) {
            return mimeType.startsWith(MediaType.BPM_FILE_VALUE)
                    ? mimeType
                    : MediaType.BPM_FILE_VALUE + "/" + mimeType;
        } else {
            return mimeType;
        }
    }

    public static byte[] getFileContent(String url) {
        if (Base64Utils.isBase64DataUrl(url)) {
            String fileId = getFileStorage().store(Base64Utils.getData(url));
            return fileId.getBytes(Charset.forName("UTF-8"));
        } else {
            return url.getBytes(Charset.forName("UTF-8"));
        }
    }

    private static FileStorage getFileStorage() {
        return CDI.current().select(FileStorage.class, new AnnotationLiteral<ConcreteImplementation>() {
        }).get();
    }
}

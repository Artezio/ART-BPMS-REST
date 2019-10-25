package com.artezio.forms.formio.jackson;

import com.artezio.bpm.utils.Base64Utils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.camunda.bpm.engine.variable.impl.value.builder.FileValueBuilderImpl;
import org.camunda.bpm.engine.variable.value.FileValue;
import com.artezio.camunda.spinjar.jackson.MediaType;

import java.io.IOException;

import static com.artezio.camunda.spinjar.jackson.FileValueDeserializer.getFileContent;

public class FileValueDeserializer<T extends FileValue> extends StdDeserializer<T> {

    public FileValueDeserializer(Class<?> exactClass) {
        super(exactClass);
    }

    @Override
    public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
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
            return mimeType;
        } else {
            return MediaType.BPM_FILE_VALUE + "/" + mimeType;
        }
    }

}

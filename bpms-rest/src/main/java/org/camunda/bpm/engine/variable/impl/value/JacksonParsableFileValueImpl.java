package org.camunda.bpm.engine.variable.impl.value;

import org.camunda.bpm.engine.variable.impl.type.FileValueTypeImpl;
import org.camunda.bpm.engine.variable.impl.type.JacksonParsableFileValueTypeImpl;
import org.camunda.bpm.engine.variable.value.FileValue;
import spinjar.com.fasterxml.jackson.annotation.JsonIgnore;
import spinjar.com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.charset.Charset;

public class JacksonParsableFileValueImpl extends FileValueImpl {

    public JacksonParsableFileValueImpl() {
        super(null, null);
    }

    public JacksonParsableFileValueImpl(FileValue fileValue) {
        super(((FileValueImpl) fileValue).getByteArray(), new JacksonParsableFileValueTypeImpl(), fileValue.getFilename(),
                fileValue.getMimeType(), fileValue.getEncoding());
    }

    @Override
    @JsonProperty(value = "value")
    @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
    public byte[] getByteArray() {
        return super.getByteArray();
    }

    @Override
    @JsonProperty("type")
    @com.fasterxml.jackson.annotation.JsonProperty("type")
    public FileValueTypeImpl getType() {
        return (FileValueTypeImpl) super.getType();
    }

    @JsonProperty("type")
    @com.fasterxml.jackson.annotation.JsonProperty("type")
    public void setType(FileValueTypeImpl type) {
        this.type = type;
    }

    @Override
    @JsonIgnore
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Charset getEncodingAsCharset() {
        return super.getEncodingAsCharset();
    }

}

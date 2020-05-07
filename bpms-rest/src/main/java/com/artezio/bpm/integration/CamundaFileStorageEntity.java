package com.artezio.bpm.integration;

import com.artezio.forms.storages.FileStorageEntity;
import org.camunda.bpm.engine.variable.value.FileValue;

import java.io.InputStream;

public class CamundaFileStorageEntity implements FileStorageEntity {

    private String id;
    private String name;
    private String mimeType;
    private InputStream content;
    private String url;
    private String storage;

    public CamundaFileStorageEntity(String id, FileValue fileValue) {
        this.id = id;
        name = fileValue.getFilename();
        mimeType = fileValue.getMimeType();
        content = fileValue.getValue();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public InputStream getContent() {
        return content;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String getStorage() {
        return storage;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @Override
    public void setContent(InputStream content) {
        this.content = content;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public void setStorage(String storage) {
        this.storage = storage;
    }
}

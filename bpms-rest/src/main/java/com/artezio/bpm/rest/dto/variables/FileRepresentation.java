package com.artezio.bpm.rest.dto.variables;

import java.util.Map;
import java.util.Objects;

public class FileRepresentation {

    private String fileName;
    private String mimeType;
    private String url;
    private String size;

    public FileRepresentation(Map<String, Object> file, String filePath) {
        fileName = (String) file.get("filename");
        mimeType = (String) file.get("mimeType");
        size = String.valueOf(((String) file.get("value")).length());
        url = filePath + "[?(@.filename == '" + fileName + "')]";
    }

    public String getFileName() {
        return fileName;
    }

    public String getUrl() {
        return url;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getSize() {
        return size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileRepresentation that = (FileRepresentation) o;
        return fileName.equals(that.fileName) &&
                mimeType.equals(that.mimeType) &&
                url.equals(that.url) &&
                size.equals(that.size);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, mimeType, url, size);
    }

}

package com.artezio.bpm.services.integration;

import com.artezio.bpm.services.integration.cdi.DefaultImplementation;
import com.artezio.logging.Log;
import org.apache.commons.io.IOUtils;

import javax.inject.Named;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Base64;

import static com.artezio.logging.Log.Level.CONFIG;

@Named
@DefaultImplementation
public class Base64UrlFileStorage implements FileStorage {

    @Override
    @Log(level = CONFIG, beforeExecuteMessage = "Saving file in storage")
    public String store(InputStream dataStream) {
        try {
            byte[] data = IOUtils.toByteArray(dataStream);
            String encodedBytes = Base64.getMimeEncoder().encodeToString(data);
            String dataHeader = String.format("data:%s;base64,", guessContentType(data));
            return dataHeader + encodedBytes;
        } catch (IOException e) {
            throw new RuntimeException("An error occured while serializing the file to base64 url components", e);
        }
    }

    @Override
    public InputStream retrieve(String id) {
        return IOUtils.toInputStream(id, Charset.forName("UTF-8"));
    }

    private String guessContentType(byte[] data) {
        try (InputStream is = new BufferedInputStream(new ByteArrayInputStream(data))) {
            return URLConnection.guessContentTypeFromStream(is);
        } catch (IOException e) {
            throw new RuntimeException("Exception while reading bytes from array.", e);
        }
    }
}

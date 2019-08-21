package com.artezio.camunda.spinjar.jackson;

import spinjar.com.fasterxml.jackson.databind.ObjectMapper;
import spinjar.com.jayway.jsonpath.spi.json.JacksonJsonProvider;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;

@Provider
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FileValueJacksonJsonProvider extends JacksonJsonProvider {

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        JacksonDataFormatConfigurator.registerSpinjarFileValueSerializers(objectMapper);
    }

    public FileValueJacksonJsonProvider() {
        super(objectMapper);
    }

}
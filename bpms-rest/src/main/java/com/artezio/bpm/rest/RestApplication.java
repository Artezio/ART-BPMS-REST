package com.artezio.bpm.rest;

import com.artezio.bpm.services.*;
import com.artezio.ws.rs.ExceptionMapper;
import com.artezio.ws.rs.PragmaRemover;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

import javax.ws.rs.ApplicationPath;

@Component
@ApplicationPath("/api")
public class RestApplication extends ResourceConfig {

    public RestApplication() {
        registerEndpoints();

        register(ExceptionMapper.class);
        register(PragmaRemover.class);
        register(MultiPartFeature.class);
    }

    public void registerEndpoints() {
        register(DeploymentSvc.class);
        register(ProcessDefinitionSvc.class);
        register(TaskSvc.class);
        register(MessageSvc.class);
        register(SignalSvc.class);
        register(OpenApiResource.class);
    }

}

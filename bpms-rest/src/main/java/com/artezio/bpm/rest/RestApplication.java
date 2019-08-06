package com.artezio.bpm.rest;

import com.artezio.bpm.services.*;
import com.artezio.ws.rs.ExceptionMapper;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

@ApplicationPath("/api")
public class RestApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(DeploymentSvc.class);
        classes.add(ProcessDefinitionSvc.class);
        classes.add(TaskSvc.class);
        classes.add(MessageSvc.class);
        classes.add(SignalSvc.class);

        classes.add(ExceptionMapper.class);

        classes.add(OpenApiResource.class);

        return classes;
    }

}

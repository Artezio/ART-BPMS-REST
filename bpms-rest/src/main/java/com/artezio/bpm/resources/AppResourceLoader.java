package com.artezio.bpm.resources;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletContext;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Named
public class AppResourceLoader implements AbstractResourceLoader {

    @Inject
    private ServletContext servletContext;

    @Override
    public InputStream getResource(String deploymentId, String resourceKey) {
        return servletContext.getResourceAsStream(resourceKey);
    }

    @Override
    public List<String> listResourceNames(String deploymentId) {
        return listResourceNames(deploymentId, "/");
    }

    private List<String> listResourceNames(String deploymentId, String initialPath) {
        try {
            String resourcePath = initialPath.startsWith("/") ? initialPath : "/" + initialPath;
            URL url = servletContext.getResource(resourcePath);
            if (url == null) return Collections.emptyList();
            File resource = new File(url.toURI());
            return Arrays.stream(resource.listFiles())
                    .flatMap(file -> {
                        String resourceName = initialPath + "/" + file.getName();
                        return file.isDirectory()
                                ? listResourceNames(deploymentId, resourceName).stream()
                                : Arrays.asList(resourceName).stream();
                    })
                    .collect(Collectors.toList());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
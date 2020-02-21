package com.artezio.bpm.resources;

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

public class AppResourceLoader extends AbstractResourceLoader {

    private ServletContext servletContext;

    public AppResourceLoader() {
    }

    public AppResourceLoader(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public InputStream getResource(String resourceKey) {
        resourceKey = getResourcePath(resourceKey);
        return servletContext.getResourceAsStream(resourceKey);
    }

    @Override
    public List<String> listResourceNames(String initialPath) {
        try {
            String resourcePath = initialPath.startsWith("/") ? initialPath : "/" + initialPath;
            URL url = servletContext.getResource(resourcePath);
            if (url == null) return Collections.emptyList();
            File resource = new File(url.toURI());
            return Arrays.stream(resource.listFiles())
                    .flatMap(file -> {
                        String resourceName = initialPath + "/" + file.getName();
                        return file.isDirectory()
                                ? listResourceNames(resourceName).stream()
                                : Arrays.asList(resourceName).stream();
                    })
                    .collect(Collectors.toList());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}

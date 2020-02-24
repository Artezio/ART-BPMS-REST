package com.artezio.bpm.resources;

import javax.enterprise.inject.spi.CDI;
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
import java.util.stream.Stream;

public class AppResourceLoader extends AbstractResourceLoader {

    private ServletContext servletContext;

    public AppResourceLoader(String rootDirectory) {
        super(rootDirectory);
        this.servletContext = CDI.current().select(ServletContext.class).get();
    }

    @Override
    public InputStream getResource(String resourceKey) {
        resourceKey = getResourcePath(resourceKey);
        return servletContext.getResourceAsStream(rootDirectory + "/" + resourceKey);
    }

    @Override
    public List<String> listResourceNames() {
        return listResourceNames(rootDirectory);
    }

    private List<String> listResourceNames(String resourcesDirectory) {
        try {
            String resourcePath = resourcesDirectory.startsWith("/") ? resourcesDirectory : "/" + resourcesDirectory;
            URL url = servletContext.getResource(resourcePath);
            if (url == null) return Collections.emptyList();
            File resource = new File(url.toURI());
            return Arrays.stream(resource.listFiles())
                    .flatMap(file -> {
                        String resourceName = resourcesDirectory + "/" + file.getName();
                        return file.isDirectory()
                                ? listResourceNames(resourceName).stream()
                                : Stream.of(resourceName);
                    })
                    .collect(Collectors.toList());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}

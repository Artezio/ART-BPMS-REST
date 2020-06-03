package com.artezio.bpm.resources;

import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AppResourceLoader extends AbstractResourceLoader {

    public AppResourceLoader(String rootDirectory) {
        super(rootDirectory.startsWith("/") ? rootDirectory : "/" + rootDirectory);
    }

    @Override
    public InputStream getResource(String resourceKey) {
        resourceKey = getResourcePath(resourceKey);
        return getClass().getResourceAsStream(rootDirectory + "/" + resourceKey);
    }

    @Override
    public List<String> listResourceNames() {
        return listResourceNames(rootDirectory).stream()
                .map(resourceName -> resourceName.substring((rootDirectory + "/").length()))
                .collect(Collectors.toList());
    }

    private List<String> listResourceNames(String resourcesDirectory) {
        try {
            URL url = getClass().getResource(resourcesDirectory);
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
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getGroupId() {
        return APP_PROTOCOL; 
    }

}

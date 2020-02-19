package com.artezio.bpm.resources;

import java.io.InputStream;
import java.util.List;

interface AbstractResourceLoader {

    InputStream getResource(String deploymentId, String resourceKey);

    List<String> listResourceNames(String deploymentId);

}

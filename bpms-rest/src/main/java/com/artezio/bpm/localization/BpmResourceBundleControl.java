package com.artezio.bpm.localization;

import org.apache.http.impl.io.EmptyInputStream;
import org.camunda.bpm.engine.RepositoryService;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class BpmResourceBundleControl extends ResourceBundle.Control {

    private static final String BPM_RESOURCE_NAME_PREFIX = "i18n/";
    private static final List<String> FORMAT_BPM_RESOURCES = Collections.singletonList("java.bpm.resources");

    private RepositoryService repositoryService;
    private String deploymentId;

    public BpmResourceBundleControl(String deploymentId, RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
        this.deploymentId = deploymentId;
    }

    @Override
    public List<String> getFormats(String baseName) {
        return FORMAT_BPM_RESOURCES;
    }

    @Override
    public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
            throws IOException {
        String requestedPropertyResourceName = BPM_RESOURCE_NAME_PREFIX + toResourceName(toBundleName(baseName, locale), "properties");
        InputStream propertyResource = null;
        try {
            List<String> deploymentPropertyResourceNames = findAppropriatePropertyResourceNames(BPM_RESOURCE_NAME_PREFIX + baseName, locale.getLanguage());
            String resultPropertyResourceName = findExactlyMatchingPropertyResourceName(requestedPropertyResourceName, deploymentPropertyResourceNames);
            if (resultPropertyResourceName == null) {
                resultPropertyResourceName = findMostAppropriatePropertyResourceName(requestedPropertyResourceName, deploymentPropertyResourceNames);
            }
            propertyResource = resultPropertyResourceName != null
                    ? repositoryService.getResourceAsStream(deploymentId, resultPropertyResourceName)
                    : EmptyInputStream.INSTANCE;

            return new PropertyResourceBundle(propertyResource);
        } finally {
            if (propertyResource != null) {
                propertyResource.close();
            }
        }
    }

    @Override
    public List<Locale> getCandidateLocales(String baseName, Locale locale) {
        return asList(locale);
    }

    private List<String> findAppropriatePropertyResourceNames(String resourceBaseName, String language) {
        return repositoryService.getDeploymentResourceNames(deploymentId).stream()
                .filter(deploymentResourceName -> deploymentResourceName.contains(resourceBaseName + "_" + language))
                .collect(Collectors.toList());
    }

    private String findExactlyMatchingPropertyResourceName(String requestedResourceName, List<String> deploymentResourceNames) {
        return deploymentResourceNames.stream()
                .filter(deploymentResourceName -> deploymentResourceName.equals(requestedResourceName))
                .findFirst()
                .orElse(null);
    }

    private String findMostAppropriatePropertyResourceName(String requestedResourceName, List<String> deploymentResourceNames) {
        String deploymentResourceName = null;
        int underscoreLastIndex;
        while (deploymentResourceName == null
                && (underscoreLastIndex = requestedResourceName.lastIndexOf('_')) != -1) {
            requestedResourceName = requestedResourceName.substring(0, underscoreLastIndex).concat(".properties");
            deploymentResourceName = findExactlyMatchingPropertyResourceName(requestedResourceName, deploymentResourceNames);
        }
        return deploymentResourceName;
    }

}

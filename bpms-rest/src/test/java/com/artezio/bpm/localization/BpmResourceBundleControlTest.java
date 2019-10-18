package com.artezio.bpm.localization;

import org.camunda.bpm.engine.RepositoryService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BpmResourceBundleControlTest {

    private String deploymentId = "deploymentId";
    @Mock
    private RepositoryService repositoryService;
    @InjectMocks
    private BpmResourceBundleControl resourceBundleControl = new BpmResourceBundleControl(deploymentId, null);

    @Test
    public void testNewBundle_PassedLangRangeWith_LangCode_Script_Region_Variant_ThereIsExactlyMatchingResource() throws IOException {
        String baseName = "test-resource";
        String resourceName = "i18n/" + baseName + "_tl_Tscr_TC_testv.properties";
        Locale locale = buildTestLocale();
        InputStream resourceBundleStream = getClass().getClassLoader().getResourceAsStream(resourceName);

        when(repositoryService.getDeploymentResourceNames(deploymentId)).thenReturn(asList(resourceName));
        when(repositoryService.getResourceAsStream(deploymentId, resourceName)).thenReturn(resourceBundleStream);

        ResourceBundle resourceBundle = resourceBundleControl.newBundle(baseName, locale, "format", null, false);

        assertEquals(resourceBundle.getString("property1"), "value1");
        assertEquals(resourceBundle.getString("property2"), "value2");
    }

    @Test
    public void testNewBundle_PassedLangRangeWith_LangCode_Script_Region_Variant_ThereIsCloseAppropriateBundle() throws IOException {
        String baseResourceName = "test-resource";
        String resourceName = "i18n/" + baseResourceName + "_tl.properties";
        Locale locale = buildTestLocale();
        InputStream testL10nResourceAsStream = getClass().getClassLoader().getResourceAsStream(resourceName);

        when(repositoryService.getDeploymentResourceNames(deploymentId)).thenReturn(asList(resourceName));
        when(repositoryService.getResourceAsStream(deploymentId, resourceName)).thenReturn(testL10nResourceAsStream);

        ResourceBundle resourceBundle = resourceBundleControl.newBundle(baseResourceName, locale, "format", null, false);

        assertEquals(resourceBundle.getString("property1"), "value1");
        assertEquals(resourceBundle.getString("property2"), "value2");
    }

    @Test
    public void testNewBundle_PassedLangRangeWith_LangCode_Script_Region_Variant_ThereIsNoAppropriateBundle() throws IOException {
        String baseName = "test-resource";
        String resourceName = "some-resource-name.properties";
        Locale locale = buildTestLocale();

        when(repositoryService.getDeploymentResourceNames(deploymentId)).thenReturn(asList(resourceName));

        ResourceBundle resourceBundle = resourceBundleControl.newBundle(baseName, locale, "format", null, false);

        assertFalse(resourceBundle.getKeys().hasMoreElements());
    }

    @Test
    public void testNewBundle_PassedLangRangeWith_LangCode() throws IOException {
        String baseName = "test-resource";
        String resourceName = "some-resource-name_lc.properties";
        Locale locale = buildTestLocale();

        when(repositoryService.getDeploymentResourceNames(deploymentId)).thenReturn(asList(resourceName));

        ResourceBundle resourceBundle = resourceBundleControl.newBundle(baseName, locale, "format", null, false);

        assertFalse(resourceBundle.getKeys().hasMoreElements());
    }

    private Locale buildTestLocale() {
        return new Locale.Builder()
                .setLanguage("tl")
                .setRegion("TC")
                .setScript("Tscr")
                .setVariant("testv")
                .build();
    }

}

package com.artezio.bpm.resources;

import com.artezio.forms.ResourceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.servlet.ServletContext;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(CDI.class)
public class AbstractResourceLoaderTest {

    private AbstractResourceLoader resourceLoader = new AbstractResourceLoader("public") {
        @Override
        public InputStream getResource(String s) {
            return null;
        }

        @Override
        public List<String> listResourceNames() {
            return null;
        }
    };

    @Test
    public void testGetProtocol_NoProtocolDefined() {
        String actual = AbstractResourceLoader.getProtocol("noProtocolResource");

        assertEquals(AbstractResourceLoader.APP_PROTOCOL, actual);
    }

    @Test
    public void testGetProtocol_AppProtocol() {
        String actual = AbstractResourceLoader.getProtocol("embedded:app:aResource");

        assertEquals(AbstractResourceLoader.APP_PROTOCOL, actual);
    }

    @Test
    public void testGetProtocol_DeploymentProtocol() {
        String actual = AbstractResourceLoader.getProtocol("embedded:deployment:aResource");

        assertEquals(AbstractResourceLoader.DEPLOYMENT_PROTOCOL, actual);
    }

    @Test
    public void getFormPath_WithProtocolInKey() {
        String actual = resourceLoader.getResourcePath("embedded:deployment:aResource");

        assertEquals("aResource", actual);
    }

    @Test
    public void getFormPath_WithoutProtocolInKey() {
        String actual = resourceLoader.getResourcePath("aResource");

        assertEquals("aResource", actual);
    }

    @Test
    public void getResourceLoader_ResourceKeyContainsEmbeddedAppProtocol() {
        String deploymentId = "";
        String resourceKey = "embedded:app:resourceName";
        String resourceRootDirectory = "/";

        initMockCdiContext();

        ResourceLoader resourceLoader = AbstractResourceLoader.getResourceLoader(deploymentId, resourceKey, resourceRootDirectory);

        assertEquals(AppResourceLoader.class, resourceLoader.getClass());
    }

    @Test
    public void getResourceLoader_ResourceKeyContainsEmbeddedDeploymentProtocol() {
        String deploymentId = "";
        String resourceKey = "embedded:deployment:resourceName";
        String resourceRootDirectory = "/";

        initMockCdiContext();

        ResourceLoader resourceLoader = AbstractResourceLoader.getResourceLoader(deploymentId, resourceKey, resourceRootDirectory);

        assertEquals(DeploymentResourceLoader.class, resourceLoader.getClass());
    }

    @Test
    public void getResourceLoader_ResourceKeyNotContainProtocol() {
        String deploymentId = "";
        String resourceKey = "resourceName";
        String resourceRootDirectory = "/";

        PowerMockito.mockStatic(CDI.class);
        CDI cdiContext = mock(CDI.class);
        Instance<ServletContext> servletContextInstance = mock(Instance.class);
        when(CDI.current()).thenReturn(cdiContext);
        when(cdiContext.select(ServletContext.class)).thenReturn(servletContextInstance);
        when(servletContextInstance.get()).thenReturn(null);

        ResourceLoader resourceLoader = AbstractResourceLoader.getResourceLoader(deploymentId, resourceKey, resourceRootDirectory);

        assertEquals(AppResourceLoader.class, resourceLoader.getClass());
    }

    private void initMockCdiContext() {
        PowerMockito.mockStatic(CDI.class);
        CDI cdiContext = mock(CDI.class);
        Instance<ServletContext> servletContextInstance = mock(Instance.class);
        when(CDI.current()).thenReturn(cdiContext);
        when(cdiContext.select(ServletContext.class)).thenReturn(servletContextInstance);
        when(servletContextInstance.get()).thenReturn(null);
    }

}

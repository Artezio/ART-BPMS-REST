package com.artezio.bpm.resources;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(CDI.class)
public class AppResourceLoaderTest {

    private static final String PUBLIC_RESOURCES_DIRECTORY = "public";

    private ServletContext servletContext;
    private AppResourceLoader loader;

    @Before
    public void init() {
        PowerMockito.mockStatic(CDI.class);
        CDI cdiContext = mock(CDI.class);
        Instance<ServletContext> servletContextInstance = mock(Instance.class);
        servletContext = mock(ServletContext.class);
        when(CDI.current()).thenReturn(cdiContext);
        when(cdiContext.select(ServletContext.class)).thenReturn(servletContextInstance);
        when(servletContextInstance.get()).thenReturn(servletContext);

        loader = new AppResourceLoader(PUBLIC_RESOURCES_DIRECTORY);
    }

    @Test
    public void testListResourceNames() throws MalformedURLException {
        when(servletContext.getResource("/" + PUBLIC_RESOURCES_DIRECTORY))
                .thenReturn(Thread.currentThread().getContextClassLoader().getResource(PUBLIC_RESOURCES_DIRECTORY));

        List<String> actuals = loader.listResourceNames();

        assertTrue(actuals.contains(PUBLIC_RESOURCES_DIRECTORY + "/component.js"));
    }

    @Test
    public void testGetResource() throws IOException {
        String resourcesKey = "component.js";

        when(servletContext.getResourceAsStream(PUBLIC_RESOURCES_DIRECTORY + "/" + resourcesKey))
                .thenReturn(Thread.currentThread().getContextClassLoader().getResourceAsStream(PUBLIC_RESOURCES_DIRECTORY + "/" + resourcesKey));

        InputStream actual = loader.getResource(resourcesKey);

        assertNotNull(actual);
        assertTrue(actual.available() > 0);
    }

    @Test
    public void testListResourceNames_IfResourcePathNotExists() throws MalformedURLException {
        String resourcesPath = "non-existent-path";
        ResourceLoader resourceLoader = new AppResourceLoader(resourcesPath);

        when(servletContext.getResource(anyString())).thenReturn(null);

        List<String> actuals = resourceLoader.listResourceNames();

        assertTrue(actuals.isEmpty());
    }

}

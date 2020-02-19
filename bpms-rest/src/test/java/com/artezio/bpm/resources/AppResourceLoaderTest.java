package com.artezio.bpm.resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class AppResourceLoaderTest {

    @InjectMocks
    private AppResourceLoader loader = new AppResourceLoader();

    @Mock
    private ServletContext servletContext;

    @Test
    public void testListResourceNames() throws MalformedURLException {
        String resourcesPath = "bpm-resources";

        when(servletContext.getResource(any()))
                .thenReturn(Thread.currentThread().getContextClassLoader().getResource(resourcesPath));

        List<String> actuals = loader.listResourceNames(resourcesPath);

        assertTrue(actuals.contains("bpm-resources/component.js"));
    }
    
    @Test
    public void testGetResource() throws IOException {
        String resourcesKey = "bpm-resources/component.js";
        when(servletContext.getResourceAsStream(resourcesKey))
                .thenReturn(Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcesKey));

        InputStream actual = loader.getResource(resourcesKey);

        assertNotNull(actual);
        assertTrue(actual.available() > 0);
    }

    @Test
    public void testListResourceNames_IfResourcePathNotExists() throws MalformedURLException {
        String resourcesPath = "non-existent-path";
        when(servletContext.getResource(any()))
                .thenReturn(null);

        List<String> actuals = loader.listResourceNames(resourcesPath);

        assertTrue(actuals.isEmpty());
    }
    
    
    
}

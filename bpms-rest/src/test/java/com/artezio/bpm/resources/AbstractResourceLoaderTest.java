package com.artezio.bpm.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class AbstractResourceLoaderTest {

    private AbstractResourceLoader resourceLoader = new AbstractResourceLoader() {
        @Override
        public InputStream getResource(String s) {
            return null;
        }

        @Override
        public List<String> listResourceNames(String s) {
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

}

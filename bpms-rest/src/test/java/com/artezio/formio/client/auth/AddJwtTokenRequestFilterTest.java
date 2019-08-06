package com.artezio.formio.client.auth;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.specimpl.MultivaluedTreeMap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

@RunWith(MockitoJUnitRunner.class)
public class AddJwtTokenRequestFilterTest {

    private static ResteasyClient resteasyClient = mock(ResteasyClient.class);

    @Mock
    private FormioAuthApi formioAuthApiProxy;
    @Mock
    private ResteasyWebTarget restEasyWebTarget;
    @InjectMocks
    private AddJwtTokenRequestFilter filter = new AddJwtTokenRequestFilter();

    @BeforeClass
    public static void initClass() throws NoSuchFieldException {
        Field restEasyClientField = AddJwtTokenRequestFilter.class.getDeclaredField("client");
        setField(AddJwtTokenRequestFilter.class, restEasyClientField, resteasyClient);
    }

    @Test
    public void testFilter() {
        String expectedToken = "expected-jwt-token";
        ClientRequestContext mockContext = mock(ClientRequestContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedTreeMap<>();
        when(mockContext.getHeaders()).thenReturn(headers);
        when(resteasyClient.target(any(UriBuilder.class))).thenReturn(restEasyWebTarget);
        when(restEasyWebTarget.proxy(FormioAuthApi.class)).thenReturn(formioAuthApiProxy);
        Response loginResponse = mock(Response.class);
        when(formioAuthApiProxy.login(any())).thenReturn(loginResponse);
        when(loginResponse.getHeaderString("x-jwt-token")).thenReturn(expectedToken);

        filter.filter(mockContext);

        assertFalse(headers.isEmpty());
        List<Object> actualTokenHeaders = headers.get("x-jwt-token");
        assertEquals(1, actualTokenHeaders.size());
        Object actualToken = actualTokenHeaders.get(0);
        assertEquals(expectedToken, actualToken);
    }

}

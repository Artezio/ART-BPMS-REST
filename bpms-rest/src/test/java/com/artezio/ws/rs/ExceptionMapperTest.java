package com.artezio.ws.rs;

import static org.junit.Assert.assertEquals;

import javax.ws.rs.core.Response.Status;

import org.junit.Test;

import com.artezio.bpm.services.exceptions.NotAuthorizedException;

public class ExceptionMapperTest {

    @Test
    public void testFindResponseStatusForEnlistedException() {
        Throwable exception = new NotAuthorizedException();

        Status actual = ExceptionMapper.findResponseStatus(exception);

        assertEquals(Status.FORBIDDEN, actual);
    }

    @Test
    public void testFindResponseStatusFoUnlistedException() {
        Throwable exception = new RuntimeException();

        Status actual = ExceptionMapper.findResponseStatus(exception);

        assertEquals(Status.INTERNAL_SERVER_ERROR, actual);
    }

}

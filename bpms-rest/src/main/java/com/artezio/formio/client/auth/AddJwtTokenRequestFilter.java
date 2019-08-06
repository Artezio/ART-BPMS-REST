package com.artezio.formio.client.auth;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.UriBuilder;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class AddJwtTokenRequestFilter implements ClientRequestFilter {
    private final static String FORMIO_SERVER_LOGIN = System.getProperty("FORMIO_LOGIN", "root@root.root");
    private final static String FORMIO_SERVER_PASSWORD = System.getProperty("FORMIO_PASSWORD", "root");
    private final static String FORMIO_SERVER_PATH = System.getProperty("FORMIO_URL", "http://localhost:3001");
    private final static Integer FORMIO_JWT_EXPIRATION_TIME_SECONDS = Integer.parseInt(System.getProperty("FORMIO_JWT_EXPIRATION_TIME_SECONDS", "240"));

    private static ResteasyClient client = new ResteasyClientBuilder()
            .connectionPoolSize(10)
            .build();

    private static volatile String token = null;
    private static volatile LocalDateTime expiration = null;

    @Override
    public void filter(ClientRequestContext requestContext) {
        String token = getJwtToken();
        requestContext.getHeaders().add("x-jwt-token", token);
    }

    private static synchronized String getJwtToken() {
        if (token != null && expiration.isAfter(LocalDateTime.now())) {
            return token;
        }

        token = client.target(UriBuilder.fromPath(FORMIO_SERVER_PATH))
                .proxy(FormioAuthApi.class)
                .login(new Credentials(FORMIO_SERVER_LOGIN, FORMIO_SERVER_PASSWORD))
                .getHeaderString("x-jwt-token");
        expiration = LocalDateTime.now()
                .plus(FORMIO_JWT_EXPIRATION_TIME_SECONDS, ChronoUnit.SECONDS)
                .minus(10, ChronoUnit.SECONDS);
        return token;
    }
}

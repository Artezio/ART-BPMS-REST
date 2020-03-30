package com.artezio.bpm.services;

import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;

import javax.annotation.Resource;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Named
@Stateless
public class IdentitySvc {

    @Inject
    private Principal loggedUser;
    @Resource
    private SessionContext sessionContext;

    public List<String> userGroups() {
        Set<String> result = getKeycloakSecurityContext().getToken().getRealmAccess().getRoles();
        return ! result.isEmpty() 
                ? new ArrayList<>(result)
                : Arrays.asList("");
    }

    public String userId() {
        return loggedUser.getName();
    }

    public String userEmail() {
        return getKeycloakSecurityContext().getToken().getEmail();
    }

    private KeycloakSecurityContext getKeycloakSecurityContext() {
        KeycloakPrincipal<?> keycloakPrincipal = (KeycloakPrincipal<?>) (sessionContext.getCallerPrincipal());
        return keycloakPrincipal.getKeycloakSecurityContext();
    }

}

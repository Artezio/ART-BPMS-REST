package com.artezio.bpm.services;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;

@Stateless
public class IdentitySvc {

    @Inject
    private Principal loggedUser;
    @Resource
    private SessionContext sessionContext;

    public List<String> userGroups() {
        Set<String> result = getKeycloakSecurityContext().getToken().getRealmAccess().getRoles();
        return new ArrayList<>(result);
    }

    public String userId() {
        return loggedUser.getName();
    }

    private KeycloakSecurityContext getKeycloakSecurityContext() {
        KeycloakPrincipal<?> keycloakPrincipal = (KeycloakPrincipal<?>) (sessionContext.getCallerPrincipal());
        return keycloakPrincipal.getKeycloakSecurityContext();
    }

}

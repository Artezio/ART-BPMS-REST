package com.artezio.bpm.services;

import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
@RequestScope
public class IdentitySvc {

    private final Principal loggedUser;

    public IdentitySvc() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        this.loggedUser = (Principal) authentication.getPrincipal();
    }

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
        KeycloakPrincipal<?> keycloakPrincipal = (KeycloakPrincipal<?>) loggedUser;
        return keycloakPrincipal.getKeycloakSecurityContext();
    }

}

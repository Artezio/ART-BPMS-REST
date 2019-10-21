package com.artezio.bpm.services.exceptions;

public class NoDeploymentException extends RuntimeException {
    public NoDeploymentException() {
        super("There are no deployments.");
    }
}

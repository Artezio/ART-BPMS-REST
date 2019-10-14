package com.artezio.bpm.services;

public class ThereAreNoDeploymentsException extends RuntimeException {
    public ThereAreNoDeploymentsException() {
        super("There are no deployments.");
    }
}

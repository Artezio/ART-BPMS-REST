package com.artezio.bpm.services.exceptions;

public class NotAuthorizedException extends RuntimeException {

    public NotAuthorizedException() {
	super("Not authorized.");
    }

    private static final long serialVersionUID = 3714162592072411273L;

}

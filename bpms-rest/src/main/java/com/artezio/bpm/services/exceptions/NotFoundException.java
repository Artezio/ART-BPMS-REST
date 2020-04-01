package com.artezio.bpm.services.exceptions;

public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Not found.");
    }

    public NotFoundException(String message) {
        super(message);
    }

    private static final long serialVersionUID = 5905410725045087188L;

}

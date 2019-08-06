package com.artezio.bpm.rest.exception;

import javax.ws.rs.core.Response.Status;

public class InvalidRequestException extends RestException {

  private static final long serialVersionUID = 1L;
  
  public InvalidRequestException(Status status, String message) {
    super(status, message);
  }
  
  public InvalidRequestException(Status status, Exception cause, String message) {
    super(status, cause, message);
  }
}
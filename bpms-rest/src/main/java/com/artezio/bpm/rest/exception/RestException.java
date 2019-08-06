package com.artezio.bpm.rest.exception;

import javax.ws.rs.core.Response.Status;

public class RestException extends RuntimeException {
  
  private static final long serialVersionUID = 1L;
  
  private Status status;
  
  public RestException(String message) {
    super(message);
  }
  
  public RestException(Status status, String message) {
    super(message);
    this.status = status;
  }
  
  public RestException(Status status, Exception cause) {
    super(cause);
    this.status = status;
  }
  
  public RestException(Status status, Exception cause, String message) {
    super(message, cause);
    this.status = status;
  }

  public Status getStatus() {
    return status;
  }
}
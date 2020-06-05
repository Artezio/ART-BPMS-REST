package com.artezio.bpm.rest.exception;

import org.springframework.http.HttpStatus;

public class RestException extends RuntimeException {
  
  private static final long serialVersionUID = 1L;
  
  private HttpStatus status;
  
  public RestException(String message) {
    super(message);
  }
  
  public RestException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }
  
  public RestException(HttpStatus status, Exception cause) {
    super(cause);
    this.status = status;
  }
  
  public RestException(HttpStatus status, Exception cause, String message) {
    super(message, cause);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
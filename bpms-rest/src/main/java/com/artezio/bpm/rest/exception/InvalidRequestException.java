package com.artezio.bpm.rest.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends RestException {

  private static final long serialVersionUID = 1L;
  
  public InvalidRequestException(HttpStatus status, String message) {
    super(status, message);
  }
  
  public InvalidRequestException(HttpStatus status, Exception cause, String message) {
    super(status, cause, message);
  }
}
package com.researchspace.integrations.jove.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class JoveTokenException extends RuntimeException {

  public JoveTokenException(String message) {
    super(message);
  }

  public JoveTokenException(String message, Exception cause) {
    super(message, cause);
  }

  public JoveTokenException(Exception ex) {
    super(ex);
  }
}

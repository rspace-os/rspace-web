package com.researchspace.api.v1.controller;

/** Exception indicating that RSpace is unable to authorize user's action in 3rd-party API */
public class ExternalApiAuthorizationException extends RuntimeException {

  public ExternalApiAuthorizationException(String message) {
    super(message);
  }
}

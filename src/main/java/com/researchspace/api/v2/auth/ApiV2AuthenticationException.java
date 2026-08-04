package com.researchspace.api.v2.auth;

/** Indicates that REST API v2 credentials are missing or invalid. */
public class ApiV2AuthenticationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ApiV2AuthenticationException() {}
}

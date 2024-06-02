package com.researchspace.api.v1.auth;

/** Implementation-agnostic authorisation exception */
public class ApiAuthenticationException extends RuntimeException {

  /** */
  private static final long serialVersionUID = 1L;

  public ApiAuthenticationException(String msg) {
    super(msg);
  }
}

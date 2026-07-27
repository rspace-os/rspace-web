package com.researchspace.api.v1.auth;

import lombok.Getter;

/** Implementation-agnostic authorisation exception */
@Getter
public class ApiAuthenticationException extends RuntimeException {

  /** */
  private static final long serialVersionUID = 1L;

  private final String messageKey;
  private final Object[] args;

  public ApiAuthenticationException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}

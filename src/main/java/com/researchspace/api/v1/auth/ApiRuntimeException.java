package com.researchspace.api.v1.auth;

import lombok.Getter;

/** API exception with error code that will be converted to ApiError with translated message. */
@Getter
public class ApiRuntimeException extends RuntimeException {

  private static final long serialVersionUID = 4298235353335075650L;

  private String errorCode;
  private Object[] args;

  public ApiRuntimeException(String errorCode, Object... args) {
    super(errorCode);
    this.errorCode = errorCode;
    this.args = args;
  }
}

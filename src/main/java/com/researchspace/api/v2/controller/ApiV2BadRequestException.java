package com.researchspace.api.v2.controller;

import lombok.Getter;

/** API request error caused by invalid collection query syntax or values. */
@Getter
public class ApiV2BadRequestException extends RuntimeException {

  private static final long serialVersionUID = -3930578979900763094L;
  private final String errorCode;
  private final Object[] args;

  public ApiV2BadRequestException(String errorCode, Object... args) {
    super(errorCode);
    this.errorCode = errorCode;
    this.args = args;
  }
}

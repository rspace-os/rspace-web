package com.researchspace.api.v1.auth;

import com.researchspace.model.field.LocalizedException;
import java.util.function.BiFunction;
import lombok.Getter;

/** API exception with error code that will be converted to ApiError with translated message. */
@Getter
public class ApiRuntimeException extends RuntimeException implements LocalizedException {

  private static final long serialVersionUID = 4298235353335075650L;

  private String errorCode;

  private Object[] args;

  public ApiRuntimeException(String errorCode, Object... args) {
    super(errorCode);
    this.errorCode = errorCode;
    this.args = args;
  }

  @Override
  public String resolve(BiFunction<String, Object[], String> resolver) {
    return resolver.apply(errorCode, args);
  }
}

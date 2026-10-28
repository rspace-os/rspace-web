package com.researchspace.api.v1.auth;

import com.researchspace.model.field.LocalizedException;
import java.util.function.BiFunction;
import lombok.Getter;

/** Implementation-agnostic authorisation exception */
@Getter
public class ApiAuthenticationException extends RuntimeException implements LocalizedException {

  /** */
  private static final long serialVersionUID = 1L;

  private final String messageKey;

  private final Object[] args;

  public ApiAuthenticationException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }

  @Override
  public String resolve(BiFunction<String, Object[], String> resolver) {
    return resolver.apply(messageKey, args);
  }
}

package com.researchspace.model.field;

import java.util.function.BiFunction;

/** Null argument whose user-facing text is resolved at an application boundary. */
public class LocalizedNullPointerException extends NullPointerException
    implements LocalizedException {

  private final String code;
  private final Object[] arguments;

  public LocalizedNullPointerException(String code, Object... arguments) {
    super(code);
    this.code = code;
    this.arguments = arguments;
  }

  @Override
  public String resolve(BiFunction<String, Object[], String> resolver) {
    return resolver.apply(code, arguments);
  }
}

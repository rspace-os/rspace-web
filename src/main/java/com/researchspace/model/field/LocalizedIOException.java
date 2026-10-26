package com.researchspace.model.field;

import java.io.IOException;
import java.util.function.BiFunction;

/** I/O failure whose user-facing text is resolved at an application boundary. */
public class LocalizedIOException extends IOException implements LocalizedException {

  private final String code;
  private final Object[] arguments;

  public LocalizedIOException(String code, Object... arguments) {
    super(code);
    this.code = code;
    this.arguments = arguments;
  }

  @Override
  public String resolve(BiFunction<String, Object[], String> resolver) {
    return resolver.apply(code, arguments);
  }
}

package com.researchspace.model.record;

import com.researchspace.model.field.LocalizedException;
import java.util.function.BiFunction;

/** Illegal add-child operation whose user-facing message is resolved at an application boundary. */
public class LocalizedIllegalAddChildOperation extends IllegalAddChildOperation
    implements LocalizedException {

  private static final long serialVersionUID = 1L;

  private final String code;
  private final Object[] arguments;

  public LocalizedIllegalAddChildOperation(String code, Object... arguments) {
    super(code);
    this.code = code;
    this.arguments = arguments;
  }

  @Override
  public String resolve(BiFunction<String, Object[], String> resolver) {
    return resolver.apply(code, arguments);
  }
}

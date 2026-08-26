package com.researchspace.model.field;

import java.util.Arrays;
import java.util.function.BiFunction;

/** Illegal argument whose user-facing text is resolved at an application boundary. */
public class LocalizedIllegalArgumentException extends IllegalArgumentException {

  private static final long serialVersionUID = -6265721901924491487L;

  private final String code;
  private final Object[] arguments;
  private final ErrorList nestedErrors;

  public LocalizedIllegalArgumentException(String code, Object... arguments) {
    this(code, (ErrorList) null, arguments);
  }

  public LocalizedIllegalArgumentException(
      String code, ErrorList nestedErrors, Object... arguments) {
    super(code);
    this.code = code;
    this.arguments = arguments;
    this.nestedErrors = nestedErrors;
  }

  /** Resolves this exception and any model validation errors nested inside it. */
  public String resolve(BiFunction<String, Object[], String> resolver) {
    Object[] resolvedArguments = arguments;
    if (nestedErrors != null) {
      resolvedArguments = Arrays.copyOf(arguments, arguments.length + 1);
      resolvedArguments[arguments.length] = nestedErrors.resolveMessagesAndJoin(resolver, ", ");
    }
    return resolver.apply(code, resolvedArguments);
  }
}

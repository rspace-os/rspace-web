package com.researchspace.model.field;

import org.springframework.context.MessageSourceResolvable;

/** Unsupported operation whose user-facing text is resolved at an application boundary. */
public class LocalizedUnsupportedOperationException extends UnsupportedOperationException
    implements MessageSourceResolvable {

  private static final long serialVersionUID = -8883077312372394270L;

  private final String code;
  private final Object[] arguments;

  public LocalizedUnsupportedOperationException(String code, Object... arguments) {
    super(code);
    this.code = code;
    this.arguments = arguments;
  }

  @Override
  public String[] getCodes() {
    return new String[] {code};
  }

  @Override
  public Object[] getArguments() {
    return arguments;
  }

  @Override
  public String getDefaultMessage() {
    return null;
  }
}

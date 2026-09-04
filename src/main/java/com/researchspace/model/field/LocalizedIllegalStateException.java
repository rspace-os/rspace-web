package com.researchspace.model.field;

import org.springframework.context.MessageSourceResolvable;

/** Illegal state whose user-facing text is resolved at an application boundary. */
public class LocalizedIllegalStateException extends IllegalStateException
    implements MessageSourceResolvable {

  private static final long serialVersionUID = -7880961943929307680L;

  private final String code;
  private final Object[] arguments;

  public LocalizedIllegalStateException(String code, Object... arguments) {
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

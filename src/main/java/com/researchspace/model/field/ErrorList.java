package com.researchspace.model.field;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.apache.commons.lang3.StringUtils;

/** Holds validation errors to return to client/browser in a Spring MVC-independent manner. */
public class ErrorList implements Serializable {
  private static final long serialVersionUID = 1658698239631269737L;
  private final List<String> errorMessages = new ArrayList<>();
  private final List<MessageCode> messageCodes = new ArrayList<>();

  private record MessageCode(String code, Object[] arguments) implements Serializable {}

  /** Create an ErrorList of error messages */
  public static ErrorList of(String... messages) {
    ErrorList el = new ErrorList();

    for (String message : messages) {
      el.addErrorMsg(message);
    }

    return el;
  }

  /**
   * Convenience constructor for creating an ErrorList that is to hold only a single element.
   *
   * @return A new {@link ErrorList} object populated with the message.
   * @deprecated replaced by a more concise version - {@link #of(String...)}}
   */
  @Deprecated
  public static ErrorList createErrListWithSingleMsg(String msg) {
    ErrorList el = new ErrorList();
    el.addErrorMsg(msg);
    return el;
  }

  public void addErrorMsg(String msg) {
    errorMessages.add(msg);
  }

  /** Adds a message code for resolution at the controller or service boundary. */
  public void addErrorMsgCode(String code, Object... arguments) {
    messageCodes.add(new MessageCode(code, arguments));
  }

  /** Resolves all message codes, retaining any already-resolved legacy messages. */
  public void resolveMessageCodes(BiFunction<String, Object[], String> resolver) {
    messageCodes.forEach(code -> errorMessages.add(resolver.apply(code.code(), code.arguments())));
    messageCodes.clear();
  }

  /** Resolves all message codes and joins them without changing this error list. */
  public String resolveMessagesAndJoin(
      BiFunction<String, Object[], String> resolver, String delimiter) {
    List<String> resolved = new ArrayList<>(errorMessages);
    messageCodes.forEach(code -> resolved.add(resolver.apply(code.code(), code.arguments())));
    return StringUtils.join(resolved, delimiter);
  }

  /** Returns a snapshot containing resolved messages and unresolved message codes. */
  public List<String> getErrorMessages() {
    List<String> result = new ArrayList<>(errorMessages);
    messageCodes.stream().map(MessageCode::code).forEach(result::add);
    return result;
  }

  public String getAllErrorMessagesAsStringsSeparatedBy(String delimiter) {
    return StringUtils.join(getErrorMessages(), delimiter);
  }

  public String toString() {
    return getErrorMessages() + " has " + getErrorMessages().size() + " messages.";
  }

  public boolean hasErrorMessages() {
    return !errorMessages.isEmpty() || !messageCodes.isEmpty();
  }

  /**
   * Merges the argument's messages with this object. The argument is unaltered.
   *
   * @param el An {@link ErrorList} to merge with this object's error messages.
   */
  public void addErrorList(final ErrorList el) {
    errorMessages.addAll(el.errorMessages);
    messageCodes.addAll(el.messageCodes);
  }
}

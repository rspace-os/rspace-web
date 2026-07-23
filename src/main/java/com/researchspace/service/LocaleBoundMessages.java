package com.researchspace.service;

import java.util.List;
import java.util.Locale;

/** Binds {@link MessageSourceUtils} to a fixed locale for Velocity templates. */
public class LocaleBoundMessages {

  private final MessageSourceUtils messages;
  private final Locale locale;

  public LocaleBoundMessages(MessageSourceUtils messages, Locale locale) {
    this.messages = messages;
    this.locale = locale;
  }

  public String format(String key, List<Object> args) {
    return messages.format(key, args, locale);
  }

  public String getMessage(String key) {
    return messages.getMessageForLocale(key, locale);
  }

  public String getMessage(String key, Object[] args) {
    return messages.getMessage(key, args, locale);
  }

  /** Returns the locale as a BCP 47 language tag for rendered HTML. */
  public String getLanguageTag() {
    return locale.toLanguageTag();
  }
}

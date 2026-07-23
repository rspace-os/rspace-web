package com.researchspace.service;

import java.util.List;
import java.util.Locale;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

/** Accesses messages using either the current request locale or an explicit locale. */
@NoArgsConstructor
@Component("messageSourceUtils")
public class MessageSourceUtils {

  private MessageSourceAccessor messages;
  private MessageSource messageSource;

  @Autowired
  public void setMessageSource(MessageSource messageSource) {
    this.messageSource = messageSource;
    this.messages = new MessageSourceAccessor(messageSource);
  }

  public String getMessage(String code) {
    return messages.getMessage(code);
  }

  public String getMessage(MessageSourceResolvable resolvable) {
    return messages.getMessage(resolvable);
  }

  public String getMessage(String key, Object[] args) {
    return messages.getMessage(key, args);
  }

  /** Resolves a message without arguments against an explicit locale. */
  public String getMessageForLocale(String key, Locale locale) {
    return messageSource.getMessage(key, null, locale);
  }

  /** Resolves a message against an explicit locale. */
  public String getMessage(String key, Object[] args, Locale locale) {
    return messageSource.getMessage(key, args, locale);
  }

  /** Resolves a {@link MessageSourceResolvable} against an explicit locale. */
  public String getMessage(MessageSourceResolvable resolvable, Locale locale) {
    return messageSource.getMessage(resolvable, locale);
  }

  /** Accepts Velocity array literals, which are passed as {@link List Lists}. */
  public String format(String key, List<Object> args) {
    return messages.getMessage(key, args == null ? null : args.toArray());
  }

  /** Locale-explicit variant of {@link #format(String, List)}. */
  public String format(String key, List<Object> args, Locale locale) {
    return messageSource.getMessage(key, args == null ? null : args.toArray(), locale);
  }

  /**
   * Convenience method to return a standard message to the API/UI, if an id or resource could not
   * be retrieved from the database. Created due to RSPAC-390
   *
   * @param resourceType Any String such as 'Group', 'User' etc
   * @param id The identifier whose object could not be retrieved
   * @return I18 text for display in UI
   */
  public String getResourceNotFoundMessage(String resourceType, Long id) {
    return getResourceNotFoundMessage(resourceType, id + "");
  }

  public String getResourceNotFoundMessage(String resourceType, String id) {
    return getMessage("errors.resource.inaccessible", new String[] {resourceType, id});
  }

  public MessageSourceUtils(MessageSource messageSource) {
    setMessageSource(messageSource);
  }
}

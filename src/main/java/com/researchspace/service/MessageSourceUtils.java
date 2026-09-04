package com.researchspace.service;

import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
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

  @Autowired
  public void setMessageSource(MessageSource messageSource) {
    this.messages = new MessageSourceAccessor(messageSource);
  }

  public String getMessage(String code) {
    return messages.getMessage(code);
  }

  public String getMessage(MessageSourceResolvable resolvable) {
    return messages.getMessage(resolvable);
  }

  public String getMessage(LocalizedIllegalArgumentException exception) {
    return exception.resolve(this::getMessage);
  }

  /** Resolves coded model exceptions and leaves ordinary exception messages unchanged. */
  public String getExceptionMessage(Throwable exception) {
    if (exception instanceof LocalizedIllegalArgumentException localizedException) {
      return getMessage(localizedException);
    }
    if (exception instanceof MessageSourceResolvable resolvable) {
      return getMessage(resolvable);
    }
    return exception.getMessage();
  }

  public String getMessage(String key, Object[] args) {
    return messages.getMessage(key, args);
  }

  public String getMessageForLocale(String key, Locale locale) {
    return messages.getMessage(key, locale);
  }

  public String getMessage(String key, Object[] args, Locale locale) {
    return messages.getMessage(key, args, locale);
  }

  public String getMessage(MessageSourceResolvable resolvable, Locale locale) {
    return messages.getMessage(resolvable, locale);
  }

  /** Resolves message codes held by model validation errors. */
  public ErrorList resolve(ErrorList errors) {
    errors.resolveMessageCodes(this::getMessage);
    return errors;
  }

  /** Accepts Velocity array literals, which are passed as {@link List Lists}. */
  public String format(String key, List<?> args) {
    return messages.getMessage(key, args == null ? null : args.toArray());
  }

  public String format(String key, List<?> args, Locale locale) {
    return messages.getMessage(key, args == null ? null : args.toArray(), locale);
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

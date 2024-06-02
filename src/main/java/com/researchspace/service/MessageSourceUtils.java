package com.researchspace.service;

import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

/**
 * Class that unifies access to message resources in RSpace. Returns messages based on default
 * locale.
 */
@NoArgsConstructor
@Component("messageSourceUtils")
public class MessageSourceUtils {

  private MessageSourceAccessor messages;

  @Autowired
  public void setMessageSource(MessageSource messageSource) {
    messages = new MessageSourceAccessor(messageSource);
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
    return getMessage("record.inaccessible", new String[] {resourceType, id});
  }

  /*
   * for testing
   */
  public MessageSourceUtils(MessageSource messageSource) {
    setMessageSource(messageSource);
  }
}

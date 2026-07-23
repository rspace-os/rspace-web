package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.researchspace.model.comms.NotificationType;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class NotificationTypeMessagesTest {

  private final MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
  private final Locale enUS = Locale.forLanguageTag("en-US");

  @Test
  void everyNotificationTypeResolvesToARealMessage() {
    for (NotificationType type : NotificationType.values()) {
      String key = NotificationTypeMessages.keyFor(type);
      String message = messages.getMessageForLocale(key, enUS);
      assertFalse(message.startsWith("notificationType."), "key leaked as literal text: " + key);
    }
  }
}

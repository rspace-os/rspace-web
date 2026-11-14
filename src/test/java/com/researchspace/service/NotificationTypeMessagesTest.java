package com.researchspace.service;

import static org.assertj.core.api.Assertions.assertThat;

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
      assertThat(message)
          .as("key leaked as literal text: " + key)
          .doesNotStartWith("notificationType.");
    }
  }
}

package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.comms.CommunicationStatus;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.NoSuchMessageException;

class CommunicationStatusMessagesTest {

  private final MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
  private final Locale enUS = Locale.forLanguageTag("en-US");

  @Test
  void everyCommunicationStatusResolvesToARealMessage() {
    for (CommunicationStatus status : CommunicationStatus.values()) {
      String key = CommunicationStatusMessages.keyFor(status);
      String message = messages.getMessageForLocale(key, enUS);
      assertFalse(message.isBlank(), "blank message for key: " + key);
    }
  }

  /** The assertion above only means anything because an absent key throws rather than echoes. */
  @Test
  void unresolvedKeyThrowsRatherThanEchoingTheKey() {
    assertThrows(
        NoSuchMessageException.class,
        () -> messages.getMessageForLocale("messages.status.noSuchStatus", enUS));
  }
}

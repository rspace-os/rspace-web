package com.researchspace.model.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

public class ElnFieldRulesTest {

  @Test
  public void unreadableStoredRuleIsLoggedOnceWithoutItsContent() {
    StringFieldForm unknownProperty = fieldWithRule(-9_001L, "{\"leakedPropertyName\":\"x\"}");
    StringFieldForm badToken = fieldWithRule(-9_002L, "{\"sourceFieldKey\":leakedStoredToken}");

    List<LogEvent> events = new ArrayList<>();
    AbstractAppender capture =
        new AbstractAppender("elnFieldRulesCapture", null, null, true, Property.EMPTY_ARRAY) {
          @Override
          public void append(LogEvent event) {
            events.add(event.toImmutable());
          }
        };
    capture.start();
    Logger logger = (Logger) LogManager.getLogger(ElnFieldRules.class);
    Level levelBefore = logger.getLevel();
    logger.addAppender(capture);
    logger.setLevel(Level.DEBUG);
    try {
      assertNull(ElnFieldRules.storedRule(unknownProperty));
      assertNull(ElnFieldRules.storedRule(unknownProperty));
      assertNull(ElnFieldRules.storedRule(badToken));
    } finally {
      logger.removeAppender(capture);
      logger.setLevel(levelBefore);
      capture.stop();
    }

    assertThat(events)
        .extracting(LogEvent::getLevel)
        .containsExactly(Level.WARN, Level.DEBUG, Level.WARN);
    for (LogEvent event : events) {
      assertNull(event.getThrown());
      assertThat(event.getMessage().getFormattedMessage())
          .contains("form field -900")
          .doesNotContain("leaked");
    }
  }

  private static StringFieldForm fieldWithRule(Long id, String rule) {
    StringFieldForm field = new StringFieldForm("Permit");
    field.setId(id);
    field.setDisplayRule(rule);
    return field;
  }
}

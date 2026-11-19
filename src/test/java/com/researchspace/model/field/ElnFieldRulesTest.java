package com.researchspace.model.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.field.rule.ConditionOperator;
import com.researchspace.model.field.rule.FieldDisplayRule;
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
  public void signingReadsTheValueOfANumberSource() {
    NumberFieldForm weight = new NumberFieldForm("Weight");
    weight.setFieldKey("6f1c2e0a-0000-4000-8000-000000000001");
    StringFieldForm reason = new StringFieldForm("Reason");
    reason.setMandatory(true);
    reason.setDisplayRule(
        new FieldDisplayRule(weight.getFieldKey(), ConditionOperator.GREATER_THAN, List.of("2"))
            .toJson());
    NumberField weightField = new NumberField(weight);
    List<Field> fields = List.of(weightField, new StringField(reason));

    weightField.setFieldData("1.5");
    assertTrue(ElnFieldRules.everyShownMandatoryFieldHasData(fields));
    weightField.setFieldData("4");
    assertFalse(ElnFieldRules.everyShownMandatoryFieldHasData(fields));
  }

  @Test
  public void signingReadsTheValueOfATextSource() {
    StringFieldForm hazard = new StringFieldForm("Hazard");
    hazard.setFieldKey("6f1c2e0a-0000-4000-8000-000000000002");
    StringFieldForm precautions = new StringFieldForm("Precautions");
    precautions.setMandatory(true);
    precautions.setDisplayRule(
        new FieldDisplayRule(hazard.getFieldKey(), ConditionOperator.CONTAINS, List.of("toxic"))
            .toJson());
    StringField hazardField = new StringField(hazard);
    List<Field> fields = List.of(hazardField, new StringField(precautions));

    hazardField.setFieldData("Flammable");
    assertTrue(ElnFieldRules.everyShownMandatoryFieldHasData(fields));
    hazardField.setFieldData("Highly Toxic");
    assertFalse(ElnFieldRules.everyShownMandatoryFieldHasData(fields));
  }

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

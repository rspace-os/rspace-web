package com.researchspace.model.field.rule;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.service.JsonMessageSource;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every code {@link FieldDisplayRuleValidator} returns must resolve to real text that names the
 * field. A missing catalogue entry would not fail loudly: the message source falls back to showing
 * the bare code.
 */
class FieldDisplayRuleMessageKeysTest {

  private static final JsonMessageSource MESSAGES = new JsonMessageSource();
  private static final Locale EN_US = Locale.forLanguageTag("en-US");
  private static final String FIELD = "Zz-Field";

  @ParameterizedTest
  @ValueSource(
      strings = {
        FieldDisplayRuleValidator.KEY_INVALID,
        FieldDisplayRuleValidator.KEY_DUPLICATE,
        FieldDisplayRuleValidator.SOURCE_MISSING,
        FieldDisplayRuleValidator.SOURCE_SELF,
        FieldDisplayRuleValidator.SOURCE_TYPE,
        FieldDisplayRuleValidator.VALUE_UNKNOWN,
        FieldDisplayRuleValidator.CYCLE
      })
  void everyCodeResolvesToTextNamingTheField(String code) {
    // an unknown code throws NoSuchMessageException here
    String message = MESSAGES.getMessage(code, new Object[] {FIELD}, EN_US);

    assertTrue(message.contains(FIELD), code + " must name the field, but said: " + message);
  }
}

package com.researchspace.model.inventory.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

class ExtraNumberFieldTest {

  private final MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());

  @Test
  void resolvesNumericErrorsFromTheJsonCatalogueForTheCurrentLocale() {
    var previous = LocaleContextHolder.getLocaleContext();
    try {
      LocaleContextHolder.setLocale(Locale.forLanguageTag("zz-ZZ"));
      IllegalArgumentException invalid =
          assertThrows(
              IllegalArgumentException.class, () -> new ExtraNumberField().setData("invalid-value"));
      assertEquals("JSON test locale: invalid-value", messages.getExceptionMessage(invalid));
    } finally {
      LocaleContextHolder.setLocaleContext(previous);
    }
  }

  @Test
  void acceptsNumbersWithinTheSupportedRange() {
    ExtraNumberField field = new ExtraNumberField();

    field.setData("123.450000000000000000000000000001");
    assertEquals("123.450000000000000000000000000001", field.getData());
  }

  @Test
  void copiesHistoricalNumbersWithoutRelaxingNewValueValidation() {
    ExtraNumberField storedField = new ExtraNumberField();
    storedField.getEditInfo().setDescription("1e-40");

    assertEquals("1e-40", storedField.shallowCopy().getData());
    assertThrows(IllegalArgumentException.class, () -> new ExtraNumberField().setData("1e-40"));
  }

  @Test
  void reportsSyntaxAndRangeErrorsWithTheExternalizedMessage() {
    ExtraNumberField field = new ExtraNumberField();

    IllegalArgumentException syntax =
        assertThrows(IllegalArgumentException.class, () -> field.setData("3.14asdf"));
    assertEquals(
        "'3.14asdf' is not a valid number or exceeds the supported precision and range.",
        messages.getExceptionMessage(syntax));

    IllegalArgumentException range =
        assertThrows(IllegalArgumentException.class, () -> field.setData("1".repeat(36)));
    assertEquals(
        "'111111111111111111111111111111111111' is not a valid number or exceeds the supported"
            + " precision and range.",
        messages.getExceptionMessage(range));
  }
}

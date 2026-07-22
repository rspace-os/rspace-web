package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.NoSuchMessageException;

class JsonMessageSourceTest {

  private static final Locale EN_US = Locale.forLanguageTag("en-US");
  private static final JsonMessageSource SOURCE = new JsonMessageSource();

  @Test
  void resolvesBundledServerJsonKey() {
    assertMessage("ResearchSpace", "webapp.name");
  }

  @Test
  void fallsBackToDefaultLocaleForUnknownLocale() {
    assertEquals("ResearchSpace", SOURCE.getMessage("webapp.name", null, Locale.FRENCH));
  }

  @Test
  void resolvesFrontendJsonKeysWithNamespace() {
    assertMessage("Add", "common:actions.add");
    assertThrows(NoSuchMessageException.class, () -> SOURCE.getMessage("actions.add", null, EN_US));
  }

  @Test
  void appliesPositionalMessageFormatArguments() {
    assertMessage("Name is a required field.", "test.required", "Name");
  }

  @Test
  void appliesIcuPluralFormatting() {
    assertMessage("1 item", "test.itemCount", 1);
    assertMessage("3 items", "test.itemCount", 3);
  }

  @Test
  void appliesIcuFormattingToBackendMessages() {
    assertMessage(
        "There are insufficient license seats available to create or re-enable a user. Please"
            + " contact your RSpace administrator. Contact support.",
        "license.insufficientSeats.details",
        0,
        1,
        "Contact support.");
    assertMessage(
        "Location (2,2) is outside container grid dimensions (columns: 1, rows: 1).",
        "errors.inventory.location.outsideGridDimensions",
        2,
        2,
        1,
        1);
    assertMessage(
        "'value' cannot be parsed",
        "errors.inventory.field.validation",
        "'value' cannot be parsed");
  }

  @Test
  void appliesIcuSelectFormatting() {
    assertMessage("Administrator", "test.role", "admin");
    assertMessage("Regular user", "test.role", "pi");
  }

  @Test
  void throwsForUnknownCodeByDefault() {
    assertThrows(
        NoSuchMessageException.class,
        () -> SOURCE.getMessage("no.such.key.exists.anywhere", null, EN_US));
  }

  private static void assertMessage(String expected, String key, Object... arguments) {
    assertEquals(expected, SOURCE.getMessage(key, arguments, EN_US));
  }
}

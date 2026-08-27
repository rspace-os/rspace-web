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
    assertMessage(
        "Incorrect id format - should be '\\d+-\\d+' but was 'bad-id'",
        "errors.composedId.invalidFormat",
        "bad-id");
    assertMessage(
        "By accepting, you will be removed from the group 'Example Group':",
        "groups.view.removeMe.confirmText",
        "Example Group");
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

  @Test
  void zeroArgumentMessagesShipTheirApostrophesUnescaped() {
    // getMessageInternal returns the raw pattern when args are empty, so an ICU-escaped '' in a
    // message no call site ever formats reaches the user as two apostrophes.
    assertMessage(
        "'id' property not provided for a field with 'deleteFieldRequest' flag.",
        "errors.inventory.field.deleteRequestIdMissing");
  }

  private static void assertMessage(String expected, String key, Object... arguments) {
    assertEquals(expected, SOURCE.getMessage(key, arguments, EN_US));
  }

  @Test
  void wholeNumbersInBarePlaceholdersKeepTheirDigits() {
    // ICU would otherwise group these, turning a record id into "73,662".
    assertEquals("Id: 73662", SOURCE.getMessage("test.recordId", new Object[] {73662L}, null));
  }

  @Test
  void numbersTypedByTheMessageStillFormat() {
    assertEquals("2 items", SOURCE.getMessage("test.itemCount", new Object[] {2}, null));
  }
}

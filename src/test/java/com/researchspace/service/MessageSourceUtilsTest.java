package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageSourceUtilsTest {

  private MessageSourceUtils messages;
  private final Locale enUS = Locale.forLanguageTag("en-US");

  @BeforeEach
  void setUp() {
    messages = new MessageSourceUtils(new JsonMessageSource());
  }

  @Test
  void appliesArgsAgainstExplicitLocale() {
    assertEquals(
        "Name is a required field.",
        messages.getMessage("errors.required", new Object[] {"Name"}, enUS));
  }

  @Test
  void formatAppliesListArgsAgainstExplicitLocale() {
    assertEquals(
        "Name is a required field.", messages.format("errors.required", List.of("Name"), enUS));
  }

  @Test
  void formatsCompleteAuthorizationFailure() {
    assertEquals(
        "Unauthorized attempt by alice to change bob's edit-all-work permission in group Lab",
        messages.getMessage(
            "errors.authorization.failure.changeGroupPiEditPermission",
            new Object[] {"alice", "bob", "Lab"},
            enUS));
  }
}

package com.researchspace.service.inventory.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

/**
 * The names an operation gives the fields it generates are PERSISTED, so a resolver that mangles
 * one writes the damage into the sample rather than showing a bad message once.
 */
class LabelResolverTest {

  private static final Locale EN = Locale.ENGLISH;

  private static LabelResolver resolving(String key, String pattern) {
    StaticMessageSource messages = new StaticMessageSource();
    messages.addMessage("inventory:" + key, EN, pattern);
    return LabelResolver.fromMessageSource(messages, EN);
  }

  @Test
  void leavesAnApostropheAloneInANameWithNoArguments() {
    // ICU MessageFormat reads ' as an escape, so an unconditional format() would turn
    // "Donor's aliquot" into "Donors aliquot" and store that.
    LabelResolver labels = resolving("operations.aliquot.linkFieldName", "Donor's aliquot");

    assertEquals("Donor's aliquot", labels.resolve("operations.aliquot.linkFieldName"));
  }

  @Test
  void interpolatesNamedArgumentsAsIcuMessageFormat() {
    LabelResolver labels = resolving("operations.pool.linkFieldName", "Pooled from: {originName}");

    assertEquals(
        "Pooled from: Vial A",
        labels.resolve("operations.pool.linkFieldName", Map.of("originName", "Vial A")));
  }

  @Test
  void fallsBackToTheKeyWhenTheCatalogHasNoEntry() {
    LabelResolver labels = resolving("operations.aliquot.linkFieldName", "Derived from");

    assertEquals("inventory:operations.absent.field", labels.resolve("operations.absent.field"));
  }
}

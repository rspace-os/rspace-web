package com.researchspace.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

class EnglishInflectorTest {

  /**
   * Upstream pluralize test.js pairs. Kind "basic" runs both ways, "plural" only pluralizes and
   * "singular" only singularizes. A pluralize800 value is what pluralize 8.0.0 itself returns for
   * the singular where the fixture, written after that release, expects more.
   */
  @ParameterizedTest(name = "{0}: {1} <-> {2}")
  @CsvFileSource(resources = "pluralize-test-cases.csv", numLinesToSkip = 1)
  void matchesTheUpstreamFixture(String kind, String singular, String plural, String npmSingular) {
    if (!kind.equals("singular")) {
      assertEquals(plural, EnglishInflector.plural(singular));
      assertTrue(EnglishInflector.isPlural(plural));
      assertEquals(plural, EnglishInflector.plural(plural, 5));
      assertEquals(plural, EnglishInflector.plural(singular, 5));
    }
    if (!kind.equals("plural")) {
      String expected = npmSingular == null ? singular : npmSingular;
      assertEquals(expected, EnglishInflector.singular(plural));
      assertTrue(EnglishInflector.isSingular(singular));
      assertEquals(singular, EnglishInflector.plural(singular, 1));
      assertEquals(expected, EnglishInflector.plural(plural, 1));
    }
  }

  /** Every fixture word, our resource names and their upper and title case, run through npm. */
  @ParameterizedTest(name = "{0}")
  @CsvFileSource(resources = "pluralize-npm-8.0.0.csv", numLinesToSkip = 1)
  void matchesNpmPluralize(
      String word, String plural, String singular, boolean isPlural, boolean isSingular) {
    assertEquals(plural, EnglishInflector.plural(word));
    assertEquals(singular, EnglishInflector.singular(word));
    assertEquals(isPlural, EnglishInflector.isPlural(word));
    assertEquals(isSingular, EnglishInflector.isSingular(word));
  }

  @ParameterizedTest
  @CsvSource({
    "user, users",
    "maintenance, maintenances",
    "booking-configuration, booking-configurations",
    "feature-flag, feature-flags",
    "container, containers",
    "instrument, instruments",
    "status, statuses",
    "address, addresses",
    "box, boxes",
    "house, houses",
    "phase, phases",
    "movie, movies",
    "valve, valves",
    "crisis, crises",
    "index, indices",
    "person, people"
  })
  void inflectsResourceNamesAndHazardousWords(String singular, String plural) {
    assertEquals(plural, EnglishInflector.plural(singular));
    assertEquals(singular, EnglishInflector.singular(plural));
  }
}

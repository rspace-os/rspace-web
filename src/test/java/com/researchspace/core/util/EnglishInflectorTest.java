package com.researchspace.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Word pairs from hypertino/inflector 1.0.14 TestEnglishInflector, plus API v2 resource names. */
class EnglishInflectorTest {

  @ParameterizedTest
  @CsvSource({
    "alga, algae",
    "nova, novas",
    "dogma, dogmas",
    "Woman, Women",
    "church, churches",
    "quick_chateau, quick_chateaus",
    "codex, codices",
    "index, indexes",
    "NightWolf, NightWolves",
    "Milieu, Milieus",
    "basis, bases",
    "iris, irises",
    "phalanx, phalanxes",
    "tempo, tempos",
    "foot, feet",
    "series, series",
    "WorldAtlas, WorldAtlases",
    "wish, wishes",
    "Bacterium, Bacteria",
    "medium, mediums",
    "Genus, Genera",
    "stimulus, stimuli",
    "opus, opuses",
    "status, statuses",
    "Box, Boxes",
    "ferry, ferries",
    "protozoon, protozoa",
    "cherub, cherubs",
    "human, humans",
    "sugar, sugar",
    "virus, viruses",
    "gastrostomy, gastrostomies",
    "baculum, bacula",
    "pancreas, pancreases",
    "todo, todos",
    "slice, slices",
    "cookie, cookies",
    "response, responses",
    "case, cases",
    "each, each",
    "license, licenses",
    "day, days",
    "hero, heroes",
    "potato, potatoes",
    "scarf, scarves",
    "user, users",
    "maintenance, maintenances",
    "booking-configuration, booking-configurations",
    "feature-flag, feature-flags",
    "container, containers",
    "instrument, instruments",
    "address, addresses",
    "box, boxes",
    "batch, batches"
  })
  void inflectsBothWays(String singular, String plural) {
    assertEquals(plural, EnglishInflector.plural(singular));
    assertEquals(singular, EnglishInflector.singular(plural));
  }

  @Test
  void pluralizesForCount() {
    assertEquals("cat", EnglishInflector.plural("cat", 1));
    assertEquals("cats", EnglishInflector.plural("cat", 2));
    assertEquals("demoness", EnglishInflector.plural("demoness", 1));
    assertEquals("demonesses", EnglishInflector.plural("demoness", 2));
    assertEquals("semifluids", EnglishInflector.plural("semifluid", 2));
  }

  @Test
  void inflectsWordsOutsideTheRuleLists() {
    assertEquals("sulfimides", EnglishInflector.plural("sulfimide"));
    assertEquals("sulfimide", EnglishInflector.singular("sulfimides"));
    assertEquals("semifluid", EnglishInflector.singular("semifluids"));
    assertEquals("cat", EnglishInflector.singular("cats"));
  }
}

package com.researchspace.model.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CommunityListResultTest {

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  // test various valid user inputs can be handled correctly
  @Test
  public void testValidateMultiCommunityAutocompleteInput() {
    String[] inputs =
        new String[] {
          "All Groups <-1>, name2 <567>",
          "name1<123>, name2<567>,",
          "name1<123>, name2<567>",
          "name1<1>, name2<567>",
          "name2 <123>, name2 <567>"
        };
    for (String in : inputs) {
      assertTrue(CommunityListResult.validateMultiCommunityAutocompleteInput(in));
      Set<Long> ids2 = CommunityListResult.getCommunityIdsfromMultiGroupAutocomplete(in);
      assertThat(ids2).hasSize(2);
      assertThat(ids2).contains(567L);
    }
    assertEquals(
        Set.of(-1L), CommunityListResult.getCommunityIdsfromMultiGroupAutocomplete("All <-1>"));
    assertFalse(CommunityListResult.validateMultiCommunityAutocompleteInput("All <->"));
    assertFalse(CommunityListResult.validateMultiCommunityAutocompleteInput("All <--1>"));
    assertFalse(
        CommunityListResult.validateMultiCommunityAutocompleteInput("All <-9223372036854775809>"));
    assertFalse(CommunityListResult.validateMultiCommunityAutocompleteInput("INVALID"));
    assertFalse(CommunityListResult.validateMultiCommunityAutocompleteInput("All <-1>, <2>"));
  }

  @Test
  public void parsingFinishesQuicklyOnPathologicalInput() {
    String spaces = " ".repeat(100_000);
    String innerSpaces = "a" + spaces + "b";
    assertTimeoutPreemptively(
        Duration.ofSeconds(1),
        () -> {
          assertFalse(CommunityListResult.validateMultiCommunityAutocompleteInput(spaces));
          assertTrue(
              CommunityListResult.getCommunityIdsfromMultiGroupAutocomplete(spaces).isEmpty());
          assertFalse(CommunityListResult.validateMultiCommunityAutocompleteInput(innerSpaces));
          assertTrue(
              CommunityListResult.getCommunityIdsfromMultiGroupAutocomplete(innerSpaces).isEmpty());
        });
  }
}

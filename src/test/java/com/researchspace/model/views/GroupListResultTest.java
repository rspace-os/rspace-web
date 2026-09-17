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

public class GroupListResultTest {

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testValidateSingleItem() {
    assertTrue(GroupListResult.validateMultiGroupAutocompleteInput("name <123>"));
    assertFalse(GroupListResult.validateMultiGroupAutocompleteInput("name <1abc>"));
    assertFalse(GroupListResult.validateMultiGroupAutocompleteInput("name <-1>"));
    assertFalse(GroupListResult.validateMultiGroupAutocompleteInput("<123>"));
    assertFalse(GroupListResult.validateMultiGroupAutocompleteInput("name <1>, <2>"));
    assertFalse(GroupListResult.validateMultiGroupAutocompleteInput("name <123>, extra"));
    assertFalse(GroupListResult.validateMultiGroupAutocompleteInput("name <123>,,"));
    assertFalse(GroupListResult.validateMultiGroupAutocompleteInput(">>"));
    assertFalse(GroupListResult.validateMultiGroupAutocompleteInput("name <123>>"));
    assertFalse(GroupListResult.validateMultiGroupAutocompleteInput("name <9223372036854775808>"));
    assertFalse(GroupListResult.validateMultiGroupAutocompleteInput("INVALID"));
    assertFalse(GroupListResult.validateMultiGroupAutocompleteInput(""));
    assertFalse(GroupListResult.validateMultiGroupAutocompleteInput(null));
    assertTrue(GroupListResult.getGroupIdsfromMultiGroupAutocomplete(null).isEmpty());
    // malformed items are skipped, well-formed ones still parsed
    assertEquals(
        Set.of(5L), GroupListResult.getGroupIdsfromMultiGroupAutocomplete("name <1abc>, x<5>"));
    assertTrue(GroupListResult.validateMultiGroupAutocompleteInput("Alpha > Beta<123>"));
    assertEquals(
        Set.of(123L), GroupListResult.getGroupIdsfromMultiGroupAutocomplete("Alpha > Beta<123>"));
  }

  @Test
  public void parsingFinishesQuicklyOnPathologicalInput() {
    String spaces = " ".repeat(100_000);
    String unclosed = "<".repeat(100_000) + "1>";
    String invalidClosings = "<x" + ">".repeat(100_000);
    String innerSpaces = "a" + " ".repeat(100_000) + "b";
    assertTimeoutPreemptively(
        Duration.ofSeconds(1),
        () -> {
          assertFalse(GroupListResult.validateMultiGroupAutocompleteInput(spaces));
          assertTrue(GroupListResult.getGroupIdsfromMultiGroupAutocomplete(spaces).isEmpty());
          assertTrue(GroupListResult.validateMultiGroupAutocompleteInput(unclosed));
          assertEquals(Set.of(1L), GroupListResult.getGroupIdsfromMultiGroupAutocomplete(unclosed));
          assertFalse(GroupListResult.validateMultiGroupAutocompleteInput(invalidClosings));
          assertFalse(GroupListResult.validateMultiGroupAutocompleteInput(innerSpaces));
          assertTrue(GroupListResult.getGroupIdsfromMultiGroupAutocomplete(innerSpaces).isEmpty());
        });
  }

  @Test
  public void testPArseAutcompleteInput() {
    String input1 = "name1<123>, name2<567>";
    Set<Long> ids = GroupListResult.getGroupIdsfromMultiGroupAutocomplete(input1);
    assertThat(ids).hasSize(2);
    assertThat(ids).contains(567L);

    String[] singleGroupInputs = new String[] {"name1<123>,", "name1<123>, "};
    for (String in : singleGroupInputs) {
      assertTrue(GroupListResult.validateMultiGroupAutocompleteInput(in));
      Set<Long> ids2 = GroupListResult.getGroupIdsfromMultiGroupAutocomplete(in);
      assertThat(ids2).hasSize(1);
    }

    String[] inputs =
        new String[] {
          "name1<123>, name2<567>,",
          "name1<123>, name2<567>",
          "name1<1>, name2<567>",
          "name2 <123>, name2 <567>"
        };
    for (String in : inputs) {
      assertTrue(GroupListResult.validateMultiGroupAutocompleteInput(in));
      Set<Long> ids2 = GroupListResult.getGroupIdsfromMultiGroupAutocomplete(in);
      assertThat(ids2).hasSize(2);
      assertThat(ids2).contains(567L);
    }
  }
}

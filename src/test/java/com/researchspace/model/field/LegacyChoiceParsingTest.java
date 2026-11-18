package com.researchspace.model.field;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The legacy ELN choice and radio parsers delegate to {@code ElnChoiceValues}, so display, export,
 * the API and display rules read stored {@code name=a&name=b} data the same way. Valid data reads
 * exactly as before; malformed data that used to throw is now skipped.
 */
class LegacyChoiceParsingTest {

  @Test
  void validChoiceDataReadsAsBefore() {
    ChoiceBehaviour choices = new ChoiceBehaviour();
    String data = "fieldChoices=a&fieldChoices=val 2";

    assertEquals(List.of("a", "val 2"), choices.getChoiceOptionSelectedAsList(() -> data));
    assertEquals("a, val 2", choices.getChoiceOptionSelectedAsString(() -> data));
  }

  @Test
  void emptyChoiceDataReadsAsNothingSelected() {
    ChoiceBehaviour choices = new ChoiceBehaviour();

    assertEquals(List.of(), choices.getChoiceOptionSelectedAsList(() -> ""));
    assertEquals("", choices.getChoiceOptionSelectedAsString(() -> ""));
  }

  @Test
  void validOptionListsReadAsBefore() {
    ChoiceFieldForm choice = new ChoiceFieldForm();
    choice.setChoiceOptions("fieldChoices=a&fieldChoices=b&fieldChoices=c");
    choice.setDefaultChoiceOption("fieldSelectedChoices=b");

    assertEquals(List.of("a", "b", "c"), choice.getChoiceOptionAsList());
    assertEquals(List.of("b"), choice.getDefaultChoiceOptionAsList());
    assertEquals(
        List.of("Yes", "No"), new RadioBehaviour().getAsList("fieldRadios=Yes&fieldRadios=No"));
  }

  /** Each of these threw ArrayIndexOutOfBoundsException before the parsers were consolidated. */
  @ParameterizedTest
  @ValueSource(strings = {"&fieldChoices=a", "fieldChoices=", "no separator"})
  void malformedDataNoLongerThrows(String data) {
    ChoiceBehaviour choices = new ChoiceBehaviour();
    ChoiceFieldForm choice = new ChoiceFieldForm();
    choice.setChoiceOptions(data);

    List<String> expected = data.startsWith("&") ? List.of("a") : List.of();
    assertEquals(expected, choices.getChoiceOptionSelectedAsList(() -> data));
    assertEquals(expected, choice.getChoiceOptionAsList());
    assertEquals(expected, new RadioBehaviour().getAsList(data));
  }
}

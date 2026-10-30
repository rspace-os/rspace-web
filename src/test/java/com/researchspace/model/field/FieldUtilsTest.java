package com.researchspace.model.field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class FieldUtilsTest {

  @ParameterizedTest
  @ValueSource(
      strings = {"a=b", "a=b&c=d", "a=val 1&c=val 2", "a=b&", "v=4&v=6&v=8&v=other", "a=b=c"})
  public void acceptsNameValueLists(String options) {
    assertTrue(FieldUtils.isValidRadioOrChoiceString(options));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "&",
        "&&",
        "a-b",
        "&a=b",
        "a=b&&",
        "a=b&&c=d",
        "a=val&2 1&c=val 2",
        " a=b",
        "a="
      })
  public void rejectsMalformedLists(String options) {
    assertFalse(FieldUtils.isValidRadioOrChoiceString(options));
  }

  @Test
  public void lengthIsCheckedSeparatelyFromFormat() {
    assertFalse(FieldUtils.isValidRadioOrChoiceString(null));
    String longest = "a=" + "b".repeat(FieldUtils.MAX_OPTIONS_LENGTH - 2);
    assertTrue(FieldUtils.isValidRadioOrChoiceString(longest));
    assertFalse(FieldUtils.isTooLong(longest));
    assertTrue(FieldUtils.isValidRadioOrChoiceString(longest + "b"));
    assertTrue(FieldUtils.isTooLong(longest + "b"));
    assertFalse(FieldUtils.isTooLong(null));
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 1000, 10_000})
  public void attackStringsFinishQuickly(int repeats) {
    String unbalanced = "!=" + "%!!=".repeat(repeats);
    String danglingEquals = "a=b&".repeat(repeats) + "=";
    assertTimeoutPreemptively(
        Duration.ofSeconds(1),
        () -> {
          assertFalse(FieldUtils.isValidRadioOrChoiceString(unbalanced));
          assertFalse(FieldUtils.isValidRadioOrChoiceString(danglingEquals));
        });
  }
}

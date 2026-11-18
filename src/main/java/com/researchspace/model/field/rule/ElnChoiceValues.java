package com.researchspace.model.field.rule;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/** Reads the selected options out of stored ELN choice field data. */
public final class ElnChoiceValues {

  private ElnChoiceValues() {}

  /**
   * Parses data such as {@code fieldChoices=a&fieldChoices=b} into {@code [a, b]}.
   *
   * <p>Matches what {@code ChoiceBehaviour} displays: each {@code &}-separated segment contributes
   * the text between its first and second {@code =}. Unlike {@code ChoiceBehaviour}, it never
   * throws: segments without {@code =} and empty values are skipped, because stored data written
   * through the forms API is not validated.
   *
   * @return the selected options, empty for blank data
   */
  public static List<String> parse(String data) {
    List<String> selected = new ArrayList<>();
    if (StringUtils.isBlank(data)) {
      return selected;
    }
    for (String segment : data.split("&")) {
      // The text between the first and second '='; empty when the segment has no '='.
      String value = StringUtils.substringBefore(StringUtils.substringAfter(segment, "="), "=");
      if (!value.isEmpty()) {
        selected.add(value);
      }
    }
    return selected;
  }
}

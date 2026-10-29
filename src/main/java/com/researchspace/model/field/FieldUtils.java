package com.researchspace.model.field;

import java.util.regex.Pattern;

/** Utility class for String matching. */
public class FieldUtils {

  /** Longest accepted option string, matching the length of the stored choice options column. */
  public static final int MAX_OPTIONS_LENGTH = 1000;

  private static final Pattern OPTION = Pattern.compile("\\S+=[^&=]+");

  /**
   * Checks that a choice or radio option string is a non-empty '&amp;'-separated list of
   * 'name=value' options, with no whitespace in names and no '=' or '&amp;' in values.
   */
  public static boolean isValidRadioOrChoiceString(String toTest) {
    if (toTest == null || toTest.isEmpty()) {
      return false;
    }
    String[] options = toTest.split("&", -1);
    int optionCount = options.length;
    if (options[optionCount - 1].isEmpty()) {
      optionCount--;
    }
    for (int i = 0; i < optionCount; i++) {
      if (!OPTION.matcher(options[i]).matches()) {
        return false;
      }
    }
    return true;
  }

  public static boolean isTooLong(String options) {
    return options != null && options.length() > MAX_OPTIONS_LENGTH;
  }
}

package com.researchspace.model.dtos;

import org.springframework.validation.Errors;

public class NumberUtils {
  /**
   * Validates if the passed-in <code>String</code> can be converted to a double.
   *
   * @param fieldName
   * @param inputText
   * @param errors An {@link Errors} object
   */
  public static void validateNumber(String fieldName, String inputText, Errors errors) {
    try {
      Double.parseDouble(inputText);
    } catch (NumberFormatException nfe) {
      errors.rejectValue(fieldName, "errors.number.invalidNumber");
    }
  }
}

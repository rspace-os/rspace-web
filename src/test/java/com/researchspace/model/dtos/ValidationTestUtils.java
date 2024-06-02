package com.researchspace.model.dtos;

import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

/*
 * Utility methods for unit tests
 */
public class ValidationTestUtils {

  public static boolean hasError(String errorCode, Errors errors) {
    for (ObjectError err : errors.getAllErrors()) {
      if (err.getCode().equals(errorCode)) {
        return true;
      }
    }
    return false;
  }
}

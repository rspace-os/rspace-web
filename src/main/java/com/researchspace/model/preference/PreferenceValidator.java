package com.researchspace.model.preference;

import com.researchspace.model.field.LocalizedIllegalArgumentException;

public interface PreferenceValidator {
  /**
   * @param value
   * @return a localized exception on failure, otherwise null on success
   */
  LocalizedIllegalArgumentException getExceptionIfInvalid(String value);

  PreferenceValidator ALWAYS_TRUE = value -> null;
}

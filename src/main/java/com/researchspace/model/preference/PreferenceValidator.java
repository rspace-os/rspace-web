package com.researchspace.model.preference;

import com.researchspace.model.field.LocalizedIllegalArgumentException;

public interface PreferenceValidator {
  /**
   * @param value
   * @return a localized exception on failure, otherwise null on success
   */
  LocalizedIllegalArgumentException getExceptionIfInvalid(String value);

  /**
   * @deprecated use {@link #getExceptionIfInvalid(String)} so arguments can be localized.
   */
  @Deprecated
  default String getMsgIfInvalid(String value) {
    LocalizedIllegalArgumentException exception = getExceptionIfInvalid(value);
    return exception == null ? null : exception.getMessage();
  }

  PreferenceValidator ALWAYS_TRUE = value -> null;
}

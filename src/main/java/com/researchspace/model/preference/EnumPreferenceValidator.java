package com.researchspace.model.preference;

import com.researchspace.model.field.LocalizedIllegalArgumentException;
import org.apache.commons.lang3.StringUtils;

public class EnumPreferenceValidator implements PreferenceValidator {

  private final Class<?> type;

  public EnumPreferenceValidator(Class<?> type) {
    this.type = type;
  }

  public LocalizedIllegalArgumentException getExceptionIfInvalid(String value) {
    if (value != null) {
      for (Object enumValue : type.getEnumConstants()) {
        if (enumValue.toString().equals(value)) {
          return null;
        }
      }
      return new LocalizedIllegalArgumentException(
          "validation.preference.unknownEnumValue",
          value,
          type.getName(),
          StringUtils.join(type.getEnumConstants(), ","));
    }
    return null;
  }
}

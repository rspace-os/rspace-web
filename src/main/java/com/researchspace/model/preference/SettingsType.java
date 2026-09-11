package com.researchspace.model.preference;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.researchspace.model.field.LocalizedIllegalArgumentException;

/** Basic typing for preferences/system settings */
public enum SettingsType {
  BOOLEAN,
  NUMBER,
  STRING,
  ENUM,
  TEXT;

  private static final int MAX_SIZE_LIMIT = 255;
  private static final int MAX_TEXT_SIZE_LIMIT = 65535;

  public static void validate(SettingsType type, String value) {
    if (isEmpty(value)) {
      return;
    }

    if (type.equals(SettingsType.TEXT)) {
      if (value.length() > MAX_TEXT_SIZE_LIMIT) {
        throw new LocalizedIllegalArgumentException(
            "validation.settings.textTooLong", value.length(), MAX_TEXT_SIZE_LIMIT);
      }
    } else {
      if (value.length() > MAX_SIZE_LIMIT) {
        throw new LocalizedIllegalArgumentException(
            "validation.settings.valueTooLong", value.length(), MAX_SIZE_LIMIT);
      }
    }

    if (type.equals(SettingsType.BOOLEAN)) {
      if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
        throw new LocalizedIllegalArgumentException("validation.settings.invalidBoolean", value);
      }
    } else if (type.equals(SettingsType.NUMBER)) {
      try {
        Double.valueOf(value);
      } catch (NumberFormatException e) {
        throw new LocalizedIllegalArgumentException("validation.settings.invalidNumber", value);
      }
    }
  }
}

package com.researchspace.model.preference;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Basic typing for preferences/system settings
 */
public enum SettingsType {
	
	BOOLEAN, NUMBER, STRING, ENUM, TEXT;
	
	private static final int MAX_SIZE_LIMIT = 255;
	private static final int MAX_TEXT_SIZE_LIMIT =	65535;

	public static void validate(SettingsType type, String value) {
		if (isEmpty(value)) {
			return;
		}

		if (type.equals(SettingsType.TEXT)) {
			if (value.length() > MAX_TEXT_SIZE_LIMIT) {
				throw new IllegalArgumentException(
						format("Text value is too long, is %d characters but max is %d", value.length(),
								MAX_TEXT_SIZE_LIMIT));
			}
		} else {
			if (value.length() > MAX_SIZE_LIMIT) {
				throw new IllegalArgumentException(
						format("Value is too long, is %d characters but max is %d", value.length(),
								MAX_SIZE_LIMIT));
			}
		}

		if (type.equals(SettingsType.BOOLEAN)) {
			if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
				throw new IllegalArgumentException(format("[%s] is not a boolean", value));
			}
		} else if (type.equals(SettingsType.NUMBER)) {
			try {
				Double.valueOf(value);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(format("[%s] is not a number", value));
			}
		}
	}

}

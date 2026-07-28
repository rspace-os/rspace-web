package com.researchspace.model.preference;

import org.apache.commons.lang3.StringUtils;

public class EnumPreferenceValidator implements PreferenceValidator {

	private Class<?> type;
	
	public EnumPreferenceValidator(Class<?> type) {
		this.type = type;
	}
	
	public String getMsgIfInvalid(String value) {
		if (value != null) {
			for (Object enumValue : type.getEnumConstants()) {
				if (enumValue.toString().equals(value)) {
					return null;
				}
			}
			return String.format("Unknown value [%s] for [%s], must be one of %s", 
					value, type.getName(), StringUtils.join(type.getEnumConstants(), ","));
		}
		return null;
	}

}

package com.researchspace.model.system;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import com.researchspace.model.PropertyDescriptor;
import com.researchspace.model.apps.App;
import com.researchspace.model.preference.SettingsType;

public class SystemPropertyTestFactory {
	
	/**
	 * Creates a boolean system property, default true
	 */
	public static SystemProperty createASystemProperty() {
		PropertyDescriptor desc = createAPropertyDescriptor();
		return new SystemProperty(desc);
	}

	public static PropertyDescriptor createAPropertyDescriptor() {
		return new PropertyDescriptor(randomAlphabetic(5), SettingsType.BOOLEAN, "true");
	}

	public static PropertyDescriptor createAPropertyDescriptor(String name, SettingsType type, String defaultValue) {
		PropertyDescriptor pd = createAPropertyDescriptor();
		pd.setName(name);
		pd.setType(type);
		pd.setDefaultValue(defaultValue);
		return pd;

	}

	public static App createAnyApp() {
		return new App("app", "app description", true);
	}

	
}

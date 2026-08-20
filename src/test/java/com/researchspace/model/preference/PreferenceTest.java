package com.researchspace.model.preference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PreferenceTest {

	//RSPAC-1919
	@ParameterizedTest(name="{0} has default = true")
	@MethodSource("messagingPreferences")
	void defaultNotificationPrefsAreTrue(Preference preference) {
		assertTrue(Boolean.TRUE.equals(Boolean.parseBoolean(preference.getDefaultValue())));
	}
	
	@ParameterizedTest(name="{0} is a message preference")
	@MethodSource("messagingPreferences")
	void messagingPrefs(Preference preference) {
		assertTrue(preference.isMessagingPreference());
	}
	
	@ParameterizedTest(name="{0} is not a message preference")
	@MethodSource("notMessagingPreferences")
	void nonMessagingPrefs(Preference preference) {
		assertFalse(preference.isMessagingPreference());
	}
	
	static Stream<Preference> messagingPreferences (){
	  return EnumSet.allOf(Preference.class).stream()
		.filter(PreferenceTest::isMessage);
	}

	private static boolean isMessage(Preference p) {
		return p.getCategory().equals(PreferenceCategory.MESSAGING)
				|| p.getCategory().equals(PreferenceCategory.MESSAGING_BROADCAST);
	}
	
	static Stream<Preference> notMessagingPreferences (){
		return EnumSet.allOf(Preference.class).stream()
				.filter(p->!isMessage(p));
	}
}

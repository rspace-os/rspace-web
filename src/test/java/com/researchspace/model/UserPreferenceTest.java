package com.researchspace.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.TestFactory;
import org.junit.jupiter.api.Assertions;

public class UserPreferenceTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testUserPreferencePreferenceUserString() {
		new UserPreference(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF, 
				TestFactory.createAnyUser("user"), "true");
	}
	
	@Test
	(expected=IllegalArgumentException.class)
	public void testUserPreferenceMaxStringLength() {
		String str = CoreTestUtils.getRandomName(256); // just a string > max length
		new UserPreference(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF, 
				TestFactory.createAnyUser("user"), str);
	}
	
	@Test
	(expected=IllegalStateException.class)
	public void testUserPreferenceGetWrongValueTypeThrosISE() {
		UserPreference up =new UserPreference(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF, 
				TestFactory.createAnyUser("user"), "false");
		up.getValueAsNumber();
	}

	@Test
	(expected=IllegalArgumentException.class)
	public void testBooleanPreferenceChecksArgs() {
		// this is a boolean pref, can only be set with string representation of booleans
		UserPreference up =	createUserPref(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF);
		//all ok
		up.setValue("true");
		up.setValue("false");
		up.setValue("FALSE");
		up.setValue("TRUE");
		
		//throws IAE
		up.setValue("otherstring");
		
	}
	@Test
	public void testUserPreferenceREturnsDEfaultValueUnlessSet() {
		
		UserPreference up =	createUserPref(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF);
		assertEquals(up.getValue(), Preference.NOTIFICATION_DOCUMENT_EDITED_PREF.getDefaultValue());
		up.setValue("true");
		assertEquals("true", up.getValue());
		assertTrue(up.isBooleanType());
		assertFalse(up.isNumeric());
	}

	UserPreference createUserPref(Preference type) {
		return new UserPreference(type, TestFactory.createAnyUser("user"), null);
	}
	
	@Test
	public void testEqualityBasedonTypeAndUserButNotValue() {
		UserPreference up = createUserPref(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF);
		UserPreference up2 = createUserPref(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF);
		assertEquals(up, up2);
		// values are different but objects still equal
		up2.setValue("true");
		assertEquals(up, up2);
		
		UserPreference upOther = createUserPref(Preference.NOTIFICATION_DOCUMENT_SHARED_PREF);
		assertFalse(upOther.equals(up));
	}

	@Test
	public void testAdvancedPref() {
		UserPreference pageSizePref = createUserPref(Preference.UI_PDF_PAGE_SIZE);
		pageSizePref.setValue("LETTER");
		
		UserPreference boxPref = createUserPref(Preference.BOX_LINK_TYPE);
		boxPref.setValue("VERSIONED");
	}

	@Test
	public void testValidLengthDependingOnPreferenceType() {
		String longStringValue = StringUtils.repeat("x", 300);
		String veryLongStringValue = StringUtils.repeat("x", 70000);

		IllegalArgumentException iae = Assertions.assertThrows(IllegalArgumentException.class,
				()-> createUserPref(Preference.UI_CLIENT_SETTINGS).setValue(longStringValue));
		assertEquals("Value is too long, is 300 characters but max is 255", iae.getMessage());

		createUserPref(Preference.UI_JSON_SETTINGS).setValue(longStringValue);
		iae = Assertions.assertThrows(IllegalArgumentException.class,
				()-> createUserPref(Preference.UI_JSON_SETTINGS).setValue(veryLongStringValue));
		assertEquals("Text value is too long, is 70000 characters but max is 65535", iae.getMessage());
	}

}

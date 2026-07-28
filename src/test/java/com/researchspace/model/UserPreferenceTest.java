package com.researchspace.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.TestFactory;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserPreferenceTest {

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testUserPreferencePreferenceUserString() {
    new UserPreference(
        Preference.NOTIFICATION_DOCUMENT_EDITED_PREF, TestFactory.createAnyUser("user"), "true");
  }

  @Test
  public void testUserPreferenceMaxStringLength() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          String str = CoreTestUtils.getRandomName(256); // just a string > max length
          new UserPreference(
              Preference.NOTIFICATION_DOCUMENT_EDITED_PREF, TestFactory.createAnyUser("user"), str);
        });
  }

  @Test
  public void testUserPreferenceGetWrongValueTypeThrosISE() {
    assertThrows(
        IllegalStateException.class,
        () -> {
          UserPreference up =
              new UserPreference(
                  Preference.NOTIFICATION_DOCUMENT_EDITED_PREF,
                  TestFactory.createAnyUser("user"),
                  "false");
          up.getValueAsNumber();
        });
  }

  @Test
  public void testBooleanPreferenceChecksArgs() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          // this is a boolean pref, can only be set with string representation of booleans
          UserPreference up = createUserPref(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF);
          // all ok
          up.setValue("true");
          up.setValue("false");
          up.setValue("FALSE");
          up.setValue("TRUE");

          // throws IAE
          up.setValue("otherstring");
        });
  }

  @Test
  public void testUserPreferenceREturnsDEfaultValueUnlessSet() {

    UserPreference up = createUserPref(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF);
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

    IllegalArgumentException iae =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> createUserPref(Preference.UI_CLIENT_SETTINGS).setValue(longStringValue));
    assertEquals("Value is too long, is 300 characters but max is 255", iae.getMessage());

    createUserPref(Preference.UI_JSON_SETTINGS).setValue(longStringValue);
    iae =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> createUserPref(Preference.UI_JSON_SETTINGS).setValue(veryLongStringValue));
    assertEquals("Text value is too long, is 70000 characters but max is 65535", iae.getMessage());
  }
}

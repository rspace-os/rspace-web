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
          UserPreference up = createUserPref(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF);
          up.setValue("true");
          up.setValue("false");
          up.setValue("FALSE");
          up.setValue("TRUE");

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
  public void invalidNumberAndEnumPreferencesUseMessageCodes() {
    IllegalArgumentException exception =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> createUserPref(Preference.WORKSPACE_RESULTS_PER_PAGE).setValue("not-number"));
    assertEquals("validation.settings.invalidNumber", exception.getMessage());

    exception =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> createUserPref(Preference.UI_PDF_PAGE_SIZE).setValue("not-page-size"));
    assertEquals("validation.preference.unknownEnumValue", exception.getMessage());
  }

  @Test
  public void testValidLengthDependingOnPreferenceType() {
    String longStringValue = StringUtils.repeat("x", 300);
    String veryLongStringValue = StringUtils.repeat("x", 70000);

    IllegalArgumentException iae =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> createUserPref(Preference.UI_CLIENT_SETTINGS).setValue(longStringValue));
    assertEquals("validation.settings.valueTooLong", iae.getMessage());

    createUserPref(Preference.UI_JSON_SETTINGS).setValue(longStringValue);
    iae =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> createUserPref(Preference.UI_JSON_SETTINGS).setValue(veryLongStringValue));
    assertEquals("validation.settings.textTooLong", iae.getMessage());
  }
}

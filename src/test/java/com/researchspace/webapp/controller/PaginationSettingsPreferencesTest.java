package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static com.researchspace.model.preference.Preference.FORM_RESULTS_PER_PAGE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.UserManager;
import java.util.EnumSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PaginationSettingsPreferencesTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock UserManager userMgr;
  @InjectMocks PaginationSettingsPreferences paginationSettingsPreferences;
  User anyUser = TestFactory.createAnyUser("any");

  @Before
  public void setUp() throws Exception {}

  @Test
  public void rejectNonRESULTS_PER_PAGEPrefs() {

    PaginationCriteria<Object> pgCrit = PaginationCriteria.createDefaultForClass(Object.class);
    EnumSet.allOf(Preference.class).stream()
        .filter(pref -> !pref.name().endsWith("RESULTS_PER_PAGE"))
        .forEach(
            pref ->
                assertIllegalArgumentException(
                    () ->
                        paginationSettingsPreferences.updateResultsPerPageProperty(
                            anyUser, pgCrit, pref)));
  }

  @Test
  // condition 2 from RSPAC-1740
  public void useIncomingPgCritResultsPerPage_Condition2() {
    // default preference is 10
    when(userMgr.getPreferenceForUser(anyUser, FORM_RESULTS_PER_PAGE))
        .thenReturn(new UserPreference(FORM_RESULTS_PER_PAGE, anyUser, null));
    PaginationCriteria<Object> pgCrit = PaginationCriteria.createDefaultForClass(Object.class);
    final int nonDefaultResultsPerPAge = 4;
    pgCrit.setResultsPerPage(nonDefaultResultsPerPAge);
    paginationSettingsPreferences.updateResultsPerPageProperty(
        anyUser, pgCrit, FORM_RESULTS_PER_PAGE);
    // we use incoming value
    assertEquals(nonDefaultResultsPerPAge, pgCrit.getResultsPerPage().intValue());
    // and update the preference
    Mockito.verify(userMgr)
        .setPreference(
            FORM_RESULTS_PER_PAGE, nonDefaultResultsPerPAge + "", anyUser.getUniqueName());
  }

  @Test
  // condition 1 from RSPAC-1740 - pref & incoming pagination are the same
  public void useIncomingPgCritResultsPerPage_Condition1() {
    final int nonDefaultPreference = 16;
    when(userMgr.getPreferenceForUser(anyUser, FORM_RESULTS_PER_PAGE))
        .thenReturn(new UserPreference(FORM_RESULTS_PER_PAGE, anyUser, nonDefaultPreference + ""));
    PaginationCriteria<Object> pgCrit = PaginationCriteria.createDefaultForClass(Object.class);
    final int nonDefaultResultsPerPage = nonDefaultPreference;
    pgCrit.setResultsPerPage(nonDefaultResultsPerPage);
    paginationSettingsPreferences.updateResultsPerPageProperty(
        anyUser, pgCrit, FORM_RESULTS_PER_PAGE);
    // we use incoming value
    assertEquals(nonDefaultResultsPerPage, pgCrit.getResultsPerPage().intValue());
    // and the preference is not updated
    Mockito.verify(userMgr, never())
        .setPreference(
            FORM_RESULTS_PER_PAGE, nonDefaultResultsPerPage + "", anyUser.getUniqueName());
  }

  @Test
  // condition 3 from RSPAC-1740 - no incoming pgCrit explicitly set - use preference
  public void useIncomingPgCritResultsPerPage_Condition3() {
    final int nonDefaultPreference = 16;
    when(userMgr.getPreferenceForUser(anyUser, FORM_RESULTS_PER_PAGE))
        .thenReturn(new UserPreference(FORM_RESULTS_PER_PAGE, anyUser, nonDefaultPreference + ""));
    // a default value is generated in constructor, but not explicitly set from e.g. a request
    // parameter
    PaginationCriteria<?> pgCrit = new PaginationCriteria<>();

    paginationSettingsPreferences.updateResultsPerPageProperty(
        anyUser, pgCrit, FORM_RESULTS_PER_PAGE);
    condition3Assertions(nonDefaultPreference, pgCrit);
  }

  private void condition3Assertions(final int nonDefaultPreference, PaginationCriteria<?> pgCrit) {
    // we use preference value
    assertEquals(nonDefaultPreference, pgCrit.getResultsPerPage().intValue());
    // and the preference is not updated
    Mockito.verify(userMgr, never())
        .setPreference(FORM_RESULTS_PER_PAGE, nonDefaultPreference + "", anyUser.getUniqueName());
  }

  @Test
  // condition 3b from RSPAC-1740 - results per PAge is null
  public void useIncomingPgCritResultsPerPage_Condition3b() {
    final int nonDefaultPreference = 16;
    when(userMgr.getPreferenceForUser(anyUser, FORM_RESULTS_PER_PAGE))
        .thenReturn(new UserPreference(FORM_RESULTS_PER_PAGE, anyUser, nonDefaultPreference + ""));
    // resultsperpage is null - this is the case in workspace settings 1.56
    PaginationCriteria<?> pgCrit = new PaginationCriteria<>();
    pgCrit.setResultsPerPage(null);

    paginationSettingsPreferences.updateResultsPerPageProperty(
        anyUser, pgCrit, FORM_RESULTS_PER_PAGE);
    // we use preference value
    condition3Assertions(nonDefaultPreference, pgCrit);
  }
}

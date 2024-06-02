package com.researchspace.webapp.controller;

import static com.researchspace.model.preference.Preference.DELETED_RECORDS_RESULTS_PER_PAGE;
import static com.researchspace.model.preference.Preference.DIRECTORY_RESULTS_PER_PAGE;
import static com.researchspace.model.preference.Preference.FORM_RESULTS_PER_PAGE;
import static com.researchspace.model.preference.Preference.SHARED_RECORDS_RESULTS_PER_PAGE;
import static com.researchspace.model.preference.Preference.WORKSPACE_RESULTS_PER_PAGE;

import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.UserManager;
import java.util.EnumSet;
import org.apache.commons.lang.StringUtils;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Autowired;

public class PaginationSettingsPreferences {

  public PaginationSettingsPreferences() {}

  private @Autowired UserManager userManager;

  private static final EnumSet<Preference> listingResultsPerPagePrefs =
      EnumSet.of(
          WORKSPACE_RESULTS_PER_PAGE,
          DIRECTORY_RESULTS_PER_PAGE,
          FORM_RESULTS_PER_PAGE,
          SHARED_RECORDS_RESULTS_PER_PAGE,
          DELETED_RECORDS_RESULTS_PER_PAGE);

  void updateResultsPerPageProperty(
      User user, PaginationCriteria<?> pgCrit, Preference preference) {
    Validate.isTrue(
        listingResultsPerPagePrefs.contains(preference),
        String.format(
            " Preference must be one of %s ", StringUtils.join(listingResultsPerPagePrefs, ",")));
    // this is always non-null, will return default preference value if not set in
    // DB
    Integer resultsPerPageFromPref =
        userManager.getPreferenceForUser(user, preference).getValueAsNumber().intValue();
    Integer selectedResultsPerPage = pgCrit.getResultsPerPage();

    // condition numbers are from RSPAC-1740
    // if no incoming request param (ie null or default), use the preference
    if (selectedResultsPerPage == null || !pgCrit.isResultsPerPageExplicitlySet()) {
      // condition 3
      pgCrit.setResultsPerPage(resultsPerPageFromPref);
    } else if (!selectedResultsPerPage.equals(resultsPerPageFromPref)) {
      // if selected value is different from preference, update the preference
      // condition 2
      userManager.setPreference(preference, "" + selectedResultsPerPage, user.getUsername());
    } // else condition 1, use the incoming
  }
}

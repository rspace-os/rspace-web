package com.researchspace.service;

import com.researchspace.model.Organisation;
import com.researchspace.model.User;
import java.util.List;

public interface OrganisationManager {

  /**
   * @param term
   * @return
   */
  List<Organisation> getApprovedOrganisations(String term);

  /**
   * @param title
   * @return
   */
  boolean organisationExists(String title);

  /**
   * Internal method to check (organisationExists) if the organization already exists. If not, we
   * save as a new non-approved organization.
   *
   * @param user
   */
  void checkAndSaveNonApprovedOrganisation(User user);
}

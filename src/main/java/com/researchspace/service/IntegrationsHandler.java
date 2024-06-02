package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import java.util.Map;

/** Handler for IntegrationsController /RSPAC-838 */
public interface IntegrationsHandler {

  String SLACK_APP_NAME = "SLACK";
  String DATAVERSE_APP_NAME = "DATAVERSE";
  String ORCID_APP_NAME = "ORCID";
  String GITHUB_APP_NAME = "GITHUB";
  String FIGSHARE_APP_NAME = "FIGSHARE";
  String OWNCLOUD_APP_NAME = "OWNCLOUD";
  String NEXTCLOUD_APP_NAME = "NEXTCLOUD";
  String EVERNOTE_APP_NAME = "EVERNOTE";
  String EGNYTE_APP_NAME = "EGNYTE";
  String MSTEAMS_APP_NAME = "MSTEAMS";
  String ONBOARDING_APP_NAME = "ONBOARDING";
  String PROTOCOLS_IO_APP_NAME = "PROTOCOLS_IO";
  String PYRAT_APP_NAME = "PYRAT";
  String DMPTOOL_APP_NAME = "DMPTOOL";
  String CLUSTERMARKET_APP_NAME = "CLUSTERMARKET";
  String OMERO_APP_NAME = "OMERO";
  String JOVE_APP_NAME = "JOVE";
  String DRYAD_APP_NAME = "DRYAD";
  String ARGOS_APP_NAME = "ARGOS";
  String ZENODO_APP_NAME = "ZENODO";
  String DMPONLINE_APP_NAME = "DMPONLINE";

  String PYRAT_USER_TOKEN = "PYRAT_USER_TOKEN";
  String ZENODO_USER_TOKEN = "ZENODO_USER_TOKEN";
  String EGNYTE_DOMAIN_SETTING = "EGNYTE_DOMAIN";
  String ACCESS_TOKEN_SETTING = "ACCESS_TOKEN";

  /**
   * Boolean test as to whether the supplied <code>integrationName</code> is a valid name whose data
   * can be retrieved or updated
   *
   * @param integrationName
   * @return <code>true</code> if a valid name, <code>false</code> otherwise
   */
  boolean isValidIntegration(String integrationName);

  /**
   * Gets the {@link IntegrationInfo} enablement for the specified property name
   *
   * @param subject
   * @param integrationName
   * @return an {@link IntegrationInfo} for this {@code} integrationName{@code}
   * @throws IllegalArgumentException if<code>propertyName</code> is not recognised
   */
  IntegrationInfo getIntegration(User subject, String integrationName);

  /**
   * Updates a property to a new value
   *
   * @param subject
   * @param newInfo
   * @return the updated user preference
   * @throws IllegalArgumentException if<code>propertyName</code> in the supplied <code>newInfo
   *     </code> argument is not recognised
   */
  IntegrationInfo updateIntegrationInfo(User subject, IntegrationInfo newInfo);

  /**
   * Creates or updates AppConfigElementSet
   *
   * @param optionsId if provided, updates existing AppConfigElementSet
   * @param options options to save
   * @param appName used to update integration info cache
   * @param trustedOrigin some App options should be only updated from trusted sources, not by data
   *     provided by user
   * @param user
   */
  void saveAppOptions(
      Long optionsId,
      Map<String, String> options,
      String appName,
      boolean trustedOrigin,
      User user);

  /**
   * Deletes AppConfigElementSet
   *
   * @param optionsId id of AppConfigElementSet to delete
   * @param appName used to update integration info cache
   * @param subject
   */
  void deleteAppOptions(Long optionsId, String appName, User subject);

  /**
   * For internal use during application start-up, or to set up for tests. Should not be called in
   * regular usage by client code.
   */
  void init();
}

package com.researchspace.service;

import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.model.User;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Top level interface to encapsulate the logic required to establish if the API is available to the
 * given user or not.
 */
public interface ApiAvailabilityHandler {

  /**
   * Checks whether API is available for a given user and request uri, returning optional
   * explanatory message if it's not
   *
   * @param user
   * @return A {@link ServiceOperationResult} with optional message if result is <code>false</code>
   */
  ServiceOperationResult<String> isAvailable(User user, HttpServletRequest request);

  /**
   * Check if global 'api.available' system setting makes API available for a given user.
   *
   * @param user
   * @return A {@link ServiceOperationResult} with optional message if result is <code>false</code>
   */
  boolean isApiAvailableForUser(User user);

  boolean isOAuthAccessAllowed(User user);

  void assertInventoryAndDataciteEnabled(User user);

  boolean isInventoryAndDataciteEnabled(User user);

  /**
   * Asserts that inventory is available for the user and that the identifier integration for the
   * given setting type (IGSN or PIDINST) is configured and enabled, throwing {@link
   * UnsupportedOperationException} otherwise.
   */
  void assertInventoryAndIdentifierTypeEnabled(User user, InventorySettingType settingType);

  /**
   * @return whether inventory is available for the user and the identifier integration for the
   *     given setting type (IGSN or PIDINST) is configured and enabled
   */
  boolean isInventoryAndIdentifierTypeEnabled(User user, InventorySettingType settingType);

  boolean isDataCiteConnectorEnabled();

  boolean isInventoryAvailable(User user);

  void setDataCiteConnector(DataCiteConnector dataCiteConnector); // testing purposes
}

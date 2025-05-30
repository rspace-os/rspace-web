package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.views.ServiceOperationResult;
import javax.servlet.http.HttpServletRequest;

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

  boolean isDataCiteConnectorEnabled();

  boolean isInventoryAvailable(User user);
}

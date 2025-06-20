package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;

public class ApiAvailabilityHandlerImpl implements ApiAvailabilityHandler {

  @Autowired private SystemPropertyPermissionManager systemPropertyManager;
  @Autowired private DataCiteConnector dataCiteConnector;

  private static final ServiceOperationResult<String> enabledResult =
      new ServiceOperationResult<>("Enabled", true);
  private static final ServiceOperationResult<String> apiDisabledResult =
      new ServiceOperationResult<>(
          "Access to all API has been disabled by your administrator", false);
  private static final ServiceOperationResult<String> invDisabledResult =
      new ServiceOperationResult<>(
          "Access to Inventory has been disabled by your administrator", false);

  void setSystemPropertyManager(SystemPropertyPermissionManager systemPropertyManager) {
    this.systemPropertyManager = systemPropertyManager;
  }

  void setDataCiteConnector(DataCiteConnector dataciteConnector) {
    this.dataCiteConnector = dataciteConnector;
  }

  @Override
  public ServiceOperationResult<String> isAvailable(User user, HttpServletRequest request) {
    if (!isApiAvailableForUser(user)) {
      return apiDisabledResult;
    }
    if (isInventoryRequest(request)) {
      if (isInventoryAvailable(user)) {
        return enabledResult;
      } else {
        return invDisabledResult;
      }
    }
    return enabledResult;
  }

  @Override
  public boolean isApiAvailableForUser(User user) {
    return systemPropertyManager.isPropertyAllowed(user, SystemPropertyName.API_AVAILABLE);
  }

  @Override
  public boolean isOAuthAccessAllowed(User user) {
    return systemPropertyManager.isPropertyAllowed(
        user, SystemPropertyName.API_OAUTH_AUTHENTICATION);
  }

  @Override
  public void assertInventoryAndDataciteEnabled(User user) {
    assertInventoryAvailable(user);
    assertDataCiteConnectorEnabled();
  }

  @Override
  public boolean isInventoryAndDataciteEnabled(User user) {
    return isInventoryAvailable(user) && isDataCiteConnectorEnabled();
  }

  private void assertDataCiteConnectorEnabled() {
    if (!isDataCiteConnectorEnabled()) {
      throw new UnsupportedOperationException(
          "IGSN integration is not enabled on this RSpace instance.");
    }
  }

  private void assertInventoryAvailable(User user) {
    if (!isInventoryAvailable(user)) {
      throw new UnsupportedOperationException("Inventory is not enabled on this RSpace instance.");
    }
  }

  @Override
  public boolean isDataCiteConnectorEnabled() {
    return dataCiteConnector != null && dataCiteConnector.isDataCiteConfiguredAndEnabled();
  }

  @Override
  public boolean isInventoryAvailable(User user) {
    return systemPropertyManager.isPropertyAllowed(user, SystemPropertyName.INVENTORY_AVAILABLE);
  }

  private boolean isInventoryRequest(HttpServletRequest request) {
    return request != null && request.getRequestURL().toString().contains("inventory/v1");
  }
}

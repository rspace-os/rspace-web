package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;

public class ApiAvailabilityHandlerImpl implements ApiAvailabilityHandler {

  private @Autowired SystemPropertyPermissionManager systemPropertyManager;
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

  private boolean isInventoryAvailable(User user) {
    return systemPropertyManager.isPropertyAllowed(user, SystemPropertyName.INVENTORY_AVAILABLE);
  }

  private boolean isInventoryRequest(HttpServletRequest request) {
    return request != null && request.getRequestURL().toString().contains("inventory/v1");
  }
}

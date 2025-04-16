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
  private static final ServiceOperationResult<String> enableResult =
      new ServiceOperationResult<>("Enabled", true);
  private static final ServiceOperationResult<String> apiDisabled =
      new ServiceOperationResult<String>(
          "Access to all API has been disabled by your administrator", false);
  private static final ServiceOperationResult<String> invDisabled =
      new ServiceOperationResult<String>(
          "Access to Inventory has been disabled by your administrator", false);

  void setSystemPropertyManager(SystemPropertyPermissionManager systemPropertyManager) {
    this.systemPropertyManager = systemPropertyManager;
  }

  @Override
  public ServiceOperationResult<String> isAvailable(User user, HttpServletRequest request) {
    // rsinv-365
    if (isApiAvailable(user)) {
      if (!isInventoryRequest(request)) {
        return enableResult; // case 3
      } else {
        if (isInventoryAvailable(user)) {
          return enableResult; // 1
        } else {
          // case 2
          return invDisabled;
        }
      }
    } else {
      // case 4
      return apiDisabled;
    }
  }

  private boolean isApiAvailable(User user) {
    return systemPropertyManager.isPropertyAllowed(user, SystemPropertyName.API_AVAILABLE);
  }

  private boolean isInventoryAvailable(User user) {
    return systemPropertyManager.isPropertyAllowed(user, SystemPropertyName.INVENTORY_AVAILABLE);
  }

  private boolean isInventoryRequest(HttpServletRequest request) {
    return request != null && request.getRequestURL().toString().contains("inventory/v1");
  }
}

package com.researchspace.auth;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.User;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

public class MaintenanceLoginAuthorizer extends AbstractLoginAuthorizer implements LoginAuthorizer {

  public static final String REDIRECT_FOR_MAINTENANCE = "/public/maintenanceInProgress";
  public static final String MAINTENANCE_LOGIN_REQUEST_PARAM = "maintenanceLogin";

  @Autowired @Setter // for testing
  private MaintenanceManager maintenanceMgr;

  @Override
  public boolean isLoginPermitted(ServletRequest request, ServletResponse response, User subject)
      throws IOException {
    if (isMaintenanceInProgress()) {
      if (!containsMaintenanceLoginParameter(request)) {
        logoutAndRedirect(request, response, REDIRECT_FOR_MAINTENANCE);
        return false;
      }
    }
    return true;
  }

  private boolean isMaintenanceInProgress() {
    ScheduledMaintenance nextMaintenance = maintenanceMgr.getNextScheduledMaintenance();
    return nextMaintenance != null && !nextMaintenance.getCanUserLoginNow();
  }

  private boolean containsMaintenanceLoginParameter(ServletRequest request) {
    return request.getParameter(MAINTENANCE_LOGIN_REQUEST_PARAM) != null;
  }
}

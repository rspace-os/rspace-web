package com.researchspace.auth;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.User;
import java.io.IOException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.springframework.beans.factory.annotation.Autowired;

public class MaintenanceLoginAuthorizer extends AbstractLoginAuthorizer implements LoginAuthorizer {

  public static final String REDIRECT_FOR_MAINTENANCE = "/public/maintenanceInProgress";
  public static final String MAINTENANCE_LOGIN_REQUEST_PARAM = "maintenanceLogin";

  private @Autowired MaintenanceManager maintenanceMgr;

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

  /* ======================
   *     for tests
   * ===================== */

  public void setMaintenanceMgr(MaintenanceManager maintenanceMgr) {
    this.maintenanceMgr = maintenanceMgr;
  }
}

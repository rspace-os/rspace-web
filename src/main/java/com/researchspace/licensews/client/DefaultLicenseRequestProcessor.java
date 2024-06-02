package com.researchspace.licensews.client;

import com.researchspace.Constants;
import com.researchspace.licenseserver.model.License;
import com.researchspace.model.Role;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.service.LicenseRequestResult;

/** Default implementation that does not include admin roles in the seat calculations. */
public class DefaultLicenseRequestProcessor implements LicenseRequestProcessor {

  @Override
  public LicenseRequestResult processLicenseRequest(
      int seatsRequested, Role role, UserStatistics userStats, License license) {
    int currEnabledUsersInRole = 0;
    switch (role.getName()) {
      case Constants.ADMIN_ROLE:
        currEnabledUsersInRole = userStats.getTotalEnabledRSpaceAdmins();
        break;
      case Constants.SYSADMIN_ROLE:
        currEnabledUsersInRole = userStats.getTotalEnabledSysAdmins();
        break;
      default:
        currEnabledUsersInRole =
            userStats.getTotalEnabledUsers()
                - userStats.getTotalEnabledSysAdmins()
                - userStats.getTotalEnabledRSpaceAdmins();
    }
    int numAvailable = license.permits(role.getName(), currEnabledUsersInRole);
    if (numAvailable >= seatsRequested) {
      return new LicenseRequestResult(true, numAvailable, true);
    } else {
      return new LicenseRequestResult(false, numAvailable, true);
    }
  }
}

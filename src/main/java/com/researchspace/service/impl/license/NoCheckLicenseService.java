package com.researchspace.service.impl.license;

import com.researchspace.licenseserver.model.License;
import com.researchspace.model.Role;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.service.LicenseRequestResult;
import com.researchspace.service.RemoteLicenseService;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

/**
 * A permissive {@link RemoteLicenseServer} implementation which has no interaction with any actual
 * license server and returns true from isLicenseActive() therefore permitting all actions in the
 * system which check for an active license, without actually needing a license.
 */
public class NoCheckLicenseService implements RemoteLicenseService {
  @Override
  public LicenseRequestResult requestUserLicenses(int userCount, Role role) {
    return new LicenseRequestResult(true, Integer.MAX_VALUE, true);
  }

  @Override
  public boolean isLicenseActive() {
    return true;
  }

  @Override
  public Optional<String> getServerUniqueId() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getCustomerName() {
    return Optional.empty();
  }

  @Override
  public boolean uploadServerData(String serverData, String macCode, int numUsers) {
    return true;
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public License getLicense() {
    Date now = new Date();
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.YEAR, 1);
    return new License(now, cal.getTime(), "no-check-license", Integer.MAX_VALUE);
  }

  @Override
  public int getAvailableSeatCount(UserStatistics userStats) {
    return Integer.MAX_VALUE;
  }
}

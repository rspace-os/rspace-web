package com.researchspace.license;

import com.researchspace.licenseserver.model.License;
import com.researchspace.model.Role;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.service.LicenseRequestResult;
import com.researchspace.service.LicenseService;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

/** Always returns an inactive license that prevents users being created. */
public class InactiveLicenseTestService implements LicenseService {
  @Override
  public LicenseRequestResult requestUserLicenses(int userCount, Role role) {
    return new LicenseRequestResult(false, 0, true);
  }

  @Override
  public boolean isLicenseActive() {
    return false;
  }

  @Override
  public Optional<String> getServerUniqueId() {
    return Optional.of("TEST");
  }

  @Override
  public Optional<String> getCustomerName() {
    return Optional.of("TEST_CUSTOMER");
  }

  @Override
  public License getLicense() {
    Calendar cal = Calendar.getInstance();
    Date now = cal.getTime();
    cal.add(Calendar.YEAR, 1);
    Date expires = cal.getTime();
    return new License(now, expires, "12345", 0);
  }

  @Override
  public int getAvailableSeatCount(UserStatistics userStats) {
    return 0;
  }
}

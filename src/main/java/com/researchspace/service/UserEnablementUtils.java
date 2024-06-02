package com.researchspace.service;

import com.researchspace.model.Role;
import com.researchspace.model.User;

public interface UserEnablementUtils {

  void notifyByEmailUserEnablementChange(User user, User systemUser, boolean status);

  void auditUserEnablementChangeEvent(boolean status, User user);

  void checkLicenseForUserInRole(int numSeatsRequested, Role role);

  void setLicenseService(LicenseService licenseService);
}

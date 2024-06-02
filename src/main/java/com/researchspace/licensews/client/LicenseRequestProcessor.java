package com.researchspace.licensews.client;

import com.researchspace.licenseserver.model.License;
import com.researchspace.model.Role;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.service.LicenseRequestResult;

/**
 * Interface for processing whether a request for <code>userCount</code> seats for the specified
 * Role can be accepted
 */
public interface LicenseRequestProcessor {

  /**
   * Consults a {@link License} to determine if the seat request can be met
   *
   * @param seatsRequested
   * @param role the Role of the seats
   * @param userStats current user details
   * @param license A {@link License}
   * @return A {@link LicenseRequestResult} encapsulating the results of the request,
   */
  LicenseRequestResult processLicenseRequest(
      int seatsRequested, Role role, UserStatistics userStats, License license);
}

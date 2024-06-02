package com.researchspace.service;

import com.researchspace.licenseserver.model.License;
import com.researchspace.model.Role;
import com.researchspace.model.views.UserStatistics;
import java.util.Optional;

/**
 * Main interface to query license for validity of operations. Two default implementations are
 * provided that either accept or deny all requests. JUnit tests inject {@link
 * com.researchspace.service.impl.license.NoCheckLicenseService} so that tests need not concern
 * themselves with licensing.
 *
 * <p>For running the application in 'run' or 'prod' profiles, a Web service implementation is
 * provided. The location and key to access the license server is defined in the relevant
 * deployment.properties file.
 */
public interface LicenseService {

  /**
   * Request to add <code>userCount</code> new users to RSpace
   *
   * @param userCount
   * @param role The role of the users to create.
   * @return A {@link LicenseRequestResult}
   */
  LicenseRequestResult requestUserLicenses(int userCount, Role role);

  /**
   * Boolean test for whether this server's RSpace license is active. <br>
   * This does not test if new users can be added, just if the license is valid.
   *
   * @return <code>true</code> if license is valid, <code>false</code> otherwise.
   */
  boolean isLicenseActive();

  /**
   * @return unique id of this RSpace server as set in license, if available
   */
  Optional<String> getServerUniqueId();

  /**
   * @return name of customer's organisation RSpace as set in license, if available
   */
  Optional<String> getCustomerName();

  /**
   * Gets the License object, retrieving from cache if possible
   *
   * @return a {@link License}
   */
  License getLicense();

  /**
   * Gets the License object from the license server, replacing cached version if available. Default
   * interface implementation does nothing and always reutns <code>true</code>.
   *
   * @return <code>true</code> if license refresh was successful, <code>false</code> if not.
   */
  default boolean forceRefreshLicense() {
    return true;
  }

  /**
   * Calculates the available seats left on the license
   *
   * @param userStats
   * @return The number of users that can be added or enabled
   */
  int getAvailableSeatCount(UserStatistics userStats);
}

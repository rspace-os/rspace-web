package com.researchspace.service;

/** Defines methods relevant to a web-service based connection to a license server. */
public interface RemoteLicenseService extends LicenseService {
  /**
   * @param serverData - server environment info
   * @param macCode - macID
   * @param numUsers - current number of users
   * @return <code>true</code> if web service calls was successful, false otherwise.
   */
  boolean uploadServerData(String serverData, String macCode, int numUsers);

  /** Makes simple test to /health endpoint that server is up. */
  boolean isAvailable();
}

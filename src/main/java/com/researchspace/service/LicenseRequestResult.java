package com.researchspace.service;

/** Encapsulates information resulting from a request for licenses */
public class LicenseRequestResult {

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + availableSeats;
    result = prime * result + (licenseServerAvailable ? 1231 : 1237);
    result = prime * result + (requestOK ? 1231 : 1237);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    LicenseRequestResult other = (LicenseRequestResult) obj;
    if (availableSeats != other.availableSeats) return false;
    if (licenseServerAvailable != other.licenseServerAvailable) return false;
    if (requestOK != other.requestOK) return false;
    return true;
  }

  private final boolean requestOK;

  private final int availableSeats;

  private boolean licenseServerAvailable;

  public static final int CANNOT_DETERMINE_AVAILABLE_SEATS = -1;

  /**
   * Gets a result signifying that the server could not return a result - either the server was
   * down, or the API-key was incorrect.
   *
   * @return
   */
  public static LicenseRequestResult getServerUnavailableResult() {
    return new LicenseRequestResult(false, CANNOT_DETERMINE_AVAILABLE_SEATS, false);
  }

  /**
   * @return <code>true</code> if license server is online, <code>false</code> otherwise
   */
  public boolean isLicenseServerAvailable() {
    return licenseServerAvailable;
  }

  /**
   * Returns <code>true</code> if <code>isLicenseServerAvailable() == true</code>, and the license
   * request could be met in its entirety, <code>false</code> otherwise.
   *
   * @return
   */
  public final boolean isRequestOK() {
    return requestOK;
  }

  /**
   * Gets the available seat count. If <code>isRequestOK() == true</code>, then this number will
   * always be greater or equal to the number of seats requested.
   *
   * @return The available seats, or CANNOT_DETERMINE_AVAILABLE_SEATS if license server is offline.
   */
  public final int getAvailableSeats() {
    if (!licenseServerAvailable) {
      return CANNOT_DETERMINE_AVAILABLE_SEATS;
    }
    return availableSeats;
  }

  /**
   * @param requestOK -<code>true</code> if request could be met, <code>false</code> otherwise.
   * @param availableSeats - must be >= 0 if requestOK = true
   * @param licenseServerAvailable - whether license server was available or not.
   */
  public LicenseRequestResult(
      boolean requestOK, int availableSeats, boolean licenseServerAvailable) {
    super();
    this.requestOK = requestOK;
    this.availableSeats = availableSeats;
    this.licenseServerAvailable = licenseServerAvailable;
  }
}

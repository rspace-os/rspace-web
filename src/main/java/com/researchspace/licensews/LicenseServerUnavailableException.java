package com.researchspace.licensews;

/** License Server is unavailable. */
public class LicenseServerUnavailableException extends LicenseException {

  private static final long serialVersionUID = 1L;

  public LicenseServerUnavailableException() {
    this("License server couldn't be accessed!");
  }

  public LicenseServerUnavailableException(String msg) {
    super(msg);
  }
}

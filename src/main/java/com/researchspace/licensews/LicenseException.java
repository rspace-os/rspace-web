package com.researchspace.licensews;

/** General supertype of all license exceptions */
public class LicenseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LicenseException(String message) {
    super(message);
  }

  private String detail;

  /**
   * @return the detail
   */
  public String getDetail() {
    return detail;
  }

  /**
   * @param detail the detail to set
   */
  public void setDetail(String detail) {
    this.detail = detail;
  }
}

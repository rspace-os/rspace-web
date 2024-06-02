package com.researchspace.properties;

/**
 * Adds mutable methods to set properties in test environments. <br>
 * Production code should use IPropertyHolder interface only as this enforces read-only properties.
 */
public interface IMutablePropertyHolder extends IPropertyHolder {

  void setUserSignup(String booleanValue);

  /* ==============
   * test methods
   */

  /**
   * @param standalone the standalone property to set. <br>
   *     For testing purposes only - should be injected from the environment.
   */
  void setStandalone(String standalone);

  /**
   * @param cloud the cloud property to set. <br>
   *     For testing purposes only - should be injected from the environment.
   */
  void setCloud(String cloud);

  /**
   * @param netFileStoresEnabled 'true' if network file stores are enabled. <br>
   *     For testing purposes only - should be injected from the environment.
   */
  void setNetFileStoresEnabled(String netFileStoresEnabled);

  void setBannerImagePath(String path);

  void setLdapEnabled(String enabled);

  void setRoREnabled(String enabled);

  void setLdapAuthenticationEnabled(String enabled);

  void setLdapSidVerificationEnabled(String enabled);

  void setMaxUploadSize(long maxSize);

  void setProfileEmailEditable(boolean editable);

  void setPicreateGroupOnSignupEnabled(Boolean enabled);

  void setSSOSelfDeclarePiEnabled(Boolean enabled);

  void setProfileNameEditable(boolean editable);

  void setExportFolderLocation(String absolutePath);

  void setMsOfficeEnabled(boolean msOfficeEnabled);

  void setCollaboraEnabled(boolean collaboraEnabled);

  void setUserSignupCode(String userSignupCode);

  void setLicenseExceededCustomMessage(String message);

  void setCustomLoginContent(String customLoginContent);

  void setMinUsernameLength(Integer minUsernameLength);

  void setJoveApiUrl(String joveApiUrl);

  void setJoveApiKey(String joveApiKey);
}

package com.researchspace.dao.customliquibaseupdates;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("SysPropertyProviderRSPAC861")
public class SysPropertyProviderRSPAC861 {
  @Value("${dropbox.enabled}")
  private String dropboxEnabled;

  @Value("${box.enabled}")
  private String boxEnabled;

  @Value("${ecat.enabled}")
  private String ecatEnabled;

  @Value("${googledrive.enabled}")
  private String googleDriveEnabled;

  @Value("${onedrive.enabled}")
  private String oneDriveEnabled;

  @Value("${mendeley.enabled}")
  private String mendeleyEnabled;

  String getDropboxEnabled() {
    return dropboxEnabled;
  }

  void setDropboxEnabled(String dropboxEnabled) {
    this.dropboxEnabled = dropboxEnabled;
  }

  String getBoxEnabled() {
    return boxEnabled;
  }

  void setBoxEnabled(String boxEnabled) {
    this.boxEnabled = boxEnabled;
  }

  String getEcatEnabled() {
    return ecatEnabled;
  }

  void setEcatEnabled(String ecatEnabled) {
    this.ecatEnabled = ecatEnabled;
  }

  String getGoogleDriveEnabled() {
    return googleDriveEnabled;
  }

  void setGoogleDriveEnabled(String googleDriveEnabled) {
    this.googleDriveEnabled = googleDriveEnabled;
  }

  String getOneDriveEnabled() {
    return oneDriveEnabled;
  }

  void setOneDriveEnabled(String oneDriveEnabled) {
    this.oneDriveEnabled = oneDriveEnabled;
  }

  String getMendeleyEnabled() {
    return mendeleyEnabled;
  }

  void setMendeleyEnabled(String mendeleyEnabled) {
    this.mendeleyEnabled = mendeleyEnabled;
  }
}

package com.researchspace.dao.customliquibaseupdates.v26;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Helper class used to read deployment properties connected to netfilestores, so the inital entry
 * in NfsFileSystems table can be created.
 */
@Component("nfsFileSystemImporterDeploymentPropertyProvider")
public class NfsFileSystemDeploymentPropertyProvider {

  @Value("${netfilestores.enabled}")
  private String fileStoresEnabled;

  @Value("${netfilestores.name}")
  private String fileStoresName;

  @Value("${netfilestores.url}")
  private String serverUrl;

  @Value("${netfilestores.client.type}")
  private String clientTypeString;

  @Value("${netfilestores.auth.type}")
  private String authTypeString;

  @Value("${netfilestores.client.samba.domain}")
  private String sambaUserDomain;

  @Value("${netfilestores.client.sftp.server.pubKey.rsasha}")
  private String sftpServerPubKey;

  @Value("${netfilestores.auth.pubKey.registration.dialog.url}")
  private String pubKeyRegistrationDialogUrl;

  public String getFileStoresEnabled() {
    return fileStoresEnabled;
  }

  public void setFileStoresEnabled(String fileStoresEnabled) {
    this.fileStoresEnabled = fileStoresEnabled;
  }

  public String getFileStoresName() {
    return fileStoresName;
  }

  public void setFileStoresName(String fileStoresName) {
    this.fileStoresName = fileStoresName;
  }

  public String getServerUrl() {
    return serverUrl;
  }

  public void setServerUrl(String serverUrl) {
    this.serverUrl = serverUrl;
  }

  public String getClientTypeString() {
    return clientTypeString;
  }

  public void setClientTypeString(String clientTypeString) {
    this.clientTypeString = clientTypeString;
  }

  public String getAuthTypeString() {
    return authTypeString;
  }

  public void setAuthTypeString(String authTypeString) {
    this.authTypeString = authTypeString;
  }

  public String getSambaUserDomain() {
    return sambaUserDomain;
  }

  public void setSambaUserDomain(String sambaUserDomain) {
    this.sambaUserDomain = sambaUserDomain;
  }

  public String getSftpServerPubKey() {
    return sftpServerPubKey;
  }

  public void setSftpServerPubKey(String sftpServerPubKey) {
    this.sftpServerPubKey = sftpServerPubKey;
  }

  public String getPubKeyRegistrationDialogUrl() {
    return pubKeyRegistrationDialogUrl;
  }

  public void setPubKeyRegistrationDialogUrl(String pubKeyRegistrationDialogUrl) {
    this.pubKeyRegistrationDialogUrl = pubKeyRegistrationDialogUrl;
  }
}

package com.researchspace.dao.customliquibaseupdates.v26;

import com.researchspace.dao.NfsDao;
import com.researchspace.dao.customliquibaseupdates.AbstractCustomLiquibaseUpdater;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemOption;
import java.util.List;
import liquibase.database.Database;

/**
 * Creates initial NfsFileSystem object out of deployment properties and connects existing user file
 * stores to it.
 *
 * <p>This way customers who had external store configured through deployment properties can still
 * use it.
 */
public class NfsFileSystemImporter extends AbstractCustomLiquibaseUpdater {

  private NfsDao nfsDao;

  private NfsFileSystemDeploymentPropertyProvider propertyProvider;

  @Override
  protected void addBeans() {
    nfsDao = context.getBean("nfsDao", NfsDao.class);
    propertyProvider =
        context.getBean(
            "nfsFileSystemImporterDeploymentPropertyProvider",
            NfsFileSystemDeploymentPropertyProvider.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Import of initial net file system is complete.";
  }

  protected void doExecute(Database database) {
    logger.info("executing liquibase update");

    String clientTypeString = propertyProvider.getClientTypeString();
    String authTypeString = propertyProvider.getAuthTypeString();

    if (!"samba".equals(clientTypeString) && !"sftp".equals(clientTypeString)) {
      logger.info(
          "client type " + clientTypeString + " not recognised, skipping NfsFileSystem import.");
      return;
    }
    if (!"password".equals(authTypeString) && !"pubKey".equals(authTypeString)) {
      logger.info(
          "auth type " + authTypeString + " not recognised, skipping NfsFileSystem import.");
      return;
    }

    NfsFileSystem importedFileSystem = importFileSystem();
    updateFileSystemInExistingFileStores(importedFileSystem);

    logger.info("NfsFileSystem imported fine");
  }

  private NfsFileSystem importFileSystem() {
    NfsClientType clientType = NfsClientType.fromString(propertyProvider.getClientTypeString());
    NfsAuthenticationType authenticationType =
        NfsAuthenticationType.fromString(propertyProvider.getAuthTypeString());

    NfsFileSystem fileSystem = new NfsFileSystem();
    fileSystem.setDisabled(!Boolean.parseBoolean(propertyProvider.getFileStoresEnabled()));
    fileSystem.setName(propertyProvider.getFileStoresName());
    fileSystem.setUrl(propertyProvider.getServerUrl());

    fileSystem.setClientType(clientType);
    if (NfsClientType.SAMBA.equals(clientType)) {
      fileSystem.setClientOption(
          NfsFileSystemOption.SAMBA_DOMAIN, propertyProvider.getSambaUserDomain());
    } else if (NfsClientType.SFTP.equals(clientType)) {
      fileSystem.setClientOption(
          NfsFileSystemOption.SFTP_SERVER_PUBLIC_KEY, propertyProvider.getSftpServerPubKey());
    }

    fileSystem.setAuthType(authenticationType);
    if (NfsAuthenticationType.PUBKEY.equals(authenticationType)) {
      fileSystem.setAuthOption(
          NfsFileSystemOption.PUBLIC_KEY_REGISTRATION_DIALOG_URL,
          propertyProvider.getPubKeyRegistrationDialogUrl());
    }

    nfsDao.saveNfsFileSystem(fileSystem);

    logger.info("initial file system imported with id: " + fileSystem.getId());

    return fileSystem;
  }

  private void updateFileSystemInExistingFileStores(NfsFileSystem importedFileSystem) {

    List<NfsFileStore> allFileStores = nfsDao.getFileStores();
    if (allFileStores == null || allFileStores.isEmpty()) {
      logger.info("no file stores to update");
    } else {
      for (NfsFileStore fileStore : allFileStores) {
        fileStore.setFileSystem(importedFileSystem);
        nfsDao.saveNfsFileStore(fileStore);
      }
      logger.info(
          allFileStores.size()
              + " pre-existing file store(s) point to newly imported file system now");
    }
  }

  /*
   * for tests
   */
  protected void setPropertyProvider(NfsFileSystemDeploymentPropertyProvider propertyProvider) {
    this.propertyProvider = propertyProvider;
  }
}

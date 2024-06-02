package com.researchspace.dao.customliquibaseupdates.v27;

import com.researchspace.dao.NfsDao;
import com.researchspace.dao.customliquibaseupdates.AbstractCustomLiquibaseUpdater;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import java.util.List;
import liquibase.database.Database;

/**
 * Links orphaned file stores to first active File System, or to only existing one if none active is
 * found. If there is no File System at all, but there are orphaned filestores, then a default File
 * System is created.
 *
 * <p>This is to mitigate the problem with 26.0-26.3 releases where newly created filestores were
 * not connected to any File System.
 */
public class NfsFileStoreToFileSystemLinker extends AbstractCustomLiquibaseUpdater {

  protected static final String FILE_SYSTEM_DEFAULT_NAME = "<default>";

  private NfsDao nfsDao;

  private NfsFileSystem defaultFileSystem;

  @Override
  protected void addBeans() {
    nfsDao = context.getBean("nfsDao", NfsDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Linking filestores operation is complete.";
  }

  protected void doExecute(Database database) {
    logger.info("executing liquibase update");

    linkOrphanedFileStoresToDefaultFileSystem();

    logger.info("filestore linking finished fine");
  }

  private void linkOrphanedFileStoresToDefaultFileSystem() {

    List<NfsFileStore> allFileStores = nfsDao.getFileStores();
    if (allFileStores == null || allFileStores.isEmpty()) {
      logger.info("no file stores to update");
    } else {
      for (NfsFileStore fileStore : allFileStores) {
        if (fileStore.getFileSystem() == null) {
          fileStore.setFileSystem(getDefaultFileSystem());
          nfsDao.saveNfsFileStore(fileStore);
        }
      }
    }
  }

  /**
   * this method may have a side effect of creating new dummy file system (if there were no file
   * sytems before), so it should only be called if necessary.
   *
   * @returns 1) first enabled file system, or 2) first configured file system, if none is enabled,
   *     or 3) newly added default file system, if there was no pre-existing file systems on a
   *     database. sysadmin may update the dummy file system configuration later
   * @return
   */
  protected NfsFileSystem getDefaultFileSystem() {
    if (defaultFileSystem == null) {
      List<NfsFileSystem> fileSystems = nfsDao.getFileSystems();
      if (fileSystems == null || fileSystems.isEmpty()) {
        defaultFileSystem = createDefaultFileSystem();
      } else {
        for (NfsFileSystem system : fileSystems) {
          if (system.isEnabled()) {
            defaultFileSystem = system;
            break;
          }
        }
        if (defaultFileSystem == null) {
          defaultFileSystem = fileSystems.get(0);
        }
      }
      logger.info("asked for default filestore, returning: " + defaultFileSystem.getId());
    }
    return defaultFileSystem;
  }

  private NfsFileSystem createDefaultFileSystem() {

    NfsFileSystem fileSystem = new NfsFileSystem();
    fileSystem.setDisabled(true);
    fileSystem.setName(FILE_SYSTEM_DEFAULT_NAME);
    nfsDao.saveNfsFileSystem(fileSystem);

    return fileSystem;
  }

  /*
   * ========================
   * for testing
   */
  protected void setNfsDao(NfsDao nfsDao) {
    this.nfsDao = nfsDao;
  }
}

package com.researchspace.service.impl;

import com.researchspace.files.service.InternalFileStore;
import com.researchspace.service.FileStoreMetaManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Detects if the file store root has been updated. Sets this as current, and if the file store is
 * new, creates a new filestore root. <br>
 * This needs to be called on initial deployment before creating sysadmin user in 'prod' profile.
 */
@Slf4j
public class FileStoreRootDetector extends AbstractAppInitializor {

  private @Autowired InternalFileStore fstore;
  private @Autowired FileStoreMetaManager fileStoreMeta;

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    setupInternalFileStore();
    warnIfFilePropertiesWithoutRoot();
  }

  /**
   * Startup diagnostic: warn if any FileProperty has no associated FileStoreRoot. Never aborts
   * startup - any error is logged and swallowed (matching the warn-only Liquibase check it
   * replaced).
   */
  private void warnIfFilePropertiesWithoutRoot() {
    try {
      long orphaned = fileStoreMeta.countFilePropertiesWithoutRoot();
      if (orphaned > 0) {
        log.warn(
            "There are {} FileProperty row(s) not associated with a FileStoreRoot (root_id is"
                + " null).",
            orphaned);
      }
    } catch (Exception e) {
      log.warn("Could not run the FileProperty/FileStoreRoot integrity diagnostic", e);
    }
  }

  public void onInitialAppDeployment() {
    setupInternalFileStore();
  }

  private void setupInternalFileStore() {
    fstore.setupInternalFileStoreRoot();
    log.info("Current filestore root is " + fstore.getCurrentFileStoreRoot().getFileStoreRoot());
  }
}

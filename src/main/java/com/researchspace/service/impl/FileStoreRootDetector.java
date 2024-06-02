package com.researchspace.service.impl;

import com.researchspace.files.service.ExternalFileStore;
import com.researchspace.files.service.InternalFileStore;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.service.FileStoreMetaManager;
import java.util.Date;
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
  private @Autowired(required = false) ExternalFileStore extFs;
  private @Autowired FileStoreMetaManager fileStoreMeta;

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    setupInternalFileStore();
    setupExtFileStore();
  }

  public void onInitialAppDeployment() {
    setupInternalFileStore();
    setupExtFileStore();
  }

  private void setupExtFileStore() {
    // set up extFS if not already set
    if (extFs != null) {
      log.info("RSpace starting with External FileStore");
      FileStoreRoot extRoot = fileStoreMeta.getCurrentFileStoreRoot(true);
      if (extRoot == null) {
        log.info("Setting external filestoreRoot to {}", extFs.getFileStoreRoot());
        FileStoreRoot fsr = new FileStoreRoot(extFs.getFileStoreRoot());
        fsr.setCreationDate(new Date());
        fsr.setExternal(true);
        fsr.setCurrent(true);
        fileStoreMeta.saveFileStoreRoot(fsr);
      } else {
        log.info("External FS Root already set for {}", extRoot.getFileStoreRoot());
      }
    } else {
      log.info("RSpace starting with Local FileStore only");
    }
  }

  private void setupInternalFileStore() {
    fstore.setupInternalFileStoreRoot();
    log.info("Current filestore root is " + fstore.getCurrentFileStoreRoot().getFileStoreRoot());
  }
}

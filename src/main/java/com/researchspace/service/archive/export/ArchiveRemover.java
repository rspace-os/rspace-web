package com.researchspace.service.archive.export;

import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import java.io.File;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class ArchiveRemover {

  private @Autowired IPropertyHolder properties;
  private @Autowired ExportRemovalPolicy removalPolicy;
  private @Autowired CommunicationManager commMgr;

  public void setRemovalPolicy(ExportRemovalPolicy removalPolicy) {
    this.removalPolicy = removalPolicy;
  }

  public void removeOldArchives(ArchiveExportServiceManager archiver) {
    log.info("Scanning archives for files to delete..");
    File exportFolderRoot = new File(properties.getExportFolderLocation());
    if (!isValidArchiveLocation(exportFolderRoot)) {
      throw new IllegalStateException(
          "Can't find archive root at ["
              + properties.getExportFolderLocation()
              + "] to delete from.");
    }
    List<ArchivalCheckSum> toProcess = archiver.getCurrentArchiveMetadatas();
    log.info("There are {} archives eligible for deletion", toProcess.size());
    for (ArchivalCheckSum acs : toProcess) {
      if (removalPolicy.removeExport(acs)) {
        File toRemove = new File(exportFolderRoot, acs.getZipName());
        try {
          forceDelete(toRemove);
          log.info("Removing archive file : {}", toRemove.getAbsolutePath());
          acs.setDownloadTimeExpired(true);
          archiver.save(acs);
          log.info(acs.getZipName() + "marked as deleted");
          commMgr.systemNotify(
              NotificationType.PROCESS_COMPLETED,
              String.format(" The export %s has been deleted from the server", acs.getZipName()),
              acs.getExporter().getUsername(),
              true);
        } catch (IOException e) {
          log.warn(
              "Couldn't find file [{}] to delete - maybe was already deleted?. Not notifying user."
                  + " Not marking ArchivalCheckSum {}  as deleted",
              toRemove.getAbsolutePath(),
              acs.getUid());
        }
      }
    }
  }

  private boolean isValidArchiveLocation(File exportFolderRoot) {
    return exportFolderRoot.exists() && exportFolderRoot.isDirectory();
  }

  /*
   * in method to allow for tests to override and test exception handling.
   */
  void forceDelete(File toRemove) throws IOException {
    FileUtils.forceDelete(toRemove);
  }
}

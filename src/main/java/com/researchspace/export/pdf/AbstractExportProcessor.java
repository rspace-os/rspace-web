package com.researchspace.export.pdf;

import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.service.FileDuplicateStrategy;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Helper methods used by all exporters
 */
class AbstractExportProcessor {

  static Logger log = LoggerFactory.getLogger(AbstractExportProcessor.class);

  FileProperty saveToFileStore(
      File finalExportFile, List<File> tmpExportedFiles, FileStore fileStore, FileProperty fp)
      throws IOException {
    if (finalExportFile.exists()) {
      URI uri = fileStore.save(fp, finalExportFile, FileDuplicateStrategy.AS_NEW);
      if (uri != null) {
        if (finalExportFile.delete()) {
          log.info("Deleted final export file: {}", finalExportFile);
        } else {
          log.warn("Cannot Delete file {}", finalExportFile);
        }
      }
    }
    deleteTempExportFiles(tmpExportedFiles);
    return fp;
  }

  private void deleteTempExportFiles(List<File> tmpExportedFiles) {
    // may need clear out other pdf elements.
    for (File tempPdf : tmpExportedFiles) {
      try {

        if (tempPdf.exists()) {
          if (tempPdf.delete()) {
            log.info("Deleted file: {}", tempPdf);
          } else {
            log.warn("Cannot delete file: {}", tempPdf);
          }
        }
      } catch (Exception e) {
        log.warn(e.toString());
      }
    }
  }
}

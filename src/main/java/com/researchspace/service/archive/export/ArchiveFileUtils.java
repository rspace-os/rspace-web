package com.researchspace.service.archive.export;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ArchiveFileUtils {

  private static final Logger log = LoggerFactory.getLogger(ArchiveFileUtils.class);

  protected static boolean writeToFile(String outName, byte[] buf, File recordFolder) {
    if (buf == null || buf.length < 2) return false;

    boolean success = true;
    File fx = new File(recordFolder, outName);
    try (FileOutputStream fOS = new FileOutputStream(fx)) {
      fOS.write(buf);
      fOS.flush();
    } catch (IOException ex) {
      log.warn("Error on output: " + ex.getMessage());
      success = false;
    }
    return success;
  }
}

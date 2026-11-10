package com.researchspace.service.impl;

import jakarta.annotation.PostConstruct;
import java.io.File;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public class LoggingInitializer {

  private static final Logger log = LoggerFactory.getLogger(LoggingInitializer.class);
  private static final String defaultLoggingDir = ".";

  @Value("${logging.dir}")
  private String loggingDir;

  @PostConstruct
  public void init() {
    File loggingDirFile;

    if (StringUtils.isEmpty(loggingDir)) {
      loggingDir = defaultLoggingDir;
    }
    loggingDirFile = new File(loggingDir);

    // Fall back to the default unless the path is, or can be created as, a writable directory.
    if (!loggingDir.equals(defaultLoggingDir) && !isWritableDirectory(loggingDirFile)) {
      log.warn("logging.dir '{}' is not a writable directory; logging to '.'", loggingDir);
      loggingDir = defaultLoggingDir;
      loggingDirFile = new File(loggingDir);
    }

    if (!loggingDir.equals(defaultLoggingDir)) {
      reconfigureLogging(loggingDirFile);
    }
  }

  private static boolean isWritableDirectory(File dir) {
    return (dir.isDirectory() || dir.mkdirs()) && dir.canWrite();
  }

  /**
   * Custom log file location can be set by users via deployment.properties files. loggingDir is
   * loaded after logging initialisation begins, therefore once the @Value has been loaded, the
   * directory gets set as the system property `loggingDirectory`, which log4j2.xml and
   * log4j2-dev.xml prepend to the file appender paths when .reconfigure() is called.
   * `loggingDirectory` defaults to `.` (which is project root in dev or tomcat working directory in
   * prod) when not set.
   *
   * @param loggingDirFile the new log file directory
   */
  private void reconfigureLogging(File loggingDirFile) {
    System.setProperty("loggingDirectory", loggingDirFile.getAbsolutePath());
    ((LoggerContext) LogManager.getContext(false)).reconfigure();
  }

  String getLoggingDir() {
    return loggingDir;
  }

  void setLoggingDir(String loggingDir) {
    this.loggingDir = loggingDir;
  }
}

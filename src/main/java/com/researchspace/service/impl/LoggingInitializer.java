package com.researchspace.service.impl;

import java.io.File;
import javax.annotation.PostConstruct;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.springframework.beans.factory.annotation.Value;

public class LoggingInitializer {

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

    if (!loggingDirFile.exists()) {
      // revert to default if couldn't create
      if (!loggingDirFile.mkdirs()) {
        loggingDir = defaultLoggingDir;
        loggingDirFile = new File(loggingDir);
      }
    }

    if (!loggingDir.equals(defaultLoggingDir)) {
      reconfigureLogging(loggingDirFile);
    }
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

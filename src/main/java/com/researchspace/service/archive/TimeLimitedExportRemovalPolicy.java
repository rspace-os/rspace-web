package com.researchspace.service.archive;

import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.service.archive.export.ExportRemovalPolicy;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * Default implementation that uses a set duration to determine if an archive should be deleted or
 * not.
 *
 * <p>This can be via a property 'archive.folder.storagetime' in deployment.properties, or set
 * directly.
 *
 * <p>If neither is set, the default is 24 hrs. <br>
 * This class is instantiated as a Spring Bean in BaseConfig class to enable autowiring of
 * properties
 */
public class TimeLimitedExportRemovalPolicy implements ExportRemovalPolicy {
  private Logger log = LoggerFactory.getLogger(TimeLimitedExportRemovalPolicy.class);

  @Value("${archive.folder.storagetime}")
  private String storageTimeProperty;

  private Integer storageTime;

  /** Default length in hours to store archives for. */
  public static final int DEFAULT_LENGTH = 24;

  @Override
  public boolean removeExport(ArchivalCheckSum archive) {
    final long time = 60 * 60 * 1000;
    long now = new Date().getTime();
    long archiveGenerated = archive.getArchivalDate();
    return (now - archiveGenerated) > getStorageTime() * time;
  }

  /**
   * Setter for time in hours to store an archive for
   *
   * @param storageTime A non-null positive {@link Integer}
   */
  public void setStorageTime(Integer storageTime) {
    if (isInvalidTime(storageTime)) {
      logInvalidTime();
      return;
    }
    this.storageTime = storageTime;
  }

  /**
   * Gets the storage time. In order of priority, an explicitly set value overrides the injected
   * property value, which in turn overrides a fall-back default of DEFAULT_LENGTH hours.
   *
   * @return
   */
  int getStorageTime() {
    if (storageTime != null) {
      return storageTime;
    }
    try {
      Integer time = Integer.parseInt(storageTimeProperty);
      if (isInvalidTime(time)) {
        logInvalidTime();
        this.storageTime = DEFAULT_LENGTH;
      } else {
        this.storageTime = time;
      }

    } catch (NumberFormatException nfe) {
      log.warn(
          "Couldn't convert value [{}] to an integer value...reverting to default of "
              + "{} hours.",
          storageTimeProperty,
          DEFAULT_LENGTH);
      this.storageTime = DEFAULT_LENGTH;
    }
    return storageTime;
  }

  /*
   * package scoped for testing, not API
   */
  void setStorageTimeProperty(String storageTimeProperty) {
    this.storageTimeProperty = storageTimeProperty;
  }

  private void logInvalidTime() {
    log.warn(
        "Time value is not a positive integer .. reverting to default of {} hours.",
        DEFAULT_LENGTH);
  }

  private boolean isInvalidTime(Integer time) {
    return time == null || time <= 0;
  }

  @Override
  public String getRemovalCircumstancesMsg() {
    return String.format(
        "The export will be eligible for deletion after %d hours.", getStorageTime());
  }
}

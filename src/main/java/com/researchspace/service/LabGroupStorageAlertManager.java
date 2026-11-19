package com.researchspace.service;

import com.researchspace.model.User;
import java.util.Optional;

/**
 * Alerts every sysadmin once when the file usage of a Lab Group reaches the storage alert
 * threshold, and again only after the usage has dropped below it.
 */
public interface LabGroupStorageAlertManager {

  /**
   * Reads the stored threshold.
   *
   * @return the threshold in MB, or empty when the alert is off (unset, blank or unreadable)
   */
  Optional<Long> getThresholdMb();

  /**
   * Validates and stores a new threshold, then checks every Lab Group against it straight away.
   *
   * @param rawThresholdMb a whole number of MB from 1 to 999,999,999,999; {@code null} or blank
   *     removes the threshold
   * @param subject the sysadmin saving the value
   * @return the threshold now stored, or empty when it was removed
   * @throws com.researchspace.model.field.LocalizedIllegalArgumentException if the value is invalid
   */
  Optional<Long> setThresholdMb(String rawThresholdMb, User subject);

  /**
   * Checks every Lab Group against the stored threshold: alerts the sysadmins about groups that
   * have reached it and re-arms groups that are back below it. Does nothing but re-arm when the
   * alert is off.
   */
  void checkLabGroups();
}

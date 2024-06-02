package com.researchspace.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * Lock handler for media files, designed to match WOPI locks handler specification.
 *
 * <p>From WOPI documentation
 * (https://wopi.readthedocs.io/projects/wopirest/en/latest/concepts.html#term-lock): "WOPI locks
 * must: - Be associated with a single file. - Contain a lock ID of maximum length 1024 ASCII
 * characters. - Prevent all changes to that file unless a proper lock ID is provided. - Expire
 * after 30 minutes unless refreshed (see RefreshLock). - Not be associated with a particular user.
 * "
 */
@Component
public class MediaFileLockHandler {

  private int lockDurationInMilis = 30 * 60 * 1000;

  private Map<String, WopiFileLock> locksMap = new HashMap<>();

  @Data
  @AllArgsConstructor
  private static class WopiFileLock {
    private String lockId;
    private String fileId;
    private Date lockTimer;

    private void resetLockTimer() {
      lockTimer = new Date();
    }
  }

  /**
   * @return null if successful / current lockId if mismatched lockId
   */
  public synchronized String lock(String fileId, String lockId) {
    WopiFileLock currentLock = getLockFromMap(fileId);
    if (currentLock == null) {
      putNewLockToMap(fileId, lockId);
    } else {
      String currentLockId = currentLock.getLockId();
      if (currentLockId.equals(lockId)) {
        refreshLock(fileId, lockId);
      } else {
        return currentLockId;
      }
    }
    return null;
  }

  /**
   * @param global id of the file
   * @return current lockId (or empty string if no lock)
   */
  public synchronized String getLock(String fileId) {
    WopiFileLock currentLock = getLockFromMap(fileId);
    return currentLock == null ? "" : currentLock.getLockId();
  }

  /**
   * @return null if successful / current lockId (or empty string) if mismatched lockId
   */
  public synchronized String refreshLock(String fileId, String lockId) {
    WopiFileLock currentLock = getLockFromMap(fileId);
    if (currentLock == null) {
      return "";
    }
    String currentLockId = currentLock.getLockId();
    if (!currentLockId.equals(lockId)) {
      return currentLockId;
    }
    currentLock.resetLockTimer();
    return null;
  }

  /**
   * @return null if successful / current lockId (or empty string) if mismatched lockId
   */
  public synchronized String unlock(String fileId, String lockId) {
    WopiFileLock currentLock = getLockFromMap(fileId);
    if (currentLock == null) {
      return "";
    }
    String currentLockId = currentLock.getLockId();
    if (!currentLockId.equals(lockId)) {
      return currentLockId;
    }
    locksMap.remove(fileId);
    return null;
  }

  /**
   * @return null if successful / current lockId (or empty string) if mismatched oldLockId
   */
  public synchronized String unlockAndRelock(String fileId, String oldLockId, String newLockId) {
    WopiFileLock currentLock = getLockFromMap(fileId);
    if (currentLock == null) {
      return "";
    }
    String currentLockId = currentLock.getLockId();
    if (!currentLockId.equals(oldLockId)) {
      return currentLockId;
    }

    /* relocking. "This operation must be atomic" - so let's just change id
     * of current lock and update its timer */
    currentLock.setLockId(newLockId);
    currentLock.resetLockTimer();

    return null;
  }

  /* returns current lock or null if no valid lock.
   * also silently removes expired lock from map. */
  private WopiFileLock getLockFromMap(String fileId) {
    WopiFileLock currentFileLock = locksMap.get(fileId);
    if (currentFileLock != null && hasLockExpired(currentFileLock)) {
      // remove expired lock
      locksMap.remove(fileId);
      currentFileLock = null;
    }
    return currentFileLock;
  }

  private void putNewLockToMap(String fileId, String lockId) {
    WopiFileLock newLock = new WopiFileLock(lockId, fileId, new Date());
    locksMap.put(fileId, newLock);
  }

  private boolean hasLockExpired(WopiFileLock wopiFileLock) {
    return wopiFileLock.getLockTimer().getTime() + lockDurationInMilis <= (new Date()).getTime();
  }

  /*
   * for testing
   */
  public void setLockDurationInMilis(int lockDurationInMilis) {
    this.lockDurationInMilis = lockDurationInMilis;
  }
}

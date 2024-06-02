package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.User;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.springframework.stereotype.Service;

@Service("inventoryEditLockTracker")
public class InventoryEditLockTracker {

  private final ConcurrentHashMap<String, InventoryEditLock> editLocksByGlobalId =
      new ConcurrentHashMap<>();

  @Getter
  public static class InventoryEditLock {

    private static final int LOCK_DURATION_IN_SECONDS = 300;

    private ApiUser owner;

    private long creationDateMillis;

    private boolean extended;

    public InventoryEditLock(User owner) {
      this.owner = new ApiUser(owner);
      creationDateMillis = (new Date()).getTime();
    }

    public boolean isExpired() {
      return getTimeToExpiryInSeconds() < 0;
    }

    public int getTimeToExpiryInSeconds() {
      return (int)
          ((creationDateMillis + LOCK_DURATION_IN_SECONDS * 1000 - (new Date()).getTime()) / 1000);
    }

    public void extendLockTimer() {
      creationDateMillis = (new Date()).getTime();
      extended = true;
    }

    public ApiInventoryEditLock toApiInventoryEditLock(
        String globalId, ApiInventoryEditLockStatus status) {
      ApiInventoryEditLock apiLock = new ApiInventoryEditLock();
      apiLock.setGlobalId(globalId);
      apiLock.setOwner(owner);
      apiLock.setRemainingTimeInSeconds(getTimeToExpiryInSeconds());
      apiLock.setStatus(status);
      return apiLock;
    }
  }

  /**
   * return lock details if successfully locked, or was already locked throw exception if couldn't
   * lock.
   *
   * <p>method is thread-safe.
   */
  public ApiInventoryEditLock attemptToLockForEdit(String globalId, User user) {
    ApiInventoryEditLockStatus apiStatus;
    String apiMsg = null;

    InventoryEditLock currentLock =
        editLocksByGlobalId.compute(
            globalId,
            (key, value) -> {
              // if requested item not locked yet
              if (value == null || value.isExpired()) {
                value = new InventoryEditLock(user);
              } else {
                // if requested item already locked by current user
                if (value.getOwner().getUsername().equals(user.getUsername())) {
                  value.extendLockTimer();
                }
                // if locked by another user: do nothing here
              }
              return value;
            });

    // if locked by current user
    if (currentLock.getOwner().getUsername().equals(user.getUsername())) {
      apiStatus =
          currentLock.isExtended()
              ? ApiInventoryEditLockStatus.WAS_ALREADY_LOCKED
              : ApiInventoryEditLockStatus.LOCKED_OK;
    } else {
      // if locked by another user
      apiStatus = ApiInventoryEditLockStatus.CANNOT_LOCK;
      apiMsg =
          "Item is currently edited by another user (" + currentLock.getOwner().getUsername() + ")";
    }

    ApiInventoryEditLock apiLock = currentLock.toApiInventoryEditLock(globalId, apiStatus);
    apiLock.setMessage(apiMsg);
    return apiLock;
  }

  /**
   * returns true if successfully unlocked returns false if was not locked throws exception if
   * someone else holds a lock at the start of processing the request
   *
   * <p>method is thread-safe.
   */
  public boolean attemptToUnlock(String globalId, User user) {
    InventoryEditLock currentLock = editLocksByGlobalId.get(globalId);
    boolean removed = false;

    // remove the lock if expired
    if (currentLock != null && currentLock.isExpired()) {
      removed = editLocksByGlobalId.remove(globalId, currentLock);
      if (removed) {
        currentLock = null;
      }
      // if not removed at this point, refresh the lock as it must have changed
      currentLock = editLocksByGlobalId.get(globalId);
    }
    if (currentLock == null) {
      return false; // there was no lock (or removed after expiring)
    }

    // if locked by someone else
    if (!currentLock.getOwner().getUsername().equals(user.getUsername())) {
      throw new IllegalArgumentException(
          "Cannot unlock, as current lock belongs to another user "
              + "("
              + currentLock.getOwner().getUsername()
              + ")");
    }

    // at this point must be locked by current user, attempt unlocking
    return editLocksByGlobalId.remove(globalId, currentLock);
  }

  /**
   * Get username of lock owner (or null if item not locked).
   *
   * <p>method is thread-safe.
   */
  public String getLockOwnerForItem(String globalId) {
    // we use ConcurrentHashMap, so get() is thread-safe
    InventoryEditLock currentLock = editLocksByGlobalId.get(globalId);
    return currentLock != null ? currentLock.getOwner().getUsername() : null;
  }
}

package com.researchspace.service.inventory.impl;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Before;
import org.junit.Test;

public class InventoryEditLockTrackerTest extends SpringTransactionalTest {

  private User testUser;

  @Before
  public void setUp() {
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("editLock"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());
  }

  @Test
  public void testTwoUsersLockingEditingUnlockingSamples() {

    // create a pi, in group with testUser
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(piUser);
    Group group = createGroup("group", piUser);
    addUsersToGroup(piUser, group, testUser);

    ApiSampleWithFullSubSamples userSample = createBasicSampleForUser(testUser);
    ApiSampleWithFullSubSamples piSample = createBasicSampleForUser(piUser);

    // verify samples not locked
    assertNull(invLockTracker.getLockOwnerForItem(userSample.getGlobalId()));
    assertNull(invLockTracker.getLockOwnerForItem(piSample.getGlobalId()));

    // lock first sample for testUser
    ApiInventoryEditLock editLock =
        invLockTracker.attemptToLockForEdit(userSample.getGlobalId(), testUser);
    assertNotNull(editLock);
    assertEquals(testUser.getUsername(), editLock.getOwner().getUsername());
    assertEquals(ApiInventoryEditLockStatus.LOCKED_OK, editLock.getStatus());

    // verify first sample is locked
    assertEquals(
        testUser.getUsername(), invLockTracker.getLockOwnerForItem(userSample.getGlobalId()));
    assertNull(invLockTracker.getLockOwnerForItem(piSample.getGlobalId()));

    // try to lock again
    editLock = invLockTracker.attemptToLockForEdit(userSample.getGlobalId(), testUser);
    assertEquals(ApiInventoryEditLockStatus.WAS_ALREADY_LOCKED, editLock.getStatus());

    // lock pi's sample as a pi
    editLock = invLockTracker.attemptToLockForEdit(piSample.getGlobalId(), piUser);
    assertEquals(ApiInventoryEditLockStatus.LOCKED_OK, editLock.getStatus());

    // verify both samples locked
    assertEquals(
        testUser.getUsername(), invLockTracker.getLockOwnerForItem(userSample.getGlobalId()));
    assertEquals(piUser.getUsername(), invLockTracker.getLockOwnerForItem(piSample.getGlobalId()));

    // try to lock test sample as a pi
    editLock = invLockTracker.attemptToLockForEdit(userSample.getGlobalId(), piUser);
    assertEquals(ApiInventoryEditLockStatus.CANNOT_LOCK, editLock.getStatus());
    assertEquals(
        "Item is currently edited by another user (" + testUser.getUsername() + ")",
        editLock.getMessage());

    // unlock as a testUser
    boolean unlockResult = invLockTracker.attemptToUnlock(userSample.getGlobalId(), testUser);
    assertTrue(unlockResult);

    // verify only pi's sample remain locked
    assertNull(invLockTracker.getLockOwnerForItem(userSample.getGlobalId()));
    assertEquals(piUser.getUsername(), invLockTracker.getLockOwnerForItem(piSample.getGlobalId()));

    // try to unlock again
    unlockResult = invLockTracker.attemptToUnlock(userSample.getGlobalId(), testUser);
    assertFalse(unlockResult);

    // try to lock as a pi again
    editLock = invLockTracker.attemptToLockForEdit(userSample.getGlobalId(), piUser);
    assertEquals(ApiInventoryEditLockStatus.LOCKED_OK, editLock.getStatus());

    // verify both samples locked by pi now
    assertEquals(
        piUser.getUsername(), invLockTracker.getLockOwnerForItem(userSample.getGlobalId()));
    assertEquals(piUser.getUsername(), invLockTracker.getLockOwnerForItem(piSample.getGlobalId()));

    // try to unlock as testUser
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> invLockTracker.attemptToUnlock(userSample.getGlobalId(), testUser));
    assertEquals(
        "Cannot unlock, as current lock belongs to another user (" + piUser.getUsername() + ")",
        iae.getMessage());

    // try to unlock as pi
    unlockResult = invLockTracker.attemptToUnlock(userSample.getGlobalId(), piUser);
    assertTrue(unlockResult);
    unlockResult = invLockTracker.attemptToUnlock(piSample.getGlobalId(), piUser);
    assertTrue(unlockResult);

    // verify both samples unlocked now
    assertNull(invLockTracker.getLockOwnerForItem(userSample.getGlobalId()));
    assertNull(invLockTracker.getLockOwnerForItem(piSample.getGlobalId()));
  }
}

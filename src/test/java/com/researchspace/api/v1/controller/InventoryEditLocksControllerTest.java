package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.User;
import com.researchspace.service.inventory.impl.InventoryEditLockTracker;
import com.researchspace.testutils.SpringTransactionalTest;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;

public class InventoryEditLocksControllerTest extends SpringTransactionalTest {

  private @Autowired InventoryEditLocksController locksController;

  private @Autowired InventoryEditLockTracker tracker;

  private User testUser;

  @Before
  public void setUp() {
    testUser = createInitAndLoginAnyUser();
    assertTrue(testUser.isContentInitialized());
  }

  @Test
  public void globalIdParamValidation() throws BindException {
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> locksController.lockItemForEdit("asdf", testUser));
    assertEquals("not a valid global id: asdf", iae.getMessage());

    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> locksController.lockItemForEdit("SD123", testUser));
    assertEquals("unsupported global id type: SD123", iae.getMessage());
  }

  @Test
  public void lockEditingRequirePermissions() throws BindException {

    // create a sample
    ApiSampleWithFullSubSamples testSample =
        createBasicSampleForUser(testUser, "testUser's sample");
    String testSampleGlobalId = testSample.getGlobalId();

    // create another user, not related to testUser
    User otherUser = createAndSaveUserIfNotExists("other");
    initialiseContentWithEmptyContent(otherUser);

    // try locking user sample by other user
    NotFoundException nfe =
        assertThrows(
            NotFoundException.class,
            () -> locksController.lockItemForEdit(testSampleGlobalId, otherUser));
    assertEquals(
        "Inventory record with id ["
            + testSample.getId()
            + "] could not be retrieved - possibly it has been deleted, does not exist, "
            + "or you do not have permission to access it.",
        nfe.getMessage());

    // lock as test user
    ApiInventoryEditLock apiLock = locksController.lockItemForEdit(testSampleGlobalId, testUser);
    assertEquals(ApiInventoryEditLockStatus.LOCKED_OK, apiLock.getStatus());
    assertEquals(testUser.getUsername(), invLockTracker.getLockOwnerForItem(testSampleGlobalId));

    // try unlocking as other user
    nfe =
        assertThrows(
            NotFoundException.class,
            () -> locksController.unlockItemAfterEdit(testSampleGlobalId, otherUser));
    assertEquals(
        "Inventory record with id ["
            + testSample.getId()
            + "] could not be retrieved - possibly it has been deleted, does not exist, "
            + "or you do not have permission to access it.",
        nfe.getMessage());
    assertEquals(testUser.getUsername(), invLockTracker.getLockOwnerForItem(testSampleGlobalId));

    // testUser can unlock
    locksController.unlockItemAfterEdit(testSampleGlobalId, testUser);
    assertNull(invLockTracker.getLockOwnerForItem(testSampleGlobalId));

    // lock again, then transfer to another user
    locksController.lockItemForEdit(testSampleGlobalId, testUser);
    assertEquals(testUser.getUsername(), invLockTracker.getLockOwnerForItem(testSampleGlobalId));
    ApiSample sampleUpdate = new ApiSample();
    sampleUpdate.setId(testSample.getId());
    sampleUpdate.setOwner(new ApiUser(otherUser));
    sampleApiMgr.changeApiSampleOwner(sampleUpdate, testUser);
    assertEquals(testUser.getUsername(), invLockTracker.getLockOwnerForItem(testSampleGlobalId));

    // new owner can try to unlock, but that won't work
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> locksController.unlockItemAfterEdit(testSampleGlobalId, otherUser));
    assertTrue(
        iae.getMessage().startsWith("Cannot unlock, as current lock belongs to another user"));
    assertEquals(testUser.getUsername(), invLockTracker.getLockOwnerForItem(testSampleGlobalId));

    // original lock creator can still unlock, even though no longer has permission to the item
    locksController.unlockItemAfterEdit(testSampleGlobalId, testUser);
    assertNull(invLockTracker.getLockOwnerForItem(testSampleGlobalId));
  }
}

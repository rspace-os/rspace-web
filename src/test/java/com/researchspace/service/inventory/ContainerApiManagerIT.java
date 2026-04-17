package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import javax.validation.ConstraintViolationException;
import org.junit.Test;

public class ContainerApiManagerIT extends RealTransactionSpringTestBase {

  @Test
  public void checkContainerSavingConstraints() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    // create new container
    ApiContainer newListContainer = createBasicContainerForUser(testUser);
    Container savedListContainer =
        containerApiMgr.getContainerById(newListContainer.getId(), testUser);

    /*
     *  test changing canStoreContainers/canStoreSamples/canStoreInstuments flags
     */
    ApiContainer storageFlagsUpdate = new ApiContainer();
    storageFlagsUpdate.setId(savedListContainer.getId());

    // revert the flags pt1
    storageFlagsUpdate.setCanStoreSamples(true);
    storageFlagsUpdate.setCanStoreContainers(false);
    storageFlagsUpdate.setCanStoreInstruments(false);
    containerApiMgr.updateApiContainer(storageFlagsUpdate, testUser);
    ApiContainer updatedContainer =
        containerApiMgr.getApiContainerById(savedListContainer.getId(), testUser);
    assertTrue(updatedContainer.getCanStoreSamples());
    assertFalse(updatedContainer.getCanStoreContainers());
    assertFalse(updatedContainer.getCanStoreInstruments());

    // revert the flags pt2
    storageFlagsUpdate.setCanStoreSamples(false);
    storageFlagsUpdate.setCanStoreContainers(true);
    storageFlagsUpdate.setCanStoreInstruments(false);
    containerApiMgr.updateApiContainer(storageFlagsUpdate, testUser);
    updatedContainer = containerApiMgr.getApiContainerById(savedListContainer.getId(), testUser);
    assertFalse(updatedContainer.getCanStoreSamples());
    assertTrue(updatedContainer.getCanStoreContainers());
    assertFalse(updatedContainer.getCanStoreInstruments());

    // revert the flags pt3
    storageFlagsUpdate.setCanStoreSamples(false);
    storageFlagsUpdate.setCanStoreContainers(false);
    storageFlagsUpdate.setCanStoreInstruments(true);
    containerApiMgr.updateApiContainer(storageFlagsUpdate, testUser);

    updatedContainer = containerApiMgr.getApiContainerById(savedListContainer.getId(), testUser);
    assertFalse(updatedContainer.getCanStoreSamples());
    assertFalse(updatedContainer.getCanStoreContainers());
    assertTrue(updatedContainer.getCanStoreInstruments());

    // try setting all to false
    storageFlagsUpdate.setCanStoreSamples(false);
    storageFlagsUpdate.setCanStoreContainers(false);
    storageFlagsUpdate.setCanStoreInstruments(false);
    ConstraintViolationException cve =
        assertThrows(
            ConstraintViolationException.class,
            () -> containerApiMgr.updateApiContainer(storageFlagsUpdate, testUser));
    assertEquals(
        "Container cannot have all \"canStoreSamples\", \"canStoreContainers\" and "
            + "\"canStoreSamples\" set to false",
        cve.getMessage());
  }
}

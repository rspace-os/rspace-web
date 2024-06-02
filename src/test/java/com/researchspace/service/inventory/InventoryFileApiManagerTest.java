package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiInventoryFile;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.Sample;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import java.io.IOException;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;

public class InventoryFileApiManagerTest extends SpringTransactionalTest {

  @Before
  public void setUp() throws Exception {
    super.setUp();

    sampleDao.resetDefaultTemplateOwner();
  }

  @Test
  public void defaultDevProfileInventoryFileRetrieval() {

    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(testUser);

    // get top containers, with default pagination criteria (name desc ordering)
    PaginationCriteria<Container> pgCrit =
        PaginationCriteria.createDefaultForClass(Container.class);
    ISearchResults<ApiContainerInfo> defaultContainerResult =
        containerApiMgr.getTopContainersForUser(pgCrit, null, null, testUser);
    assertEquals(2, defaultContainerResult.getTotalHits().intValue());
    // find image container and its attachment
    ApiContainerInfo imageContainerInfo = defaultContainerResult.getResults().get(1);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_NAME,
        imageContainerInfo.getName());
    assertEquals(1, imageContainerInfo.getAttachments().size());
    ApiInventoryFile defaultAttachment = imageContainerInfo.getAttachments().get(0);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_ATTACHMENT_NAME,
        defaultAttachment.getName());

    // retrieve attachment directly through manager class
    assertTrue(inventoryFileApiMgr.exists(defaultAttachment.getId()));
    InventoryFile retrievedAttachment =
        inventoryFileApiMgr.getInventoryFileById(defaultAttachment.getId(), testUser);
    assertEquals(defaultAttachment.getName(), retrievedAttachment.getFileName());
  }

  @Test
  public void addRemoveAttachmentToInventoryFile() throws IOException {

    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples apiSample = createBasicSampleForUser(user);
    Sample dbSample = sampleApiMgr.getSampleById(apiSample.getId(), user);
    assertEquals(0, dbSample.getAttachedFiles().size());

    // add attachment
    InventoryFile attachedFile = addFileAttachmentToInventoryItem(dbSample.getOid(), user);
    assertNotNull(attachedFile.getCreationDate());
    assertEquals(user.getUsername(), attachedFile.getCreatedBy());

    // verify attachment added
    dbSample = sampleApiMgr.getSampleById(apiSample.getId(), user);
    assertEquals(1, dbSample.getAttachedFiles().size());

    // copy inventory item
    ApiSampleWithFullSubSamples copiedSample = sampleApiMgr.duplicate(apiSample.getId(), user);
    Sample dbSampleCopy = sampleApiMgr.getSampleById(copiedSample.getId(), user);
    assertEquals(1, dbSampleCopy.getAttachedFiles().size());

    // delete attachment from original sample
    inventoryFileApiMgr.markInventoryFileAsDeleted(attachedFile.getId(), user);

    // verify attachment deleted from original sample
    dbSample = sampleApiMgr.getSampleById(apiSample.getId(), user);
    assertEquals(0, dbSample.getAttachedFiles().size());

    // verify attachment present on a copy
    dbSampleCopy = sampleApiMgr.getSampleById(copiedSample.getId(), user);
    assertEquals(1, dbSampleCopy.getAttachedFiles().size());
  }

  @Test
  public void attachmentPermissionInsideAndOutsideGroup() throws IOException {

    // create a test group with user and pi
    TestGroup tg = createTestGroup(1);
    User testUser = tg.u1();
    User pi = tg.getPi();

    // create user outside group
    User otherUser = doCreateAndInitUser("other");

    // create a sample with attachment owned by pi user
    ApiSampleWithFullSubSamples piSample = createBasicSampleForUser(pi, "pi's sample");

    // user in group can attach file
    InventoryFile attachment =
        addFileAttachmentToInventoryItem(new GlobalIdentifier(piSample.getGlobalId()), testUser);

    // define expected error messages
    final String expectedItemNotFoundMsg =
        "Inventory record with id [" + piSample.getId() + "] could not";
    final String expectedAttachmentNotFoundMsg =
        "Inventory file with id [" + attachment.getId() + "] could not";

    // pi can retrieve attachment added by testUser
    inventoryFileApiMgr.getInventoryFileById(attachment.getId(), pi);
    // user outside cannot
    NotFoundException nfe =
        assertThrows(
            NotFoundException.class,
            () -> inventoryFileApiMgr.getInventoryFileById(attachment.getId(), otherUser));
    assertTrue(nfe.getMessage().startsWith(expectedAttachmentNotFoundMsg));

    // user outside group cannot attach file
    nfe =
        assertThrows(
            NotFoundException.class,
            () ->
                addFileAttachmentToInventoryItem(
                    new GlobalIdentifier(piSample.getGlobalId()), otherUser));
    assertTrue(nfe.getMessage().startsWith(expectedItemNotFoundMsg));

    // pi can delete the attachment
    inventoryFileApiMgr.markInventoryFileAsDeleted(attachment.getId(), pi);
    // user outside cannot
    nfe =
        assertThrows(
            NotFoundException.class,
            () -> inventoryFileApiMgr.markInventoryFileAsDeleted(attachment.getId(), otherUser));
    assertTrue(nfe.getMessage().startsWith(expectedAttachmentNotFoundMsg));
  }
}

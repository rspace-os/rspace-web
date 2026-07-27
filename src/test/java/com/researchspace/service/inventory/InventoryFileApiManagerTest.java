package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryFile;
import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.EcatImage;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;

public class InventoryFileApiManagerTest extends SpringTransactionalTest {

  @Before
  public void setUp() throws Exception {
    super.setUp();

    sampleTemplateDao.resetDefaultTemplateOwner();
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
    SampleEntity dbSample = sampleApiMgr.getSampleById(apiSample.getId(), user);
    assertEquals(0, dbSample.getAttachedFiles().size());

    // add file attachment
    InventoryFile attachedFile = addFileAttachmentToInventoryItem(dbSample.getOid(), user);
    assertNotNull(attachedFile.getCreationDate());
    assertEquals(user.getUsername(), attachedFile.getCreatedBy());

    // add gallery attachment
    InventoryFile attachedGalleryItem = addGalleryFileToInventoryItem(dbSample.getOid(), user);
    assertNotNull(attachedGalleryItem.getCreationDate());
    assertEquals(user.getUsername(), attachedGalleryItem.getCreatedBy());

    // verify attachment added
    dbSample = sampleApiMgr.getSampleById(apiSample.getId(), user);
    assertEquals(2, dbSample.getAttachedFiles().size());
    assertNull(dbSample.getAttachedFiles().get(0).getMediaFileGlobalIdentifier());
    assertNotNull(dbSample.getAttachedFiles().get(1).getMediaFileGlobalIdentifier());

    // copy inventory item
    ApiSampleWithFullSubSamples copiedSample = sampleApiMgr.duplicate(apiSample.getId(), user);
    SampleEntity dbSampleCopy = sampleApiMgr.getSampleById(copiedSample.getId(), user);
    assertEquals(2, dbSampleCopy.getAttachedFiles().size());

    // delete attachment from original sample
    inventoryFileApiMgr.markInventoryFileAsDeleted(attachedFile.getId(), user);
    inventoryFileApiMgr.markInventoryFileAsDeleted(attachedGalleryItem.getId(), user);

    // verify attachment deleted from original sample
    dbSample = sampleApiMgr.getSampleById(apiSample.getId(), user);
    assertEquals(0, dbSample.getAttachedFiles().size());

    // verify attachment present on a copy
    dbSampleCopy = sampleApiMgr.getSampleById(copiedSample.getId(), user);
    assertEquals(2, dbSampleCopy.getAttachedFiles().size());
  }

  @Test
  public void findAttachingItemsReturnsItemAttachingGalleryFile() throws IOException {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    InventoryFile galleryAttachment =
        addGalleryFileToInventoryItem(new GlobalIdentifier(sample.getGlobalId()), user);
    String galleryFileGlobalId = galleryAttachment.getMediaFileGlobalIdentifier();

    List<ApiInventoryReferencingItem> rows =
        inventoryFileApiMgr.findAttachingItems(galleryFileGlobalId, user);

    assertEquals(1, rows.size());
    assertEquals(sample.getGlobalId(), rows.get(0).getSourceGlobalId());
    // attachments have no DataCite relation; the label is added client-side
    assertNull(rows.get(0).getRelationType());
  }

  @Test
  public void findAttachingItemsReturnsEmptyForUnattachedGalleryFile() throws IOException {
    User user = createInitAndLoginAnyUser();
    // a gallery image no inventory item has attached; also exercises the attachment-field query
    EcatImage image = addImageToGallery(user);

    List<ApiInventoryReferencingItem> rows =
        inventoryFileApiMgr.findAttachingItems(image.getOid().getIdString(), user);

    assertEquals(0, rows.size());
  }

  @Test
  public void findAttachingItemsRejectsMissingTarget() {
    User user = createInitAndLoginAnyUser();
    // same error as an unreadable file, so the response never confirms whether the file exists
    assertThrows(
        ApiRuntimeException.class, () -> inventoryFileApiMgr.findAttachingItems("GL9999", user));
  }

  @Test
  public void findAttachingItemsResolvesFieldLevelAttachmentToOwningItem() throws IOException {
    // the headline field-level path: a Gallery file attached to a sample's ATTACHMENT field must
    // surface against the owning sample (not the field), exercising the real
    // InventoryAttachmentField
    // join HQL that the mocked unit test cannot reach
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createComplexSampleForUser(user);
    ApiInventoryEntityField attachmentField = sample.getFields().get(6);
    assertEquals(ApiFieldType.ATTACHMENT, attachmentField.getType());

    InventoryFile galleryAttachment =
        addGalleryFileToInventoryItem(new GlobalIdentifier(attachmentField.getGlobalId()), user);
    String galleryFileGlobalId = galleryAttachment.getMediaFileGlobalIdentifier();

    List<ApiInventoryReferencingItem> rows =
        inventoryFileApiMgr.findAttachingItems(galleryFileGlobalId, user);

    assertEquals(1, rows.size());
    assertEquals(sample.getGlobalId(), rows.get(0).getSourceGlobalId());
  }

  @Test
  public void findAttachingItemsExcludesSoftDeletedAttachment() throws IOException {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    InventoryFile galleryAttachment =
        addGalleryFileToInventoryItem(new GlobalIdentifier(sample.getGlobalId()), user);
    String galleryFileGlobalId = galleryAttachment.getMediaFileGlobalIdentifier();

    inventoryFileApiMgr.markInventoryFileAsDeleted(galleryAttachment.getId(), user);

    // a soft-deleted attachment must not surface as a back-reference
    assertEquals(0, inventoryFileApiMgr.findAttachingItems(galleryFileGlobalId, user).size());
  }

  @Test
  public void findAttachingItemsRejectsGalleryIdResolvingToNonMediaRecord() {
    // a well-formed GL id whose number is actually a document must return the uniform not-found,
    // never an uncaught 500 (ADR-0002 non-disclosure): the read-gate resolves the target's real
    // type rather than blindly casting it to a media file
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "not a gallery file");

    assertThrows(
        ApiRuntimeException.class,
        () -> inventoryFileApiMgr.findAttachingItems("GL" + doc.getId(), user));
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

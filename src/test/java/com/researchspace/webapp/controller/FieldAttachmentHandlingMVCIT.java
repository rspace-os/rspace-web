package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.testutils.RSpaceTestUtils;
import java.util.List;
import lombok.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FieldAttachmentHandlingMVCIT extends MVCTestBase {
  private @Autowired AuditManager auditMgr;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testAddAndRemoveFieldAttachments() throws Exception {
    // create a group
    User u1 = createAndSaveUser(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUser(getRandomAlphabeticString("u2"));
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUsers(u1, u2, pi);
    Group g1 = createGroupForUsers(pi, pi.getUsername(), "", pi, u1, u2);

    // login as u1;
    logoutAndLoginAs(u1);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(u1, "<p>text</p");
    Field field = doc.getFields().get(0);
    EcatDocumentFile attachment =
        addAttachmentDocumentToField(RSpaceTestUtils.getAnyAttachment(), field, u1);
    shareRecordWithGroup(u1, g1, doc);

    // basic assertions:
    FieldAttachment fa1 = getFieldAttachment(attachment, field);
    assertFalse(fa1.isDeleted());
    logoutAndLoginAs(u2);
    assertTrue(
        permissionUtils.isPermittedViaMediaLinksToRecords(attachment, PermissionType.READ, u2));

    logoutAndLoginAs(u1);
    // we remove all text, this should trigger as deleted.
    doAutosaveAndSaveMVC(field, "", u1);

    fa1 = getFieldAttachment(attachment, field);
    assertTrue(fa1.isDeleted());
    logoutAndLoginAs(u2);
    // but is still viewable, hence revision history will still work
    assertTrue(
        permissionUtils.isPermittedViaMediaLinksToRecords(attachment, PermissionType.READ, u2));
  }

  // this is TC1 in RSPAC-1544
  @Test
  public void linksToMediaFilesAreRemovedAfterAutosavesAreCancelled_RSPAC1544() throws Exception {
    long initialLinkCount = countFieldMediaLinks();
    User anyUser = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(anyUser, "");
    Field field = doc.getFields().get(0);
    EcatImage image = addImageToGallery(anyUser);
    String imageLinkText = richTextUpdater.generateRawImageElement(image, field.getId() + "");
    // just autosaving does not trigger link-generation, need explicit call to create media link
    doAutosaveMVC(field, imageLinkText, anyUser);
    assertEquals(initialLinkCount + 1, countFieldMediaLinks());
    // postLinkToMediaFile(field,image,anyUser);

    assertEquals(initialLinkCount + 1, countFieldMediaLinks());
    // now cancel the autosave, link should be removed
    cancelAutosave(field, anyUser);
    assertEquals(initialLinkCount, countFieldMediaLinks());
  }

  // this is TC3 in RSPAC-1544
  @Test
  public void removedlinksToMediaFilesAreRestoredAfterAutosavesAreCancelled_RSPAC1544()
      throws Exception {
    FieldAttachmentTestSetup setup = fieldAttachmentStandardSetup();
    autosaveAndSaveTextLink(setup);
    assertEquals(setup.initialLinkCount + 1, countFieldMediaLinks());

    // now we edit again, removing the link text, and autosaving only
    doAutosaveMVC(setup.field, "", setup.anyUser);
    // the link is still present. Autosaving an empty field doesn't cancel link
    assertEquals(setup.initialLinkCount + 1, countFieldMediaLinks());
    // we cancel the deletion
    cancelAutosave(setup.field, setup.anyUser);

    // assertField content is restored with ecat image link in text
    assertEquals(setup.initialLinkCount + 1, countFieldMediaLinks());
    assertFieldContentsHasNLinks(setup.anyUser, setup.field, 1);
  }

  // this is TC4 in RSPAC-1544
  @Test
  public void removedlinksToMediaFilesAreRemovedAfterAutosavesAreSaved_RSPAC1544()
      throws Exception {
    // setup
    FieldAttachmentTestSetup setup = fieldAttachmentStandardSetup();
    // we save document and create association
    autosaveAndSaveTextLink(setup);

    // now save the deletion, check this is removed
    doAutosaveAndSaveMVC(setup.field, "", setup.anyUser);
    assertEquals(setup.initialLinkCount + 1, countFieldMediaLinks());
    assertFieldContentsHasNLinks(setup.anyUser, setup.field, 0);
  }

  // use case 3 RSPAC-1544
  @Test
  public void deleteTextFollowingAutosave() throws Exception {
    FieldAttachmentTestSetup setup = fieldAttachmentStandardSetup();
    doAutosaveMVC(setup.field, setup.fieldTextWithLink, setup.anyUser);
    assertEquals(setup.initialLinkCount + 1, countFieldMediaLinks());
    // now we autosave again with deleted text - should remove the association
    doAutosaveMVC(setup.field, "", setup.anyUser);
    assertEquals(setup.initialLinkCount, countFieldMediaLinks());

    // now we'll do an actual save, then delete from the text in an autosave:
    doAutosaveAndSaveMVC(setup.field, setup.fieldTextWithLink, setup.anyUser);
    // autosave the removed link:
    doAutosaveMVC(setup.field, "", setup.anyUser);
    // it should just be marked as deleted.
    assertEquals(setup.initialLinkCount + 1, countFieldMediaLinks());
    assertTrue(getFieldAttachment(setup.mediaFile, setup.field).isDeleted());

    // now we cancel the editing, so that we should restore the original saved link
    cancelAutosave(setup.field, setup.anyUser);
    assertFalse(getFieldAttachment(setup.mediaFile, setup.field).isDeleted());
  }

  // use case 4 RSPAC-1544
  @Test
  public void restoredDeletedAttachmentUndeletesFieldAttachment() throws Exception {
    // we save a FA
    FieldAttachmentTestSetup setup = fieldAttachmentStandardSetup();
    doAutosaveAndSaveMVC(setup.field, setup.fieldTextWithLink, setup.anyUser);
    // now we save deleted attachment. FA should be marked deleted
    doAutosaveAndSaveMVC(setup.field, "", setup.anyUser);
    assertTrue(getFieldAttachment(setup.mediaFile, setup.field).isDeleted());

    // now we restore revision:
    List<AuditedEntity<StructuredDocument>> docs =
        auditMgr.getRevisionsForEntity(StructuredDocument.class, setup.doc.getId());
    assertEquals(3, docs.size());
    // 2nd revision is the one with the attachment link
    auditMgr.restoreRevisionAsCurrent(docs.get(1).getRevision(), setup.doc.getId());
    // now should no longer be deleted
    assertFalse(getFieldAttachment(setup.mediaFile, setup.field).isDeleted());
  }

  @Test
  public void internalLinkCreation() throws Exception {
    InternalLinkTestSetup setup = internalLinkStandardSetup();
    // add link in text ->autosave: link added
    doAutosaveMVC(setup.srcfield, setup.fieldTextWithLink, setup.anyUser);
    assertEquals(setup.initialLinkCount + 1, countInternalLinks());

    // remove text: should be deleted
    doAutosaveMVC(setup.srcfield, "", setup.anyUser);
    assertEquals(setup.initialLinkCount, countInternalLinks());

    // autosave -> cancel autosave: InternalLink should be deleted
    doAutosaveMVC(setup.srcfield, setup.fieldTextWithLink, setup.anyUser);
    assertEquals(setup.initialLinkCount + 1, countInternalLinks());
    cancelAutosave(setup.srcfield, setup.anyUser);
    assertEquals(setup.initialLinkCount, countInternalLinks());

    // create and save -> then delete text -> autosave:  should also be deleted
    doAutosaveAndSaveMVC(setup.srcfield, setup.fieldTextWithLink, setup.anyUser);
    assertEquals(setup.initialLinkCount + 1, countInternalLinks());
    doAutosaveMVC(setup.srcfield, "", setup.anyUser);
    assertEquals(setup.initialLinkCount, countInternalLinks());

    // create and save a duplicate link -> then delete  link in text -> autosave:  link is deleted
    doAutosaveAndSaveMVC(
        setup.srcfield, setup.fieldTextWithLink + setup.fieldTextWithLink, setup.anyUser);
    assertEquals(setup.initialLinkCount + 1, countInternalLinks());
    // - removing both links in text. No text link remains, link should be removed
    doAutosaveMVC(setup.srcfield, "", setup.anyUser);
    assertEquals(setup.initialLinkCount, countInternalLinks());
  }

  /*
   * Encapsulates the setup state required for several tests
   */
  @Value
  static class FieldAttachmentTestSetup {
    User anyUser;
    long initialLinkCount;
    Field field;
    EcatMediaFile mediaFile;
    String fieldTextWithLink;
    StructuredDocument doc;
  }

  // creates an empty doc and adds image to gallery
  FieldAttachmentTestSetup fieldAttachmentStandardSetup() throws Exception {
    long initialLinkCount = countFieldMediaLinks();
    User anyUser = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(anyUser, "");
    Field field = doc.getFields().get(0);
    EcatImage image = addImageToGallery(anyUser);
    String imageLinkText = richTextUpdater.generateRawImageElement(image, field.getId() + "");
    return new FieldAttachmentTestSetup(
        anyUser, initialLinkCount, field, image, imageLinkText, doc);
  }

  // creates an empty doc and adds image to gallery
  @Value
  static class InternalLinkTestSetup {
    User anyUser;
    long initialLinkCount;
    Field srcfield;
    String fieldTextWithLink;
    StructuredDocument source;
    StructuredDocument linkTarget;
  }

  // creates a multi-field form.
  InternalLinkTestSetup internalLinkStandardSetup() throws Exception {
    long initialLinkCount = countInternalLinks();
    User anyUser = createInitAndLoginAnyUser();
    RSForm experimentForm = createAnExperimentForm("exp", anyUser);
    StructuredDocument doc = createDocumentInRootFolder(experimentForm, anyUser);
    Field field = doc.getFields().get(0);
    StructuredDocument linkTarget = createBasicDocumentInRootFolderWithText(anyUser, "targetDoc");
    String linkText = richTextUpdater.generateURLStringForInternalLink(linkTarget);
    return new InternalLinkTestSetup(anyUser, initialLinkCount, field, linkText, doc, linkTarget);
  }

  private long countInternalLinks() throws Exception {
    return doInTransaction(
        () ->
            (Long)
                (sessionFactory
                    .getCurrentSession()
                    .createQuery("select count(*) from InternalLink")
                    .uniqueResult()));
  }

  private void autosaveAndSaveTextLink(FieldAttachmentTestSetup setup) throws Exception {
    assertEquals(setup.initialLinkCount, countFieldMediaLinks());
    doAutosaveAndSaveMVC(setup.field, setup.fieldTextWithLink, setup.anyUser);
  }

  private void assertFieldContentsHasNLinks(User anyUser, Field field, int expected)
      throws Exception {
    FieldContents contentsAfterCancel =
        doInTransaction(() -> fieldParser.findFieldElementsInContent(getFieldData(anyUser, field)));
    assertEquals(expected, contentsAfterCancel.getElements(EcatImage.class).size());
  }

  private String getFieldData(User anyUser, Field field) {
    return fieldMgr.get(field.getId(), anyUser).get().getFieldData();
  }

  private long countFieldMediaLinks() throws Exception {
    return doInTransaction(
        () ->
            (Long)
                (sessionFactory
                    .getCurrentSession()
                    .createQuery("select count(*) from FieldAttachment")
                    .uniqueResult()));
  }

  private FieldAttachment getFieldAttachment(EcatMediaFile attachment, Field field)
      throws Exception {
    return doInTransaction(
        () ->
            sessionFactory
                .getCurrentSession()
                .createQuery(
                    "from FieldAttachment fa  where fa.mediaFile = :media and fa.field = :field",
                    FieldAttachment.class)
                .setParameter("media", attachment)
                .setParameter("field", field)
                .list()
                .get(0));
  }
}

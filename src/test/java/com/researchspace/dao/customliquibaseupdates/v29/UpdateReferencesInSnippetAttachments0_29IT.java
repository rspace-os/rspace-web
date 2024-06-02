package com.researchspace.dao.customliquibaseupdates.v29;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.dao.customliquibaseupdates.AbstractDBHelpers;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RecordAttachment;
import com.researchspace.model.User;
import com.researchspace.model.record.Snippet;
import com.researchspace.service.EcatCommentManager;
import com.researchspace.service.EcatImageAnnotationManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RecordManager;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.IOException;
import java.util.List;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import org.apache.commons.codec.binary.Base64;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UpdateReferencesInSnippetAttachments0_29IT extends AbstractDBHelpers {

  private @Autowired RecordManager recordManager;
  private @Autowired EcatCommentManager commentManager;
  private @Autowired RSChemElementManager rsChemElementManager;
  private @Autowired EcatImageAnnotationManager ecatImageAnnotationManager;

  private UpdateReferencesInSnippetAttachments updater;
  private User user;

  // tested elements
  private EcatImage img;
  private EcatImageAnnotation anno;
  private EcatImageAnnotation sketch;
  private RSChemElement chem;
  private EcatComment comment;

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void check() throws IOException, SetupException, CustomChangeException {

    updater = new UpdateReferencesInSnippetAttachments();
    updater.setUp();

    user = createInitAndLoginAnyUser();
    img = addImageToGallery(user);

    // check number of initial snippets
    openTransaction();
    List<Snippet> initSnippets = getAllSnippets();
    commitTransaction();

    // create two snippets with media, one with text
    Snippet snip1 = recordManager.createSnippet("snip1", "test1", user);
    Snippet snip2 = recordManager.createSnippet("snip2", "test2", user);
    Snippet snip3 = recordManager.createSnippet("snip3", "test3", user);

    // add annotation/chemicals/comments, update them to point to the snippets through parentId and
    // have empty record id, save
    openTransaction();
    addImage(snip1);
    addSketch(snip1);
    addComment(snip1);
    addAnnotation(snip2);
    addChemElement(snip2);
    commitTransaction();

    // check the snippets are added and don't have RecordAttachments
    openTransaction();
    List<Snippet> newSnippets = getAllSnippets();
    newSnippets.removeAll(initSnippets);
    assertEquals(3, newSnippets.size());
    assertEquals(0, newSnippets.get(0).getLinkedMediaFiles().size());
    assertEquals(0, newSnippets.get(1).getLinkedMediaFiles().size());
    assertEquals(0, newSnippets.get(2).getLinkedMediaFiles().size());
    commitTransaction();

    // assert annotation/chemicals/comments are added and are pointing to snippet through parent id
    // not record id
    assertOldStyleAnnotation(anno);
    assertOldStyleAnnotation(sketch);
    assertOldStyleChem(chem);
    assertOldStyleComment(comment);

    // call the updater
    openTransaction();
    updater.setUp();
    updater.execute(null);
    commitTransaction();

    // check RecordAttachment is created for snip1 and snip2
    openTransaction();
    List<Snippet> updatedSnippets = getAllSnippets();
    updatedSnippets.removeAll(initSnippets);
    assertEquals(3, updatedSnippets.size());
    for (Snippet updatedSnip : updatedSnippets) {
      if (snip3.getId().equals(updatedSnip.getId())) {
        assertEquals(0, updatedSnip.getLinkedMediaFiles().size());
      } else {
        // snip1 has an image, snip2 image with annotation
        assertEquals(1, updatedSnip.getLinkedMediaFiles().size());
        RecordAttachment recordAttachment =
            (RecordAttachment) updatedSnip.getLinkedMediaFiles().toArray()[0];
        assertEquals(updatedSnip, recordAttachment.getRecord());
        assertEquals(img, recordAttachment.getMediaFile());
      }
    }
    commitTransaction();

    // check annotation/sketch/chemical has null parent and record_id pointing to the snippet
    openTransaction();
    anno = ecatImageAnnotationManager.get(anno.getId(), user);
    sketch = ecatImageAnnotationManager.get(sketch.getId(), user);
    chem = rsChemElementManager.get(chem.getId(), user);
    comment = commentManager.getEcatComment(comment.getId(), null, user);

    assertNewStyleAnnotation(anno);
    assertNewStyleAnnotation(sketch);
    assertNewStyleChem(chem);
    assertNewStyleComment(comment);
    commitTransaction();
  }

  private void addImage(Snippet snip) throws IOException {
    String imgLink = richTextUpdater.generateRawImageElement(img, snip.getId() + "");
    addLinkAndSaveSnippet(snip, imgLink);
  }

  private void addAnnotation(Snippet snip) {
    anno = new EcatImageAnnotation(snip.getId(), null, "test".getBytes(), "annotations");
    anno.setImageId(img.getId());
    ecatImageAnnotationManager.save(anno, user);

    String annoLink = richTextUpdater.generateAnnotatedImageElement(anno, snip.getId() + "");
    addLinkAndSaveSnippet(snip, annoLink);
  }

  private void addSketch(Snippet snip) {
    sketch = new EcatImageAnnotation(snip.getId(), null, "test".getBytes(), "annotations");
    ecatImageAnnotationManager.save(sketch, user);

    String sketchLink = richTextUpdater.generateImgLinkForSketch(sketch);
    addLinkAndSaveSnippet(snip, sketchLink);
  }

  private void addChemElement(Snippet snip) throws IOException {
    String chemStr = RSpaceTestUtils.getExampleChemString();
    String imageBytes = RSpaceTestUtils.getChemImage();
    Base64 dec = new Base64();
    byte[] decodedBytes = dec.decode(imageBytes.split(",")[1]);
    chem =
        RSChemElement.builder()
            .dataImage(decodedBytes)
            .chemElements(chemStr)
            .chemElementsFormat(ChemElementsFormat.MOL)
            .parentId(snip.getId())
            .build();
    rsChemElementManager.save(chem, null);

    String chemLink =
        richTextUpdater.generateURLStringForRSChemElementLink(
            chem.getId(), chem.getParentId(), 50, 50);
    addLinkAndSaveSnippet(snip, chemLink);
  }

  private void addComment(Snippet snip) {
    comment = new EcatComment(snip.getId(), null, user);
    commentManager.addComment(comment);
    EcatCommentItem item = new EcatCommentItem(comment, "test comment item", user);
    commentManager.addCommentItem(comment, item);

    String commLink = richTextUpdater.generateURLStringForCommentLink(comment.getComId() + "");
    addLinkAndSaveSnippet(snip, commLink);
  }

  private void addLinkAndSaveSnippet(Snippet snip, String imgLink) {
    snip.setContent(snip.getContent() + " " + imgLink);
    recordDao.save(snip);
  }

  private void assertOldStyleAnnotation(EcatImageAnnotation anno) {
    assertNull(anno.getRecord());
    assertNotNull(anno.getParentId());
  }

  private void assertNewStyleAnnotation(EcatImageAnnotation anno) {
    assertNotNull(anno.getRecord());
    assertTrue(anno.getRecord() instanceof Snippet);
    assertNull(anno.getParentId());
  }

  private void assertOldStyleChem(RSChemElement chem) {
    assertNull(chem.getRecord());
    assertNotNull(chem.getParentId());
  }

  private void assertNewStyleChem(RSChemElement chem) {
    assertNotNull(chem.getRecord());
    assertTrue(chem.getRecord() instanceof Snippet);
    assertNull(chem.getParentId());
  }

  private void assertOldStyleComment(EcatComment comment) {
    assertNull(comment.getRecord());
    assertNotNull(comment.getParentId());
  }

  private void assertNewStyleComment(EcatComment comment) {
    assertNotNull(comment.getRecord());
    assertTrue(comment.getRecord() instanceof Snippet);
    assertNull(comment.getParentId());
  }
}

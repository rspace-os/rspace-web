package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.dao.EcatImageAnnotationDao;
import com.researchspace.dao.RSChemElementDao;
import com.researchspace.dao.RSMathDao;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DocumentCopyManagerTestIT extends SpringTransactionalTest {

  private User user;

  @Autowired private RichTextUpdater updater;
  @Autowired private EcatImageAnnotationDao imageAnnotationDao;
  @Autowired private RSChemElementDao chemDao;
  @Autowired private RSMathDao mathDao;
  @Autowired private DocumentCopyManager documentCopyManager;

  @Before
  public void setUp() throws IllegalAddChildOperation {
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    initialiseContentWithExampleContent(user);
    assertTrue(user.isContentInitialized());
    logoutAndLoginAs(user);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testCopyStructuredDocument() throws Exception {
    Folder root = user.getRootFolder();
    int numberb4 = root.getChildren().size();
    flushDatabaseState();
    long numb4InDB =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    RSForm anyForm = formDao.getAll().get(0);
    StructuredDocument document =
        recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);

    Field txtFld = document.getTextFields().get(0);

    // two types of annotation
    EcatImageAnnotation ann = addImageAnnotationToField(document, txtFld, user);
    EcatImageAnnotation sketch = addSketchToField(document, txtFld);

    String commentText = "comment";
    addCommentToTextField(document, txtFld, commentText);

    StructuredDocument linkTarget = createBasicDocumentInRootFolderWithText(user, "linkTargetDoc");
    addInternalLinkToField(txtFld, linkTarget);
    assertEquals(1, internalLinkDao.getLinksPointingToRecord(linkTarget.getId()).size());

    InputStream molInput = getClass().getResourceAsStream("/TestResources/Amfetamine.mol");
    String chemElementMolString = IOUtils.toString(molInput, StandardCharsets.UTF_8);
    molInput.close();

    RSChemElement origChemElement = addChemElementToField(document, txtFld, chemElementMolString);
    final String originalChemString = origChemElement.getChemElements();
    int originalChemEls = chemDao.getAll().size();

    RSMath origMath = addMathToField(txtFld, user);
    final int originalMathEls = mathDao.getAll().size();

    // now actually do the copy
    String copyName = "newcopy";
    StructuredDocument copy =
        (StructuredDocument)
            documentCopyManager.copy(document, copyName, user, null).getUniqueCopy();
    assertEquals(copyName, copy.getName());
    assertEquals(document.getIconId(), copy.getIconId());

    Field txtFldCpy = copy.getTextFields().get(0);
    List<EcatImageAnnotation> anns =
        imageAnnotationDao.getAllImageAnnotationsFromField(txtFldCpy.getId());
    assertEquals(2, anns.size());
    // annotation points to new field and record
    assertEquals(txtFldCpy.getId(), anns.get(0).getParentId());
    assertEquals(copy, anns.get(0).getRecord());
    // sketch should not contain original ID
    assertNotEquals(txtFldCpy.getId(), sketch.getId());
    // internal link entity should be copied
    assertEquals(2, internalLinkDao.getLinksPointingToRecord(linkTarget.getId()).size());

    // now we'll alter the original, and check that the copy is unaffected.
    final String originalAnnotation = ann.getAnnotations();
    ann.setTextAnnotations("NEW_ANNOTATIONS");
    imageAnnotationDao.save(ann);

    // reload copy
    EcatImageAnnotation copyAnnotation = imageAnnotationDao.get(anns.get(1).getId());
    // sanity check that we've not loaded the original annotation again
    assertNotEquals(copyAnnotation.getId(), ann.getId());
    // copy is unaffected.
    assertEquals(originalAnnotation, copyAnnotation.getAnnotations());

    // check chem element was copied
    assertEquals(originalChemEls + 1, chemDao.getAll().size());
    List<RSChemElement> copiedEls = chemDao.getAllChemElementsFromField(txtFldCpy.getId());
    assertEquals(1, copiedEls.size());
    assertEquals(copy, copiedEls.get(0).getRecord());
    assertNotEquals(copiedEls.get(0).getId(), origChemElement.getId());

    // now alter the copy
    copiedEls.get(0).setChemElements("Changed");
    chemDao.save(copiedEls.get(0));
    // now reload the original, check is unaffected by altering the copy
    RSChemElement origChemElementReloaded = chemDao.get(origChemElement.getId());
    assertEquals(originalChemString, origChemElement.getChemElements());

    // check math element was copied
    assertEquals(originalMathEls + 1, mathDao.getAll().size());
    List<RSMath> mathCopiedEls = mathDao.getAllMathElementsFromField(txtFldCpy.getId());
    assertEquals(1, mathCopiedEls.size());
    assertEquals(copy, mathCopiedEls.get(0).getRecord());
    assertNotSame(mathCopiedEls.get(0).getId(), origMath.getId());

    // now alter copy:
    mathCopiedEls.get(0).setLatex("23 +45");
    mathDao.save(mathCopiedEls.get(0));
    RSMath originalReloaded = mathDao.get(origMath.getId());
    assertEquals(origMath.getLatex(), originalReloaded.getLatex());

    // check the comment was copied
    List<EcatComment> copiedComments = commentsDao.getCommentAll(txtFldCpy.getId());
    assertEquals(1, copiedComments.size());
    assertEquals(copy, copiedComments.get(0).getRecord());
    assertEquals(commentText, copiedComments.get(0).getItems().get(0).getItemContent());

    // now add new item to copied comment...
    EcatComment copied = copiedComments.get(0);
    EcatCommentItem item2 = new EcatCommentItem();
    item2.setItemContent("new text in copy");
    copied.addCommentItem(item2);
    commentsDao.save(copied);
    // now, update comment and check original comment still unchanged.
    List<EcatComment> originalComments = commentsDao.getCommentAll(txtFld.getId());
    EcatComment comment1 = originalComments.get(0);
    assertEquals(1, comment1.getItems().size());
    assertEquals(commentText, comment1.getItems().get(0).getItemContent());

    flushDatabaseState();
    Folder f = folderDao.getRootRecordForUser(user);
    // original + copy + linkTarget
    assertEquals(numberb4 + 2, f.getChildren().size());
    assertEquals(
        numb4InDB + 2,
        recordMgr
            .listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION)
            .getTotalHits()
            .intValue());

    assertColumnIndicesAreTheSameForFieldsAndFormss(copy);
  }

  private RSChemElement addChemElementToField(StructuredDocument record, Field field, String mol) {
    RSChemElement chem = new RSChemElement();
    chem.setChemElements(mol);
    chem.setParentId(field.getId());
    chem.setRecord(record);
    chem.setSmilesString("smiles");
    chem.setChemElementsFormat(ChemElementsFormat.MOL);
    chemDao.save(chem);

    String chemContent =
        updater.generateURLStringForRSChemElementLink(chem.getId(), field.getId(), 50, 50);
    field.setFieldData(field.getFieldData() + " and chem " + chemContent);
    recordDao.save(record); // with updated field

    return chem;
  }

  private EcatImageAnnotation addSketchToField(StructuredDocument parent, Field txtFld) {
    EcatImageAnnotation sketch = new EcatImageAnnotation();
    sketch.setParentId(txtFld.getId());
    sketch.setRecord(txtFld.getStructuredDocument());
    assertTrue(sketch.isSketch());
    imageAnnotationDao.save(sketch);

    String sketchContent = updater.generateImgLinkForSketch(sketch);
    txtFld.setFieldData(txtFld.getFieldData() + " and sketch " + sketchContent);
    recordDao.save(parent); // with updated field
    return sketch;
  }

  private void addCommentToTextField(StructuredDocument parent, Field txtFld, String commentText) {
    EcatComment ecatCmmnt = new EcatComment();
    EcatCommentItem item = new EcatCommentItem();
    item.setItemContent(commentText);
    ecatCmmnt.addCommentItem(item);
    ecatCmmnt.setParentId(txtFld.getId());
    ecatCmmnt.setRecord(parent);
    commentsDao.addComment(ecatCmmnt);

    String commentContent = updater.generateURLStringForCommentLink("" + ecatCmmnt.getId());
    txtFld.setFieldData(txtFld.getFieldData() + " and comment " + commentContent);
    recordDao.save(parent); // with updated field
  }

  @Test
  public void testCopyFolderWithMediaFiles() throws IOException {
    // Create sub folder in gallery folder
    Folder originalFolder = createImgGallerySubfolder("images-folder-test", user);
    Folder imageGalleryFolder =
        recordMgr.getGallerySubFolderForUser(MediaUtils.IMAGES_MEDIA_FLDER_NAME, user);
    // Add media files to new folder
    addImageToGalleryFolder(originalFolder, user);
    // Copy folder
    RecordCopyResult copyResult =
        folderMgr.copy(originalFolder.getId(), user, "images-folder-test_copy");
    // assert copied folder contains correct files/copies are intact
    Folder copiedFolder = (Folder) copyResult.getCopy(originalFolder);
    assertEquals("images-folder-test_copy", copiedFolder.getName());
    assertEquals(1, getRecordCountInFolderForUser(copiedFolder.getId()));
  }
}

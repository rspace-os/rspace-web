package com.researchspace.archive.model;

import static com.researchspace.model.field.FieldType.NUMBER;
import static com.researchspace.testutils.NetFilesTestFactory.createAnyNfsFileStore;
import static com.researchspace.testutils.TestFactory.createAnySD;
import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalForm;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.ArchiveComment;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.FieldTestUtils;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArchiveModelFactoryTest {
  RSForm form;
  ArchiveModelFactory factory;
  User anyUser;

  @Before
  public void setUp() throws Exception {
    factory = new ArchiveModelFactory();
    setUpFormwIthAllFormFieldDataSet();
    anyUser = TestFactory.createAnyUser("any");
  }

  private void setUpFormwIthAllFormFieldDataSet() {
    form = TestFactory.createAnyForm();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testCommentMapping() {
    EcatComment commm = TestFactory.createEcatComment(1L, createAnySD(), 3L);
    ArchiveComment comment = factory.createComment(commm);
    assertNotNull(comment.getCreateDate());
    assertNotNull(comment.getUpdateDate());
    assertNotNull(comment.getLastUpdater());
    assertNotNull(comment.getAuthor());
  }

  @Test
  public void testGalleryItemMapping() {
    EcatDocumentFile doc = TestFactory.createEcatDocument(2L, anyUser);
    Folder f = TestFactory.createAFolder("folder", anyUser);
    f.addChild(doc, anyUser);
    ArchivalGalleryMetadata metadata = factory.createGalleryMetadata(doc);
    assertNotNull(metadata.getName());
    assertEquals(doc.getName(), metadata.getName());
    assertEquals(doc.getModificationDateAsDate(), metadata.getModificationDate());
  }

  @Test
  public void testHappyCase() {
    form.addFieldForm(TestFactory.createDateFieldForm());
    form.addFieldForm(FieldTestUtils.createANumberFieldForm());
    form.addFieldForm(FieldTestUtils.createStringForm());
    form.setId(1L);
    StructuredDocument doc = createAnySD(form);
    ArchivalForm arForm = factory.createArchivalFormForDocument(doc, "form");
    assertEquals(4, arForm.getFieldFormList().size());
  }

  @Test
  public void testNumberDefaultsNull_RSPAC1133() {
    form.setId(1L);
    NumberFieldForm nff = new NumberFieldForm("number");
    form.addFieldForm(nff);
    StructuredDocument doc = createAnySD(form);
    ArchivalForm arForm = factory.createArchivalFormForDocument(doc, "form");
    assertEquals(2, arForm.getFieldFormList().size());
  }

  /**
   * RSDEV-1202 / RSDEV-1140 (Vienna): a field form that is soft-deleted after a document was
   * created must still be exported, because the document holds that field's content. The previous
   * behaviour (sourcing the form copy from the form's field forms and skipping deleted ones)
   * dropped it and lost the content on import. The exported form is now built from the document's
   * own fields, so a deleted-but-still-used field form is included.
   */
  @Test
  public void usedButDeletedFieldFormIsStillExported_RSDEV_1140() {
    form.setId(1L);
    NumberFieldForm nff = new NumberFieldForm("number");
    form.addFieldForm(nff);
    StructuredDocument doc = createAnySD(form);
    // the field form is deleted after the document already has a field (and content) for it
    nff.setDeleted(true);

    ArchivalForm arForm = factory.createArchivalFormForDocument(doc, "form");

    assertEquals(doc.getFields().size(), arForm.getFieldFormList().size());
    assertTrue(
        "deleted-but-used field form should still be exported",
        arForm.getFieldFormList().stream().anyMatch(ff -> NUMBER.getType().equals(ff.getType())));
  }

  /**
   * RSPAC-1793 regression guard, restated against the correct discriminator: a deleted "ghost"
   * field form that no document field references must NOT be exported. The document never
   * instantiated it, so it is absent from the document's fields and therefore from the exported
   * form.
   */
  @Test
  public void ghostDeletedFieldFormNotUsedByDocumentIsNotExported_RSPAC_1793() {
    form.setId(1L);
    StructuredDocument doc = createAnySD(form);
    int documentFieldCount = doc.getFields().size();
    // a deleted field form hangs off the form but no document field references it
    NumberFieldForm ghost = new NumberFieldForm("ghost-number");
    ghost.setDeleted(true);
    form.addFieldForm(ghost);

    ArchivalForm arForm = factory.createArchivalFormForDocument(doc, "form");

    assertEquals(documentFieldCount, arForm.getFieldFormList().size());
    assertTrue(
        "ghost field form unused by the document should not be exported",
        arForm.getFieldFormList().stream().noneMatch(ff -> "ghost-number".equals(ff.getName())));
  }

  @Test
  public void archiveNfs() throws IllegalArgumentException, IllegalAccessException {
    NfsFileStore fileStore = createAnyNfsFileStore(createAnyUser("any"));
    fileStore.setId(1L);
    fileStore.getFileSystem().setId(2L);
    NfsElement nfsElement = new NfsElement(1L, "/a/path");
    ArchivalNfsFile archiveNfs = factory.createArchivalNfs(fileStore, nfsElement);
    assertEquals(fileStore.getFileSystem().getId(), archiveNfs.getFileSystemId());
    assertEquals(fileStore.getFileSystem().getUrl(), archiveNfs.getFileSystemUrl());
    assertEquals(fileStore.getId(), archiveNfs.getFileStoreId());
    assertEquals(fileStore.getPath(), archiveNfs.getFileStorePath());
    assertEquals(nfsElement.getPath(), archiveNfs.getRelativePath());
  }

  @Test
  public void archivalChoiceFieldHasPrintableVersionSetCorrectly() {
    ChoiceFieldForm choiceFieldForm = new ChoiceFieldForm();
    choiceFieldForm.setChoiceOptions("fieldChoices=a&fieldChoices=b&fieldChoices=c");
    choiceFieldForm.setDefaultChoiceOption("fieldSelectedChoices=a&fieldSelectedChoices=b");

    RSForm form = new RSForm("form", "desc", createAnyUser("user"));
    form.setFieldForms(List.of(choiceFieldForm));

    StructuredDocument structuredDocument = createAnySD(form);
    structuredDocument.setId(1L);
    structuredDocument.getFields().get(0).setId(1L);

    ArchivalDocument archivalDocument = factory.createArchivalDocument(structuredDocument);

    assertEquals("a, b", archivalDocument.getListFields().get(0).getFieldDataPrintable());
  }
}

package com.researchspace.archive.model;

import static com.researchspace.model.field.FieldType.NUMBER;
import static com.researchspace.testutils.NetFilesTestFactory.createAnyNfsFileStore;
import static com.researchspace.testutils.TestFactory.createAnySD;
import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalFieldForm;
import com.researchspace.archive.ArchivalForm;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.ArchiveComment;
import com.researchspace.core.util.XMLReadWriteUtils;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.FieldTestUtils;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ArchiveModelFactoryTest {
  RSForm form;
  ArchiveModelFactory factory;
  User anyUser;

  @BeforeEach
  public void setUp() throws Exception {
    factory = new ArchiveModelFactory();
    setUpFormwIthAllFormFieldDataSet();
    anyUser = TestFactory.createAnyUser("any");
  }

  private void setUpFormwIthAllFormFieldDataSet() {
    form = TestFactory.createAnyForm();
  }

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
    ArchivalForm arForm = factory.createArchivalForm(form, "form");
    assertThat(arForm.getFieldFormList()).hasSize(4);
  }

  @Test
  public void fieldKeysAndDisplayRulesAreExportedAndReadBack(@TempDir File folder)
      throws Exception {
    form.setId(1L);
    RadioFieldForm gmo = new RadioFieldForm("GMO?");
    gmo.setRadioOption("fieldChoices=Yes&fieldChoices=No");
    gmo.setFieldKey("6f1c2e0a-0000-4000-8000-000000000001");
    gmo.setColumnIndex(1);
    TextFieldForm permit = new TextFieldForm("Permit");
    permit.setFieldKey("6f1c2e0a-0000-4000-8000-000000000002");
    permit.setDisplayRule(
        "{\"sourceFieldKey\":\"6f1c2e0a-0000-4000-8000-000000000001\",\"operator\":\"HAS_VALUE\"}");
    permit.setColumnIndex(2);
    form.addFieldForm(gmo);
    form.addFieldForm(permit);

    ArchivalForm exported = factory.createArchivalForm(form, "form");
    File xml = new File(folder, "form.xml");
    XMLReadWriteUtils.toXML(xml, exported, ArchivalForm.class);
    ArchivalForm read = XMLReadWriteUtils.fromXML(xml, ArchivalForm.class, null, null);

    List<ArchivalFieldForm> fields = read.getFieldFormList();
    assertThat(fields).hasSize(3);
    assertNull(fields.get(0).getDisplayRule());
    assertEquals(gmo.getFieldKey(), fields.get(1).getFieldKey());
    assertNull(fields.get(1).getDisplayRule());
    assertEquals(permit.getFieldKey(), fields.get(2).getFieldKey());
    assertEquals(permit.getDisplayRule(), fields.get(2).getDisplayRule());
  }

  @Test
  public void testNumberDefaultsNull_RSPAC1133() {
    form.setId(1L);
    NumberFieldForm nff = new NumberFieldForm("number");
    form.addFieldForm(nff);
    ArchivalForm arForm = factory.createArchivalForm(form, "form");
    assertThat(arForm.getFieldFormList()).hasSize(2);
  }

  @Test
  public void deletedFormFieldsAreNotExported_RSPAC_1793() {
    form.setId(1L);
    NumberFieldForm nff = new NumberFieldForm("number");
    form.addFieldForm(nff);
    ArchivalForm arForm = factory.createArchivalForm(form, "form");
    assertThat(arForm.getFieldFormList()).hasSize(2);
    nff.setDeleted(true);

    arForm = factory.createArchivalForm(form, "form");
    assertThat(arForm.getFieldFormList()).hasSize(1);
    assertThat(arForm.getFieldFormList().stream())
        .noneMatch(ff -> NUMBER.getType().equals(ff.getType()));
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

  @Test
  public void testMandatoryFields() {
    TextFieldForm tff = TestFactory.createTextFieldForm();
    tff.setMandatory(true);
    DateFieldForm sff = TestFactory.createDateFieldForm();
    sff.setMandatory(false);
    RSForm form = new RSForm("form", "desc", createAnyUser("user"));
    form.addAllFieldForms(tff, sff);
    form.setId(1L);
    ArchivalForm aform = factory.createArchivalForm(form, "asd");
    assertTrue(aform.getFieldFormList().get(0).isMandatory());
    assertFalse(aform.getFieldFormList().get(1).isMandatory());
  }

  @Test
  public void archivalRadioFieldKeepsPickListSettings() {
    RadioFieldForm radioFieldForm = new RadioFieldForm("radio");
    radioFieldForm.setShowAsPickList(true);
    radioFieldForm.setSortAlphabetic(true);
    RSForm form = new RSForm("form", "desc", createAnyUser("user"));
    form.addAllFieldForms(radioFieldForm);
    form.setId(1L);

    ArchivalForm aform = factory.createArchivalForm(form, "asd");

    assertTrue(aform.getFieldFormList().get(0).isDisplayAsPickList());
    assertTrue(aform.getFieldFormList().get(0).isSortAlphabetic());
  }
}

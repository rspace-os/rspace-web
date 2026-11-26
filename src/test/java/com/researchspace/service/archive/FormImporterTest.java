package com.researchspace.service.archive;

import static com.researchspace.testutils.RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;

import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalFieldForm;
import com.researchspace.archive.ArchivalForm;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.FieldFormDao;
import com.researchspace.dao.IconImgDao;
import com.researchspace.model.User;
import com.researchspace.model.field.AttachmentFieldForm;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.ReferenceFieldForm;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.field.URIFieldForm;
import com.researchspace.model.record.FormState;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.FormManager;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FormImporterTest {
  @TempDir public File tempFolder;
  @Mock IconImgDao imgDao;
  @Mock FormManager formMgr;
  @Mock FieldFormDao fieldFormDao;
  @InjectMocks FormImporterImpl importer;

  final long OLD_FORM_ID = 123L;

  @Test
  public void testFindIconFile() throws IOException {
    File iconFile = setUpFolderWithValidIconFile();
    ArchivalDocumentParserRef ref = new ArchivalDocumentParserRef();
    ArchivalForm form = new ArchivalForm();
    ref.setArchivalForm(form);
    ref.setFileList(TransformerUtils.toList(iconFile));
    assertThat(importer.findIconFile(ref, 123L)).isPresent();
  }

  @Test
  public void testIconSaved() throws IOException {
    File iconFile = setUpFolderWithValidIconFile();
    ArchivalDocumentParserRef ref = new ArchivalDocumentParserRef();
    ArchivalForm form = new ArchivalForm();

    form.setFormId(OLD_FORM_ID);
    ref.setArchivalForm(form);
    ref.setFileList(TransformerUtils.toList(iconFile));
    Mockito.when(imgDao.saveIconEntity(Mockito.any(IconEntity.class), Mockito.eq(Boolean.TRUE)))
        .thenReturn(new IconEntity());
    assertThat(importer.createFormIcon(ref, 2L)).isPresent();
    Mockito.verify(imgDao).saveIconEntity(Mockito.any(IconEntity.class), Mockito.eq(Boolean.TRUE));
  }

  @Test
  public void testIconSaveFailsGracefullyIfImageFileCannotBeParsed() throws IOException {
    File iconFile = setUpFolderWithInValidIconFile();
    ArchivalDocumentParserRef ref = new ArchivalDocumentParserRef();
    ArchivalForm form = new ArchivalForm();

    form.setFormId(OLD_FORM_ID);
    ref.setArchivalForm(form);
    ref.setFileList(TransformerUtils.toList(iconFile));
    assertThat(importer.createFormIcon(ref, 2L)).isNotPresent();
    Mockito.verify(imgDao, never())
        .saveIconEntity(Mockito.any(IconEntity.class), Mockito.eq(Boolean.TRUE));
  }

  @Test
  public void recreatesUriReferenceAndAttachmentFieldFormsWithTheirTypes() {
    User user = TestFactory.createAnyUser("importer");
    Mockito.when(formMgr.create(user)).thenReturn(new RSForm("new", "", user));
    Mockito.when(fieldFormDao.save(Mockito.any(FieldForm.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ArchivalForm archivalForm = new ArchivalForm();
    archivalForm.setFormId(OLD_FORM_ID);
    archivalForm.setName("exported form");
    archivalForm.setType(FormType.NORMAL.name());
    archivalForm.setPublishingState(FormState.PUBLISHED.name());
    archivalForm.setFormVersion("1");
    ArchivalFieldForm stringField = archivalFieldForm("string", FieldType.STRING, true, 4);
    stringField.setDefaultValue("archived default");
    archivalForm
        .getFieldFormList()
        .addAll(
            List.of(
                archivalFieldForm("uri", FieldType.URI, true, 0),
                archivalFieldForm("reference", FieldType.REFERENCE, false, 1),
                archivalFieldForm("attachment", FieldType.ATTACHMENT, true, 2),
                archivalFieldForm("text", FieldType.TEXT, false, 3),
                stringField));
    ArchivalDocumentParserRef ref = new ArchivalDocumentParserRef();
    ref.setArchivalForm(archivalForm);

    List<FieldForm> imported = importer.makeRSForm(ref, user).getFieldForms();

    assertThat(imported)
        .extracting(FieldForm::getClass)
        .containsExactly(
            URIFieldForm.class,
            ReferenceFieldForm.class,
            AttachmentFieldForm.class,
            TextFieldForm.class,
            StringFieldForm.class);
    assertThat(imported)
        .extracting(FieldForm::getName)
        .containsExactly("uri", "reference", "attachment", "text", "string");
    assertThat(imported)
        .extracting(FieldForm::isMandatory)
        .containsExactly(true, false, true, false, true);
    assertEquals("archived default", ((StringFieldForm) imported.get(4)).getDefaultStringValue());
    for (int i = 0; i < imported.size(); i++) {
      assertEquals(i, imported.get(i).getColumnIndex());
    }
  }

  private ArchivalFieldForm archivalFieldForm(
      String name, FieldType type, boolean mandatory, int columnIndex) {
    ArchivalFieldForm fieldForm = new ArchivalFieldForm();
    fieldForm.setName(name);
    fieldForm.setType(type.getType());
    fieldForm.setMandatory(mandatory);
    fieldForm.setColumnIndex(columnIndex);
    return fieldForm;
  }

  // creates an icon file in test folder.
  private File setUpFolderWithValidIconFile() throws IOException {
    File icon = makeIconFile();
    // use real image
    IOUtils.copy(
        getInputStreamOnFromTestResourcesFolder("biggerLogo.png"), new FileOutputStream(icon));
    return icon;
  }

  private File setUpFolderWithInValidIconFile() throws IOException {
    File icon = makeIconFile();
    // use real image
    FileUtils.writeByteArrayToFile(icon, new byte[] {1, 2, 3, 4, 5});
    return icon;
  }

  private File makeIconFile() {
    File f = tempFolder;
    File icon = new File(f, "formIcon_" + OLD_FORM_ID + ".png");
    return icon;
  }
}

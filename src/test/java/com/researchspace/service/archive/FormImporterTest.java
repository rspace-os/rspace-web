package com.researchspace.service.archive;

import static com.researchspace.testutils.RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;

import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalFieldForm;
import com.researchspace.archive.ArchivalForm;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.FieldFormDao;
import com.researchspace.dao.IconImgDao;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.FormManager;
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
  @InjectMocks FormImporterImpl importer;

  @Mock FieldFormDao fieldFormDao;

  final long OLD_FORM_ID = 123L;
  static final String GMO_KEY = "6f1c2e0a-0000-4000-8000-000000000001";
  static final String PERMIT_KEY = "6f1c2e0a-0000-4000-8000-000000000002";

  @Test
  public void importKeepsFieldKeysAndDisplayRules() {
    String rule = showWhenGmo("Yes");
    RSForm form = importForm(radio(GMO_KEY), string(PERMIT_KEY, rule));

    assertThat(form.getFieldForms())
        .extracting(FieldForm::getFieldKey)
        .containsExactly(GMO_KEY, PERMIT_KEY);
    assertThat(form.getFieldForms())
        .extracting(FieldForm::getDisplayRule)
        .containsExactly(null, rule);
  }

  @Test
  public void importDropsRulesThatFailValidationButKeepsKeys() {
    RSForm form = importForm(radio(GMO_KEY), string(PERMIT_KEY, showWhenGmo("Maybe")));

    assertThat(form.getFieldForms())
        .extracting(FieldForm::getFieldKey)
        .containsExactly(GMO_KEY, PERMIT_KEY);
    assertThat(form.getFieldForms()).extracting(FieldForm::getDisplayRule).containsOnlyNulls();
  }

  @Test
  public void importDropsUnreadableRules() {
    RSForm form = importForm(radio(GMO_KEY), string(PERMIT_KEY, "{not json"));

    assertThat(form.getFieldForms())
        .extracting(FieldForm::getFieldKey)
        .containsExactly(GMO_KEY, PERMIT_KEY);
    assertThat(form.getFieldForms()).extracting(FieldForm::getDisplayRule).containsOnlyNulls();
  }

  @Test
  public void importDropsDuplicatedOrMalformedKeysTogetherWithRules() {
    RSForm duplicated = importForm(radio(GMO_KEY), string(GMO_KEY, showWhenGmo("Yes")));
    assertThat(duplicated.getFieldForms()).extracting(FieldForm::getFieldKey).containsOnlyNulls();
    assertThat(duplicated.getFieldForms())
        .extracting(FieldForm::getDisplayRule)
        .containsOnlyNulls();

    RSForm malformed = importForm(radio("GMO"), string(PERMIT_KEY, null));
    assertThat(malformed.getFieldForms()).extracting(FieldForm::getFieldKey).containsOnlyNulls();
  }

  @Test
  public void olderArchivesImportWithoutKeysOrRules() {
    RSForm form = importForm(radio(null), string(null, null));
    assertThat(form.getFieldForms()).extracting(FieldForm::getFieldKey).containsOnlyNulls();
    assertThat(form.getFieldForms()).extracting(FieldForm::getDisplayRule).containsOnlyNulls();
  }

  private RSForm importForm(ArchivalFieldForm... fields) {
    ArchivalForm archived = new ArchivalForm();
    archived.setFormId(OLD_FORM_ID);
    archived.setType("NORMAL");
    archived.setName("archived");
    archived.setModificationDate(1L);
    archived.setPublishingState("PUBLISHED");
    archived.setFormVersion("1");
    archived.getFieldFormList().addAll(List.of(fields));
    ArchivalDocumentParserRef ref = new ArchivalDocumentParserRef();
    ref.setArchivalForm(archived);
    RSForm created = new RSForm();
    created.setId(2L);
    Mockito.when(formMgr.create(null)).thenReturn(created);
    Mockito.when(fieldFormDao.save(Mockito.any(FieldForm.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    return importer.makeRSForm(ref, null);
  }

  private static String showWhenGmo(String value) {
    return "{\"sourceFieldKey\":\""
        + GMO_KEY
        + "\",\"operator\":\"IS_ANY_OF\",\"values\":[\""
        + value
        + "\"]}";
  }

  private static ArchivalFieldForm radio(String key) {
    ArchivalFieldForm radio = new ArchivalFieldForm();
    radio.setType("Radio");
    radio.setName("GMO?");
    radio.setOptions("fieldChoices=Yes&fieldChoices=No");
    radio.setSelection("");
    radio.setFieldKey(key);
    return radio;
  }

  private static ArchivalFieldForm string(String key, String rule) {
    ArchivalFieldForm string = new ArchivalFieldForm();
    string.setType("String");
    string.setName("Permit");
    string.setDefaultValue("");
    string.setFieldKey(key);
    string.setDisplayRule(rule);
    return string;
  }

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

package com.researchspace.service.archive;

import static com.researchspace.testutils.RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;

import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalForm;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.IconImgDao;
import com.researchspace.model.record.IconEntity;
import com.researchspace.service.FormManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

  final long OLD_FORM_ID = 123L;

  @Test
  public void testFindIconFile() throws IOException {
    File iconFile = setUpFolderWithValidIconFile();
    ArchivalDocumentParserRef ref = new ArchivalDocumentParserRef();
    ArchivalForm form = new ArchivalForm();
    ref.setArchivalForm(form);
    ref.setFileList(TransformerUtils.toList(iconFile));
    assertTrue(importer.findIconFile(ref, 123L).isPresent());
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
    assertTrue(importer.createFormIcon(ref, 2L).isPresent());
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
    assertFalse(importer.createFormIcon(ref, 2L).isPresent());
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

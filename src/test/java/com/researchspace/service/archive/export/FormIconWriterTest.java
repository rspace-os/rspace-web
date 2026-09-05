package com.researchspace.service.archive.export;

import static com.researchspace.core.testutil.FileTestUtils.assertFolderHasFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;

import com.researchspace.dao.IconImgDao;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FormIconWriterTest {
  @TempDir public File tempFolder;
  @Mock IconImgDao imgDao;
  @InjectMocks FormIconWriter writer;
  StructuredDocument doc;
  File imageFile;
  final long ICON_ID = 1L;

  @BeforeEach
  public void setUp() throws Exception {
    doc = TestFactory.createAnySD();
    imageFile = RSpaceTestUtils.getResource("mainLogo3.png");
  }

  @Test
  public void testWriteFormIconEntityFileHandlesMissingIconFile() throws IOException {
    doc.getForm().setIconId(ICON_ID);
    doc.getForm().setId(2L);
    Mockito.when(imgDao.getIconEntity(ICON_ID)).thenReturn(null);
    writer.writeFormIconEntityFile(doc.getForm(), tempFolder);
    assertNFilesInExportFolder(0);
  }

  @Test
  public void testWriteFormIconEntityFileHandlesNullIconFile() throws IOException {
    doc.getForm().setIconId(-1L);
    doc.getForm().setId(2L);
    writer.writeFormIconEntityFile(doc.getForm(), tempFolder);
    Mockito.verify(imgDao, never()).getIconEntity(-1L);
    assertNFilesInExportFolder(0);
  }

  @Test
  public void testWriteFormIconEntityFile() throws IOException {
    IconEntity icon = new IconEntity();
    icon.setIconImage(FileUtils.readFileToByteArray(imageFile));
    icon.setId(ICON_ID);
    icon.setImgType("png");
    doc.getForm().setIconId(ICON_ID);
    doc.getForm().setId(2L);
    Mockito.when(imgDao.getIconEntity(ICON_ID)).thenReturn(icon);
    writer.writeFormIconEntityFile(doc.getForm(), tempFolder);
    assertNFilesInExportFolder(1);
    assertFolderHasFile(tempFolder, "formIcon_2.png");
  }

  @Test
  public void testGetFormIconFileName() throws IOException {
    IconEntity icon = new IconEntity();
    icon.setId(ICON_ID);
    icon.setImgType("png");
    assertEquals("formIcon_1234.png", writer.getFormIconFileName(icon, 1234L));
    assertTrue(writer.getFormIconFileName(icon, 1234L).matches(FormIconWriter.FORM_ICON_REGEX));
  }

  private void assertNFilesInExportFolder(int expectedFileCount) {
    assertEquals(
        expectedFileCount,
        FileUtils.listFiles(tempFolder, FileFilterUtils.trueFileFilter(), null).size());
  }
}

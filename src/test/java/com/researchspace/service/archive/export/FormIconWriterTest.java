package com.researchspace.service.archive.export;

import static com.researchspace.core.testutil.FileTestUtils.assertFolderHasFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;

import com.researchspace.dao.IconImgDao;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class FormIconWriterTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  public @Rule TemporaryFolder tempFolder = new TemporaryFolder();
  @Mock IconImgDao imgDao;
  @InjectMocks FormIconWriter writer;
  StructuredDocument doc;
  File imageFile;
  final long ICON_ID = 1L;

  @Before
  public void setUp() throws Exception {
    doc = TestFactory.createAnySD();
    imageFile = RSpaceTestUtils.getResource("mainLogoN2.png");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testWriteFormIconEntityFileHandlesMissingIconFile() throws IOException {
    doc.getForm().setIconId(ICON_ID);
    doc.getForm().setId(2L);
    Mockito.when(imgDao.getIconEntity(ICON_ID)).thenReturn(null);
    writer.writeFormIconEntityFile(doc.getForm(), tempFolder.getRoot());
    assertNFilesInExportFolder(0);
  }

  @Test
  public void testWriteFormIconEntityFileHandlesNullIconFile() throws IOException {
    doc.getForm().setIconId(-1L);
    doc.getForm().setId(2L);
    writer.writeFormIconEntityFile(doc.getForm(), tempFolder.getRoot());
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
    writer.writeFormIconEntityFile(doc.getForm(), tempFolder.getRoot());
    assertNFilesInExportFolder(1);
    assertFolderHasFile(tempFolder.getRoot(), "formIcon_2.png");
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
        FileUtils.listFiles(tempFolder.getRoot(), FileFilterUtils.trueFileFilter(), null).size());
  }
}

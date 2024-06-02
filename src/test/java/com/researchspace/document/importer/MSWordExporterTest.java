package com.researchspace.document.importer;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalStateExceptionThrown;
import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class MSWordExporterTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock DocumentConversionService docConverter;
  @Mock RSpaceDocumentCreator creator;
  File inputFile;
  File outputFromConverter;

  MSWordImporter wordImporter;
  User any;
  Folder targetFolder;

  @Before
  public void setUp() throws Exception {
    wordImporter = new MSWordImporter(docConverter);
    wordImporter.setCreator(creator);
    inputFile = RSpaceTestUtils.getResource("word2rspace/dsRNAi/dsRNAi in Drosophila  cells.doc");
    // has <img> tag to test image parsing
    outputFromConverter =
        RSpaceTestUtils.getResource("word2rspace/dsRNAi/dsRNAi-in-Drosophila-cells.html");
    any = TestFactory.createAnyUser("any");
    targetFolder = TestFactory.createAFolder("afolder", any);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void failedConversionThrowsISE() throws IOException {
    FileInputStream fis = new FileInputStream(inputFile);
    ConversionResult result = new ConversionResult("error message");
    String docName = getBaseName(inputFile.getName());
    StructuredDocument doc = TestFactory.createAnySD();
    when(docConverter.convert(any(Convertible.class), eq("html"), any(File.class)))
        .thenReturn(result);

    Mockito.verify(creator, Mockito.never())
        .create(
            any(ContentProvider.class), eq(targetFolder), any(Folder.class), eq(docName), eq(any));
    assertIllegalStateExceptionThrown(
        () -> wordImporter.create(fis, any, targetFolder, null, inputFile.getName()));
  }

  @Test
  public void happyCaseCreate() throws IOException {
    FileInputStream fis = new FileInputStream(inputFile);
    ConversionResult result = new ConversionResult(outputFromConverter, "text/html");
    String docName = getBaseName(inputFile.getName());
    StructuredDocument doc = TestFactory.createAnySD();
    when(docConverter.convert(any(Convertible.class), eq("html"), any(File.class)))
        .thenReturn(result);
    when(creator.create(
            any(ContentProvider.class), eq(targetFolder), Mockito.isNull(), eq(docName), eq(any)))
        .thenReturn(doc);
    assertEquals(doc, wordImporter.create(fis, any, targetFolder, null, inputFile.getName()));
    fis.close();
  }

  @Test
  public void happyCaseReplace() throws IOException, DocumentAlreadyEditedException {
    FileInputStream fis = new FileInputStream(inputFile);
    ConversionResult result = new ConversionResult(outputFromConverter, "text/html");
    String docName = getBaseName(inputFile.getName());
    StructuredDocument toUpdate = TestFactory.createAnySD();
    when(docConverter.convert(any(Convertible.class), eq("html"), any(File.class)))
        .thenReturn(result);
    when(creator.replace(eq(toUpdate.getId()), any(ContentProvider.class), eq(docName), eq(any)))
        .thenReturn(toUpdate);
    assertEquals(toUpdate, wordImporter.replace(fis, any, toUpdate.getId(), inputFile.getName()));
    fis.close();
  }
}

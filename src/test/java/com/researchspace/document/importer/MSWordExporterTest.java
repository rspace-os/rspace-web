package com.researchspace.document.importer;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalStateExceptionThrown;
import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MSWordExporterTest {
  @Mock DocumentConversionService docConverter;
  @Mock RSpaceDocumentCreator creator;
  File inputFile;
  File outputFromConverter;

  MSWordImporter wordImporter;
  User any;
  Folder targetFolder;

  @BeforeEach
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
  public void failedReplacementConversionThrowsISE() throws Exception {
    ConversionResult result = new ConversionResult("error message");
    when(docConverter.convert(any(Convertible.class), eq("html"), any(File.class)))
        .thenReturn(result);

    try (FileInputStream fis = new FileInputStream(inputFile)) {
      assertIllegalStateExceptionThrown(
          () -> wordImporter.replace(fis, any, 123L, inputFile.getName()));
    }
    verify(creator, never()).replace(any(Long.class), any(ContentProvider.class), any(), any());
  }

  @Test
  public void happyCaseReplacementForwardsTargetAndConvertedProvider() throws Exception {
    ConversionResult result = new ConversionResult(outputFromConverter, "text/html");
    String docName = getBaseName(inputFile.getName());
    StructuredDocument doc = TestFactory.createAnySD();
    Long targetId = 123L;
    when(docConverter.convert(any(Convertible.class), eq("html"), any(File.class)))
        .thenReturn(result);
    when(creator.replace(eq(targetId), any(ContentProvider.class), eq(docName), eq(any)))
        .thenReturn(doc);

    try (FileInputStream fis = new FileInputStream(inputFile)) {
      assertEquals(doc, wordImporter.replace(fis, any, targetId, inputFile.getName()));
    }

    ArgumentCaptor<ContentProvider> provider = ArgumentCaptor.forClass(ContentProvider.class);
    verify(creator).replace(eq(targetId), provider.capture(), eq(docName), eq(any));
    assertTrue(provider.getValue() instanceof HTMLContentProvider);
    assertTrue(provider.getValue().getContentFolder().isDirectory());
  }
}

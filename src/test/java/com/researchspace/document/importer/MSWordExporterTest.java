package com.researchspace.document.importer;

import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    String inputFileName = inputFile.getName();
    assertThrows(
        IllegalStateException.class,
        () -> wordImporter.create(fis, any, targetFolder, null, inputFileName));
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
      assertThrows(
          IllegalStateException.class,
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
    assertFalse(provider.getValue().getContentFolder().exists());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void replacementExtractsDataUriImagesAndCleansTemporaryFiles(boolean creatorFails)
      throws Exception {
    String png =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
    AtomicReference<Path> tempInput = new AtomicReference<>();
    AtomicReference<Path> tempFolder = new AtomicReference<>();
    when(docConverter.convert(any(Convertible.class), eq("html"), any(File.class)))
        .thenAnswer(
            invocation -> {
              Convertible input = invocation.getArgument(0);
              File output = invocation.getArgument(2);
              tempInput.set(Path.of(java.net.URI.create(input.getFileUri())));
              tempFolder.set(output.toPath().getParent());
              Files.writeString(
                  output.toPath(),
                  "<html><body><img src='data:image/png;base64," + png + "'></body></html>");
              return new ConversionResult(output, "text/html");
            });
    StructuredDocument doc = TestFactory.createAnySD();
    when(creator.replace(eq(123L), any(ContentProvider.class), eq("image"), eq(any)))
        .thenAnswer(
            invocation -> {
              ContentProvider provider = invocation.getArgument(1);
              var images = provider.getImages();
              assertEquals(1, images.size());
              Path image = provider.getContentFolder().toPath().resolve(images.first().attr("src"));
              assertEquals(Base64.getDecoder().decode(png).length, Files.size(image));
              if (creatorFails) {
                throw new IOException("test creator failure");
              }
              return doc;
            });

    try (var input = new ByteArrayInputStream(new byte[] {1})) {
      if (creatorFails) {
        assertThrows(IOException.class, () -> wordImporter.replace(input, any, 123L, "image.docx"));
      } else {
        assertEquals(doc, wordImporter.replace(input, any, 123L, "image.docx"));
      }
    }

    assertFalse(Files.exists(tempInput.get()));
    assertFalse(Files.exists(tempFolder.get()));
  }
}

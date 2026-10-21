package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveValidatorTest {

  private final ArchiveValidator validator = new ArchiveValidator();

  @TempDir Path tempDir;

  @Test
  void acceptsExistingDocxAndOdtParityFixtures() {
    assertDoesNotThrow(() -> validator.validate(fixture("PowerPasteTesting_RSpace.docx"), "docx"));
    assertDoesNotThrow(() -> validator.validate(fixture("ODT.odt"), "odt"));
  }

  @Test
  void acceptsExistingPresentationAndSpreadsheetFixtures() {
    assertDoesNotThrow(() -> validator.validate(fixture("ODP.pptx"), "pptx"));
    assertDoesNotThrow(() -> validator.validate(fixture("simpleExcel.xlsx"), "xlsx"));
  }

  @Test
  void acceptsSmallHighlyCompressibleWorksheet() throws IOException {
    Path archive = tempDir.resolve("small-compressible.xlsx");
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
      entry(
          output, "xl/worksheets/sheet1.xml", "<worksheet>" + " ".repeat(100_000) + "</worksheet>");
    }
    assertDoesNotThrow(() -> validator.validate(archive, "xlsx"));
  }

  @Test
  void rejectsHighExpansionRatioForSpreadsheetAndPresentationPackages() throws IOException {
    byte[] zeros = new byte[1024 * 1024];
    String[][] bombEntries = {
      {"xlsx", "xl/worksheets/sheet1.xml"},
      {"pptx", "ppt/slides/slide1.xml"},
      {"ods", "content.xml"},
      {"odp", "content.xml"}
    };

    for (String[] bombEntry : bombEntries) {
      String extension = bombEntry[0];
      Path archive = tempDir.resolve("ratio-bomb." + extension);
      try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
        entry(output, bombEntry[1], zeros);
      }

      ConversionException exception =
          assertThrows(
              ConversionException.class, () -> validator.validate(archive, extension), extension);

      assertEquals(ConversionError.INPUT_TOO_LARGE, exception.error(), extension);
    }
  }

  @Test
  void acceptsChartWorkbookEmbeddingsInPresentationAndSpreadsheetPackages() throws IOException {
    byte[] embeddedWorkbook = minimalWorkbookArchive();

    Path presentation = tempDir.resolve("chart-embedding.pptx");
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(presentation))) {
      entry(output, "ppt/charts/chart1.xml", "<chartSpace/>");
      entry(output, "ppt/embeddings/Microsoft_Excel_Worksheet1.xlsx", embeddedWorkbook);
    }

    Path spreadsheet = tempDir.resolve("chart-embedding.xlsx");
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(spreadsheet))) {
      entry(output, "xl/charts/chart1.xml", "<chartSpace/>");
      entry(output, "xl/embeddings/Microsoft_Excel_Worksheet1.xlsx", embeddedWorkbook);
    }

    assertDoesNotThrow(() -> validator.validate(presentation, "pptx"));
    assertDoesNotThrow(() -> validator.validate(spreadsheet, "xlsx"));
  }

  @Test
  void rejectsDocxWorkbookEmbeddings() throws IOException {
    Path archive = tempDir.resolve("embedded-workbook.docx");
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
      entry(output, "[Content_Types].xml", "<Types/>");
      entry(output, "word/document.xml", "<document/>");
      entry(output, "word/embeddings/Microsoft_Excel_Worksheet1.xlsx", minimalWorkbookArchive());
    }

    ConversionException exception =
        assertThrows(ConversionException.class, () -> validator.validate(archive, "docx"));

    assertEquals(ConversionError.INPUT_INVALID, exception.error());
  }

  @Test
  void rejectsTraversalBeforeLibreOfficeStarts() throws IOException {
    Path archive = tempDir.resolve("traversal.docx");
    try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
      output.putNextEntry(new ZipEntry("../payload"));
      output.write(1);
    }

    ConversionException exception =
        assertThrows(ConversionException.class, () -> validator.validate(archive, "docx"));

    assertEquals(ConversionError.INPUT_INVALID, exception.error());
  }

  @Test
  void acceptsExternalDocxHyperlink() throws IOException {
    Path archive = tempDir.resolve("hyperlink.docx");
    try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
      entry(output, "[Content_Types].xml", "<Types/>");
      entry(output, "word/document.xml", "<document/>");
      entry(
          output,
          "word/_rels/document.xml.rels",
          "<Relationships><Relationship TargetMode='External'"
              + " Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink'"
              + " Target='https://example.test'/></Relationships>");
    }

    assertDoesNotThrow(() -> validator.validate(archive, "docx"));
  }

  @Test
  void rejectsExternalDocxRelationship() throws IOException {
    Path archive = tempDir.resolve("external.docx");
    try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
      entry(output, "[Content_Types].xml", "<Types/>");
      entry(output, "word/document.xml", "<document/>");
      entry(
          output,
          "word/_rels/document.xml.rels",
          "<Relationships><Relationship TargetMode='External' Target='https://example.test'/></Relationships>");
    }

    ConversionException exception =
        assertThrows(ConversionException.class, () -> validator.validate(archive, "docx"));

    assertEquals(ConversionError.INPUT_INVALID, exception.error());
  }

  @Test
  void rejectsExternalOpenDocumentReference() throws IOException {
    Path archive = tempDir.resolve("external.odt");
    try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
      storedEntry(output, "mimetype", "application/vnd.oasis.opendocument.text");
      entry(
          output,
          "content.xml",
          "<office xmlns:xlink='http://www.w3.org/1999/xlink'><image xlink:href='https://example.test/image.png'/></office>");
      entry(
          output,
          "META-INF/manifest.xml",
          "<manifest><file-entry full-path='/' media-type='application/vnd.oasis.opendocument.text'/></manifest>");
    }

    ConversionException exception =
        assertThrows(ConversionException.class, () -> validator.validate(archive, "odt"));

    assertEquals(ConversionError.INPUT_INVALID, exception.error());
  }

  private static byte[] minimalWorkbookArchive() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream output = new ZipOutputStream(bytes)) {
      entry(output, "xl/workbook.xml", "<workbook/>");
    }
    return bytes.toByteArray();
  }

  private static void entry(ZipOutputStream output, String name, String value) throws IOException {
    entry(output, name, value.getBytes());
  }

  private static void entry(ZipOutputStream output, String name, byte[] value) throws IOException {
    output.putNextEntry(new ZipEntry(name));
    output.write(value);
    output.closeEntry();
  }

  private static void storedEntry(ZipOutputStream output, String name, String value)
      throws IOException {
    byte[] bytes = value.getBytes();
    CRC32 crc = new CRC32();
    crc.update(bytes);
    ZipEntry entry = new ZipEntry(name);
    entry.setMethod(ZipEntry.STORED);
    entry.setSize(bytes.length);
    entry.setCompressedSize(bytes.length);
    entry.setCrc(crc.getValue());
    output.putNextEntry(entry);
    output.write(bytes);
    output.closeEntry();
  }

  private Path fixture(String name) {
    Path fromRepositoryRoot = Path.of("src/test/resources/TestResources", name);
    if (Files.exists(fromRepositoryRoot)) {
      return fromRepositoryRoot;
    }
    return Path.of("../../src/test/resources/TestResources", name);
  }
}

package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ArchiveValidatorTest {

  private final ArchiveValidator validator = new ArchiveValidator();

  @Test
  void acceptsExistingDocxAndOdtParityFixtures() {
    assertDoesNotThrow(() -> validator.validate(fixture("PowerPasteTesting_RSpace.docx"), "docx"));
    assertDoesNotThrow(() -> validator.validate(fixture("ODT.odt"), "odt"));
  }

  @Test
  void rejectsTraversalBeforeLibreOfficeStarts() throws IOException {
    Path archive = Files.createTempFile("traversal-", ".docx");
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
    Path archive = Files.createTempFile("hyperlink-", ".docx");
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
    Path archive = Files.createTempFile("external-", ".docx");
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
    Path archive = Files.createTempFile("external-", ".odt");
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

  private static void entry(ZipOutputStream output, String name, String value) throws IOException {
    output.putNextEntry(new ZipEntry(name));
    output.write(value.getBytes());
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

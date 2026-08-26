package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.documentconversion.validation.SafeOfficeArchiveValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeOfficeArchiveValidatorTest {

  @TempDir Path directory;

  @Test
  void acceptsExternalDocxHyperlink() throws Exception {
    Path archive = directory.resolve("hyperlink.docx");
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

    assertDoesNotThrow(() -> SafeOfficeArchiveValidator.validateDocx(archive));
  }

  @Test
  void rejectsExternalDocxRelationship() throws Exception {
    Path archive = directory.resolve("external.docx");
    try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
      entry(output, "[Content_Types].xml", "<Types/>");
      entry(output, "word/document.xml", "<document/>");
      entry(
          output,
          "word/_rels/document.xml.rels",
          "<Relationships><Relationship TargetMode='External'"
              + " Target='https://example.test'/></Relationships>");
    }

    IOException error =
        assertThrows(IOException.class, () -> SafeOfficeArchiveValidator.validateDocx(archive));

    assertEquals(DocumentConversionError.INPUT_INVALID.code(), error.getMessage());
  }

  @Test
  void rejectsNestedArchive() throws Exception {
    Path archive = directory.resolve("nested.docx");
    try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
      entry(output, "[Content_Types].xml", "<Types/>");
      entry(output, "word/document.xml", "<document/>");
      entry(output, "word/payload.zip", "payload");
    }

    assertThrows(IOException.class, () -> SafeOfficeArchiveValidator.validateDocx(archive));
  }

  @Test
  void rejectsExternalOpenDocumentReference() throws Exception {
    Path archive = directory.resolve("external.odt");
    try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
      storedEntry(output, "mimetype", "application/vnd.oasis.opendocument.text");
      entry(
          output,
          "content.xml",
          "<office xmlns:xlink='http://www.w3.org/1999/xlink'><image"
              + " xlink:href='file:///etc/passwd'/></office>");
      entry(
          output,
          "META-INF/manifest.xml",
          "<manifest><file-entry full-path='/'"
              + " media-type='application/vnd.oasis.opendocument.text'/></manifest>");
    }

    IOException error =
        assertThrows(
            IOException.class, () -> SafeOfficeArchiveValidator.validateInput(archive, "odt"));

    assertEquals(DocumentConversionError.INPUT_INVALID.code(), error.getMessage());
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
}

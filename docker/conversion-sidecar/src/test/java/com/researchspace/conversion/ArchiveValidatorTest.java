package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private static void entry(ZipOutputStream output, String name, String value) throws IOException {
    output.putNextEntry(new ZipEntry(name));
    output.write(value.getBytes());
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

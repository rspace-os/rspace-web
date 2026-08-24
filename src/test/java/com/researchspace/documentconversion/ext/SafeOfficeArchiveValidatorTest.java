package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeOfficeArchiveValidatorTest {

  @TempDir Path directory;

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

  private static void entry(ZipOutputStream output, String name, String value) throws IOException {
    output.putNextEntry(new ZipEntry(name));
    output.write(value.getBytes());
    output.closeEntry();
  }
}

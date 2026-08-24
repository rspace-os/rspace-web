package com.researchspace.document.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataUriExtractorTest {

  private static final String ONE_PIXEL_PNG =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

  @TempDir Path directory;

  @Test
  void extractsRasterImageAndRemovesActiveContent() throws Exception {
    Path html = directory.resolve("input.html");
    Files.writeString(
        html,
        "<html><body onload='bad()'><script>bad()</script>"
            + "<img src='data:image/png;base64,"
            + ONE_PIXEL_PNG
            + "'><img src='https://example.test/tracker.png'></body></html>",
        StandardCharsets.UTF_8);

    new DataUriExtractor().extract(html, directory);

    String converted = Files.readString(html);
    assertFalse(converted.contains("script"));
    assertFalse(converted.contains("onload"));
    assertFalse(converted.contains("example.test"));
    assertFalse(converted.contains("data:image"));
    assertTrue(converted.contains("<img src="));
    try (var files = Files.list(directory)) {
      assertEquals(1, files.filter(path -> path.toString().endsWith(".png")).count());
    }
  }

  @Test
  void rejectsSvgDataUri() throws Exception {
    Path html = directory.resolve("input.html");
    Files.writeString(
        html,
        "<html><body><img src='data:image/svg+xml;base64,PHN2Zy8+'></body></html>",
        StandardCharsets.UTF_8);

    var error =
        assertThrows(
            java.io.IOException.class, () -> new DataUriExtractor().extract(html, directory));
    assertEquals("conversion.input-invalid", error.getMessage());
  }

  @Test
  void removesExternalLinksAndUnsafeCss() throws Exception {
    Path html = directory.resolve("input.html");
    Files.writeString(
        html,
        "<html><body><a href='https://example.test'>link</a>"
            + "<p style=\"color:red;background-image:url(https://example.test/a)\">text</p>"
            + "<iframe src='https://example.test'></iframe></body></html>",
        StandardCharsets.UTF_8);

    new DataUriExtractor().extract(html, directory);

    String converted = Files.readString(html);
    assertFalse(converted.contains("href="));
    assertFalse(converted.contains("iframe"));
    assertFalse(converted.contains("background-image"));
    assertTrue(converted.contains("color:red"));
  }
}

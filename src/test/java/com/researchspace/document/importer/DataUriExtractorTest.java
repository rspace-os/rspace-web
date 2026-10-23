package com.researchspace.document.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
  void keepsRelativeAsposeImageAndRemovesPathsOutsideOutputFolder() throws Exception {
    Path output = Files.createDirectory(directory.resolve("converted"));
    Path local = output.resolve("PowerPasteTesting_RSpace.001.png");
    byte[] png = Base64.getDecoder().decode(ONE_PIXEL_PNG);
    Files.write(local, png);
    Path outside = Files.write(directory.resolve("outside.png"), png);
    Files.createSymbolicLink(output.resolve("escape.png"), outside);
    Path html = output.resolve("input.html");
    Files.writeString(
        html,
        "<html><body><img src='PowerPasteTesting_RSpace.001.png'>"
            + "<img src='../outside.png'><img src='"
            + local
            + "'>"
            + "<img src='http://example.test/a.png'><img src='"
            + outside.toUri()
            + "'>"
            + "<img src='escape.png'></body></html>");

    new DataUriExtractor().extract(html, output);

    var images = Jsoup.parse(Files.readString(html)).select("img");
    assertEquals(1, images.size());
    assertEquals(local.getFileName().toString(), images.first().attr("src"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"My Doc (1).png", "Résumé + figure.png", "My%20Doc%20%281%29.png"})
  void resolvesSanitizedRelativeImageNames(String source) throws Exception {
    String fileName = source.startsWith("My") ? "My Doc (1).png" : "Résumé + figure.png";
    byte[] png = Base64.getDecoder().decode(ONE_PIXEL_PNG);
    Files.write(directory.resolve(fileName), png);
    Path html = directory.resolve("input.html");
    Files.writeString(html, "<html><body><img src='" + source + "'></body></html>");

    new DataUriExtractor().extract(html, directory);

    var images = Jsoup.parse(Files.readString(html)).select("img");
    assertEquals(1, images.size());
    Path image = directory.resolve(images.first().attr("src"));
    assertEquals(fileName, image.getFileName().toString());
    try (var input = Files.newInputStream(image)) {
      assertEquals(png.length, input.readAllBytes().length);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "%2e%2e/outside.png",
        "..%2Foutside.png",
        "%2e%2e%5coutside.png",
        "nested%2fimage.png",
        "nested%5Cimage.png",
        "nested/%2e%2e/image.png"
      })
  void removesEncodedTraversalAndSeparators(String source) throws Exception {
    Path output = Files.createDirectory(directory.resolve("converted"));
    byte[] png = Base64.getDecoder().decode(ONE_PIXEL_PNG);
    Files.write(directory.resolve("outside.png"), png);
    Files.write(output.resolve("image.png"), png);
    Path literalSource = output.resolve(source);
    Files.createDirectories(literalSource.getParent());
    Files.write(literalSource, png);
    Files.write(Files.createDirectories(output.resolve("nested")).resolve("image.png"), png);
    Path html = output.resolve("input.html");
    Files.writeString(html, "<html><body><img src='" + source + "'></body></html>");

    new DataUriExtractor().extract(html, output);

    assertTrue(Jsoup.parse(Files.readString(html)).select("img").isEmpty());
  }

  @Test
  void keepsImageNamedWithLiteralPercent() throws Exception {
    Files.write(directory.resolve("100%.png"), Base64.getDecoder().decode(ONE_PIXEL_PNG));
    Path html = directory.resolve("input.html");
    Files.writeString(html, "<html><body><img src='100%.png'></body></html>");

    new DataUriExtractor().extract(html, directory);

    var images = Jsoup.parse(Files.readString(html)).select("img");
    assertEquals(1, images.size());
    assertEquals("100%.png", images.first().attr("src"));
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

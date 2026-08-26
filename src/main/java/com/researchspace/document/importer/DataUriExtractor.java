package com.researchspace.document.importer;

import com.researchspace.documentconversion.ext.DocumentConversionError;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.tika.Tika;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Extracts embedded raster images and allowlist-sanitizes converted HTML. */
final class DataUriExtractor {

  private static final Logger LOG = LoggerFactory.getLogger(DataUriExtractor.class);

  private static final long MAX_HTML_BYTES = 50L * 1024 * 1024;
  private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
  private static final long MAX_TOTAL_IMAGE_BYTES = 50L * 1024 * 1024;
  private static final int MAX_IMAGES = 100;
  private static final long MAX_PIXELS = 50_000_000;
  private static final Pattern DATA_URI =
      Pattern.compile("^data:image/(?:png|jpeg|gif);base64,([A-Za-z0-9+/]+={0,2})$");
  private static final Set<String> IMAGE_MEDIA_TYPES =
      Set.of("image/png", "image/jpeg", "image/gif");
  private static final PolicyFactory HTML_POLICY = createPolicy();
  private static final Tika TIKA = new Tika();

  void extract(Path htmlFile, Path outputDirectory) throws IOException {
    if (Files.size(htmlFile) > MAX_HTML_BYTES) {
      fail(DocumentConversionError.INPUT_TOO_LARGE);
    }
    Document document = Jsoup.parse(htmlFile.toFile(), StandardCharsets.UTF_8.name());
    List<Path> extractedFiles = new ArrayList<>();
    try {
      extractImages(document, outputDirectory, extractedFiles);
      Document sanitized = Jsoup.parse(HTML_POLICY.sanitize(document.body().html()));
      Files.writeString(htmlFile, sanitized.outerHtml(), StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      LOG.warn("Converted Word HTML failed validation", e);
      for (Path extractedFile : extractedFiles) {
        try {
          Files.deleteIfExists(extractedFile);
        } catch (IOException cleanupFailure) {
          LOG.warn("Could not remove an extracted Word import image", cleanupFailure);
        }
      }
      throw e;
    }
  }

  private void extractImages(Document document, Path outputDirectory, List<Path> extractedFiles)
      throws IOException {
    long totalBytes = 0;
    int imageCount = 0;
    for (Element image : document.select("img")) {
      String source = image.attr("src");
      if (!source.startsWith("data:")) {
        image.remove();
        continue;
      }
      if (++imageCount > MAX_IMAGES) {
        fail(DocumentConversionError.INPUT_TOO_LARGE);
      }
      Matcher matcher = DATA_URI.matcher(source);
      if (!matcher.matches()) {
        fail(DocumentConversionError.INPUT_INVALID);
      }
      byte[] bytes;
      try {
        bytes = Base64.getDecoder().decode(matcher.group(1));
      } catch (IllegalArgumentException e) {
        LOG.warn("Converted Word HTML contained invalid Base64 image data", e);
        throw new IOException(DocumentConversionError.INPUT_INVALID.code(), e);
      }
      totalBytes += bytes.length;
      if (bytes.length > MAX_IMAGE_BYTES || totalBytes > MAX_TOTAL_IMAGE_BYTES) {
        fail(DocumentConversionError.INPUT_TOO_LARGE);
      }
      String format = validateImage(bytes);
      Path output = outputDirectory.resolve(UUID.randomUUID() + "." + format);
      Files.write(output, bytes);
      extractedFiles.add(output);
      if (Files.getFileStore(output).supportsFileAttributeView("posix")) {
        Files.setPosixFilePermissions(output, PosixFilePermissions.fromString("rw-------"));
      }
      image.clearAttributes();
      image.attr("src", output.getFileName().toString());
    }
  }

  private String validateImage(byte[] bytes) throws IOException {
    String mediaType = TIKA.detect(bytes);
    if (!IMAGE_MEDIA_TYPES.contains(mediaType)) {
      fail(DocumentConversionError.INPUT_INVALID);
    }
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        fail(DocumentConversionError.INPUT_INVALID);
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels <= 0 || pixels > MAX_PIXELS) {
          fail(DocumentConversionError.INPUT_TOO_LARGE);
        }
        BufferedImage image = reader.read(0);
        if (image == null) {
          fail(DocumentConversionError.INPUT_INVALID);
        }
        return switch (mediaType) {
          case "image/jpeg" -> "jpg";
          case "image/gif" -> "gif";
          default -> "png";
        };
      } finally {
        reader.dispose();
      }
    }
  }

  private static PolicyFactory createPolicy() {
    PolicyFactory convertedDocumentElements =
        new HtmlPolicyBuilder()
            .allowElements(
                "a",
                "abbr",
                "bdi",
                "bdo",
                "center",
                "dd",
                "dl",
                "dt",
                "figcaption",
                "figure",
                "hr",
                "kbd",
                "mark",
                "pre",
                "q",
                "samp",
                "var")
            .allowWithoutAttributes("a", "font", "span")
            .allowAttributes("class", "dir", "lang", "title")
            .globally()
            .allowAttributes("span", "width")
            .onElements("col", "colgroup")
            .allowAttributes("color", "face", "size")
            .onElements("font")
            .allowAttributes("start", "type")
            .onElements("ol")
            .allowAttributes("border", "cellpadding", "cellspacing", "width")
            .onElements("table")
            .allowAttributes("colspan", "headers", "rowspan")
            .onElements("td")
            .allowAttributes("colspan", "headers", "rowspan", "scope")
            .onElements("th")
            .allowAttributes("type")
            .onElements("ul")
            .toFactory();
    return Sanitizers.BLOCKS
        .and(Sanitizers.FORMATTING)
        .and(Sanitizers.TABLES)
        .and(Sanitizers.IMAGES)
        .and(Sanitizers.STYLES)
        .and(convertedDocumentElements);
  }

  private static void fail(DocumentConversionError error) throws IOException {
    throw new IOException(error.code());
  }
}

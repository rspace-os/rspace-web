package com.researchspace.conversion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

@Component
class ArchiveValidator {

  private static final int MAX_ENTRIES = 10_000;
  private static final long MAX_ENTRY_BYTES = 100L * 1024 * 1024;
  private static final long MAX_EXPANDED_BYTES = 500L * 1024 * 1024;
  private static final long MAX_XML_BYTES = 10L * 1024 * 1024;
  private static final int MAX_XML_DEPTH = 100;
  private static final long MAX_COMPRESSION_RATIO = 100;
  private static final Set<String> NESTED_ARCHIVE_SUFFIXES =
      Set.of(".7z", ".bz2", ".gz", ".jar", ".rar", ".tar", ".tgz", ".xz", ".zip");

  void validate(Path archive, String extension) {
    try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
      List<ZipArchiveEntry> entries = physicalEntries(zip);
      validateEntries(zip, entries);
      if ("docx".equals(extension)) {
        validateDocx(zip);
      } else {
        validateOpenDocument(zip, entries, extension);
      }
    } catch (ConversionException e) {
      throw e;
    } catch (Exception e) {
      throw new ConversionException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          ConversionError.INPUT_INVALID,
          "The uploaded file is not valid",
          e);
    }
  }

  private List<ZipArchiveEntry> physicalEntries(ZipFile zip) {
    List<ZipArchiveEntry> entries = new ArrayList<>();
    zip.getEntriesInPhysicalOrder().asIterator().forEachRemaining(entries::add);
    if (entries.isEmpty()) {
      invalid("The uploaded archive is empty");
    }
    return entries;
  }

  private void validateEntries(ZipFile zip, List<ZipArchiveEntry> entries) throws IOException {
    Set<String> names = new HashSet<>();
    long expandedBytes = 0;
    for (ZipArchiveEntry entry : entries) {
      if (names.size() >= MAX_ENTRIES) {
        tooLarge("The uploaded archive has too many entries");
      }
      String normalized = normalizeName(entry.getName());
      if (!names.add(normalized.toLowerCase(Locale.ROOT))
          || entry.isUnixSymlink()
          || entry.getGeneralPurposeBit().usesEncryption()
          || !zip.canReadEntryData(entry)) {
        invalid("The uploaded archive has an unsafe entry");
      }
      if (!entry.isDirectory() && hasNestedArchiveSuffix(normalized)) {
        invalid("The uploaded archive contains nested content");
      }
      long size = count(zip.getInputStream(entry), MAX_ENTRY_BYTES);
      expandedBytes += size;
      if (expandedBytes > MAX_EXPANDED_BYTES || excessiveCompression(entry, size)) {
        tooLarge("The uploaded archive expands beyond the allowed size");
      }
      String lowerName = normalized.toLowerCase(Locale.ROOT);
      if (lowerName.endsWith("vbaproject.bin")
          || lowerName.endsWith("/objectpool")
          || lowerName.contains("/embeddings/")) {
        invalid("The uploaded archive contains active content");
      }
    }
  }

  private String normalizeName(String name) {
    if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.indexOf('\\') >= 0) {
      invalid("The uploaded archive has an unsafe entry name");
    }
    Path normalized = Path.of(name).normalize();
    String result = normalized.toString().replace('\\', '/');
    if (normalized.isAbsolute()
        || result.equals("..")
        || result.startsWith("../")
        || name.startsWith("/")
        || name.matches("^[A-Za-z]:.*")) {
      invalid("The uploaded archive has an unsafe entry name");
    }
    return result;
  }

  private boolean excessiveCompression(ZipArchiveEntry entry, long expandedSize) {
    long compressedSize = entry.getCompressedSize();
    return expandedSize > 0
        && (compressedSize <= 0
            || expandedSize / Math.max(1, compressedSize) > MAX_COMPRESSION_RATIO);
  }

  private boolean hasNestedArchiveSuffix(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    return NESTED_ARCHIVE_SUFFIXES.stream().anyMatch(lower::endsWith);
  }

  private void validateDocx(ZipFile zip) throws Exception {
    require(zip, "[Content_Types].xml");
    require(zip, "word/document.xml");
    var entries = zip.getEntries();
    while (entries.hasMoreElements()) {
      ZipArchiveEntry entry = entries.nextElement();
      if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".rels")) {
        Element root = readXml(zip, entry).getDocumentElement();
        var relationships = root.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < relationships.getLength(); i++) {
          Element relationship = (Element) relationships.item(i);
          if ("external".equalsIgnoreCase(relationship.getAttribute("TargetMode"))) {
            invalid("The uploaded archive contains an external relationship");
          }
        }
      }
    }
  }

  private void validateOpenDocument(ZipFile zip, List<ZipArchiveEntry> entries, String extension)
      throws Exception {
    String expected =
        "ott".equals(extension)
            ? "application/vnd.oasis.opendocument.text-template"
            : "application/vnd.oasis.opendocument.text";
    ZipArchiveEntry mimetype = entries.get(0);
    if (!"mimetype".equals(mimetype.getName())
        || mimetype.getMethod() != ZipArchiveEntry.STORED
        || mimetype.getLocalFileDataExtra().length != 0
        || !expected.equals(readText(zip, mimetype, 256).strip())) {
      invalid("The uploaded OpenDocument media type is invalid");
    }
    require(zip, "content.xml");
    Element manifest = readXml(zip, require(zip, "META-INF/manifest.xml")).getDocumentElement();
    var fileEntries = manifest.getElementsByTagNameNS("*", "file-entry");
    boolean foundRoot = false;
    for (int i = 0; i < fileEntries.getLength(); i++) {
      Element entry = (Element) fileEntries.item(i);
      if ("/".equals(attributeByLocalName(entry, "full-path"))) {
        foundRoot = expected.equals(attributeByLocalName(entry, "media-type"));
        break;
      }
    }
    if (!foundRoot) {
      invalid("The uploaded OpenDocument manifest is invalid");
    }
  }

  private org.w3c.dom.Document readXml(ZipFile zip, ZipArchiveEntry entry) throws Exception {
    byte[] bytes = readBytes(zip, entry, MAX_XML_BYTES);
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    org.w3c.dom.Document document =
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    validateDepth(document.getDocumentElement(), 1);
    return document;
  }

  private void validateDepth(Node node, int depth) {
    if (depth > MAX_XML_DEPTH) {
      invalid("The uploaded archive contains overly deep XML");
    }
    for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        validateDepth(child, depth + 1);
      }
    }
  }

  private String attributeByLocalName(Element element, String localName) {
    for (int i = 0; i < element.getAttributes().getLength(); i++) {
      Node attribute = element.getAttributes().item(i);
      if (localName.equals(attribute.getLocalName()) || localName.equals(attribute.getNodeName())) {
        return attribute.getNodeValue();
      }
    }
    return "";
  }

  private ZipArchiveEntry require(ZipFile zip, String name) {
    ZipArchiveEntry entry = zip.getEntry(name);
    if (entry == null || entry.isDirectory()) {
      invalid("The uploaded document package is incomplete");
    }
    return entry;
  }

  private String readText(ZipFile zip, ZipArchiveEntry entry, long limit) throws IOException {
    return new String(readBytes(zip, entry, limit), StandardCharsets.UTF_8);
  }

  private byte[] readBytes(ZipFile zip, ZipArchiveEntry entry, long limit) throws IOException {
    try (InputStream input = zip.getInputStream(entry);
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      long total = 0;
      int read;
      while ((read = input.read(buffer)) != -1) {
        total += read;
        if (total > limit) {
          tooLarge("The uploaded archive entry is too large");
        }
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private long count(InputStream input, long limit) throws IOException {
    try (input) {
      byte[] buffer = new byte[8192];
      long total = 0;
      int read;
      while ((read = input.read(buffer)) != -1) {
        total += read;
        if (total > limit) {
          tooLarge("The uploaded archive entry is too large");
        }
      }
      return total;
    }
  }

  private void invalid(String message) {
    throw new ConversionException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, ConversionError.INPUT_INVALID, message);
  }

  private void tooLarge(String message) {
    throw new ConversionException(
        HttpStatus.PAYLOAD_TOO_LARGE, ConversionError.INPUT_TOO_LARGE, message);
  }
}

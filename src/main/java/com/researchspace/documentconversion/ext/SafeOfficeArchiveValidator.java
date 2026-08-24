package com.researchspace.documentconversion.ext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
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
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Defense-in-depth validation for Office packages sent to or returned by the sidecar. */
final class SafeOfficeArchiveValidator {

  private static final int MAX_ENTRIES = 10_000;
  private static final long MAX_ENTRY_BYTES = 100L * 1024 * 1024;
  private static final long MAX_EXPANDED_BYTES = 500L * 1024 * 1024;
  private static final long MAX_XML_BYTES = 10L * 1024 * 1024;
  private static final int MAX_XML_DEPTH = 100;
  private static final long MAX_COMPRESSION_RATIO = 100;
  private static final Set<String> NESTED_ARCHIVES =
      Set.of(".7z", ".bz2", ".gz", ".jar", ".rar", ".tar", ".tgz", ".xz", ".zip");
  private static final Set<String> ALLOWED_EXTERNAL_RELATIONSHIP_TYPES =
      Set.of(
          "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink",
          "http://purl.oclc.org/ooxml/officeDocument/relationships/hyperlink");

  private SafeOfficeArchiveValidator() {}

  static void validateInput(Path archive, String extension) throws IOException {
    validate(archive, extension);
  }

  static void validateDocx(Path archive) throws IOException {
    validate(archive, "docx");
  }

  private static void validate(Path archive, String extension) throws IOException {
    try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
      List<ZipArchiveEntry> entries = physicalEntries(zip);
      validateEntries(zip, entries);
      if ("docx".equals(extension)) {
        validateDocxStructure(zip);
      } else if ("odt".equals(extension) || "ott".equals(extension)) {
        validateOpenDocument(zip, entries, extension);
      }
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw invalid(e);
    }
  }

  private static List<ZipArchiveEntry> physicalEntries(ZipFile zip) throws IOException {
    List<ZipArchiveEntry> entries = new ArrayList<>();
    zip.getEntriesInPhysicalOrder().asIterator().forEachRemaining(entries::add);
    if (entries.isEmpty()) {
      throw invalid();
    }
    return entries;
  }

  private static void validateEntries(ZipFile zip, List<ZipArchiveEntry> entries)
      throws IOException {
    Set<String> names = new HashSet<>();
    long expandedTotal = 0;
    for (ZipArchiveEntry entry : entries) {
      if (names.size() >= MAX_ENTRIES) {
        throw tooLarge();
      }
      String normalized = normalize(entry.getName());
      if (!names.add(normalized.toLowerCase(Locale.ROOT))
          || entry.isUnixSymlink()
          || entry.getGeneralPurposeBit().usesEncryption()
          || !zip.canReadEntryData(entry)) {
        throw invalid();
      }
      String lower = normalized.toLowerCase(Locale.ROOT);
      if ((!entry.isDirectory() && NESTED_ARCHIVES.stream().anyMatch(lower::endsWith))
          || lower.endsWith("vbaproject.bin")
          || lower.endsWith("/objectpool")
          || lower.contains("/embeddings/")) {
        throw invalid();
      }
      long expanded = count(zip.getInputStream(entry), MAX_ENTRY_BYTES);
      expandedTotal += expanded;
      long compressed = entry.getCompressedSize();
      if (expandedTotal > MAX_EXPANDED_BYTES
          || (expanded > 0
              && (compressed <= 0 || expanded / Math.max(1, compressed) > MAX_COMPRESSION_RATIO))) {
        throw tooLarge();
      }
    }
  }

  private static String normalize(String name) throws IOException {
    if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.indexOf('\\') >= 0) {
      throw invalid();
    }
    Path normalized = Path.of(name).normalize();
    String value = normalized.toString().replace('\\', '/');
    if (normalized.isAbsolute()
        || value.equals("..")
        || value.startsWith("../")
        || name.startsWith("/")
        || name.matches("^[A-Za-z]:.*")) {
      throw invalid();
    }
    return value;
  }

  private static void validateDocxStructure(ZipFile zip) throws Exception {
    require(zip, "[Content_Types].xml");
    require(zip, "word/document.xml");
    var entries = zip.getEntries();
    while (entries.hasMoreElements()) {
      ZipArchiveEntry entry = entries.nextElement();
      if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(".rels")) {
        continue;
      }
      var relationships = readXml(zip, entry).getElementsByTagNameNS("*", "Relationship");
      for (int i = 0; i < relationships.getLength(); i++) {
        Element relationship = (Element) relationships.item(i);
        if ("external".equalsIgnoreCase(relationship.getAttribute("TargetMode"))
            && !ALLOWED_EXTERNAL_RELATIONSHIP_TYPES.contains(relationship.getAttribute("Type"))) {
          throw invalid();
        }
      }
    }
  }

  private static void validateOpenDocument(
      ZipFile zip, List<ZipArchiveEntry> entries, String extension) throws Exception {
    String expected =
        "ott".equals(extension)
            ? "application/vnd.oasis.opendocument.text-template"
            : "application/vnd.oasis.opendocument.text";
    ZipArchiveEntry mimetype = entries.get(0);
    if (!"mimetype".equals(mimetype.getName())
        || mimetype.getMethod() != ZipArchiveEntry.STORED
        || mimetype.getLocalFileDataExtra().length != 0
        || !expected.equals(new String(readBytes(zip, mimetype, 256)).strip())) {
      throw invalid();
    }
    require(zip, "content.xml");
    Element manifest = readXml(zip, require(zip, "META-INF/manifest.xml"));
    var fileEntries = manifest.getElementsByTagNameNS("*", "file-entry");
    for (int i = 0; i < fileEntries.getLength(); i++) {
      Element entry = (Element) fileEntries.item(i);
      if ("/".equals(attribute(entry, "full-path"))
          && expected.equals(attribute(entry, "media-type"))) {
        validateOpenDocumentReferences(zip);
        return;
      }
    }
    throw invalid();
  }

  private static void validateOpenDocumentReferences(ZipFile zip) throws Exception {
    for (String name : List.of("content.xml", "styles.xml")) {
      ZipArchiveEntry entry = zip.getEntry(name);
      if (entry != null && !entry.isDirectory()) {
        validateNoExternalReferences(readXml(zip, entry));
      }
    }
  }

  private static void validateNoExternalReferences(Element root) throws IOException {
    for (Node node = root; node != null; node = nextNode(root, node)) {
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      var attributes = node.getAttributes();
      for (int i = 0; i < attributes.getLength(); i++) {
        Node attribute = attributes.item(i);
        if (("href".equals(attribute.getLocalName()) || "href".equals(attribute.getNodeName()))
            && isExternalReference(attribute.getNodeValue())) {
          throw invalid();
        }
      }
    }
  }

  private static Node nextNode(Node root, Node current) {
    if (current.getFirstChild() != null) {
      return current.getFirstChild();
    }
    while (current != root && current.getNextSibling() == null) {
      current = current.getParentNode();
    }
    return current == root ? null : current.getNextSibling();
  }

  private static boolean isExternalReference(String value) {
    if (value == null || value.isBlank() || value.startsWith("#")) {
      return false;
    }
    try {
      URI uri = URI.create(value);
      String path = uri.getPath();
      if (uri.isAbsolute()
          || uri.getAuthority() != null
          || value.startsWith("/")
          || path == null
          || path.indexOf('\\') >= 0) {
        return true;
      }
      Path normalized = Path.of(path).normalize();
      String normalizedPath = normalized.toString();
      return normalized.isAbsolute()
          || normalizedPath.equals("..")
          || normalizedPath.startsWith("../");
    } catch (IllegalArgumentException e) {
      return true;
    }
  }

  private static Element readXml(ZipFile zip, ZipArchiveEntry entry) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    Element root =
        factory
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(readBytes(zip, entry, MAX_XML_BYTES)))
            .getDocumentElement();
    validateDepth(root, 1);
    return root;
  }

  private static void validateDepth(Node node, int depth) throws IOException {
    if (depth > MAX_XML_DEPTH) {
      throw tooLarge();
    }
    for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        validateDepth(child, depth + 1);
      }
    }
  }

  private static String attribute(Element element, String localName) {
    for (int i = 0; i < element.getAttributes().getLength(); i++) {
      Node attribute = element.getAttributes().item(i);
      if (localName.equals(attribute.getLocalName()) || localName.equals(attribute.getNodeName())) {
        return attribute.getNodeValue();
      }
    }
    return "";
  }

  private static ZipArchiveEntry require(ZipFile zip, String name) throws IOException {
    ZipArchiveEntry entry = zip.getEntry(name);
    if (entry == null || entry.isDirectory()) {
      throw invalid();
    }
    return entry;
  }

  private static byte[] readBytes(ZipFile zip, ZipArchiveEntry entry, long limit)
      throws IOException {
    try (InputStream input = zip.getInputStream(entry);
        var bounded = bounded(input, limit)) {
      byte[] bytes = IOUtils.toByteArray(bounded);
      if (bounded.getCount() > limit) {
        throw tooLarge();
      }
      return bytes;
    }
  }

  private static long count(InputStream input, long limit) throws IOException {
    try (input;
        var bounded = bounded(input, limit)) {
      bounded.transferTo(OutputStream.nullOutputStream());
      if (bounded.getCount() > limit) {
        throw tooLarge();
      }
      return bounded.getCount();
    }
  }

  private static BoundedInputStream bounded(InputStream input, long limit) throws IOException {
    return BoundedInputStream.builder()
        .setInputStream(input)
        .setMaxCount(limit + 1)
        .setPropagateClose(false)
        .get();
  }

  private static IOException invalid() {
    return new IOException(DocumentConversionError.INPUT_INVALID.code());
  }

  private static IOException invalid(Exception cause) {
    return new IOException(DocumentConversionError.INPUT_INVALID.code(), cause);
  }

  private static IOException tooLarge() {
    return new IOException(DocumentConversionError.INPUT_TOO_LARGE.code());
  }
}

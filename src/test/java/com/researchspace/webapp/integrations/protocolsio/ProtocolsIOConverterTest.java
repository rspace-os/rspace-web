package com.researchspace.webapp.integrations.protocolsio;

import static com.researchspace.core.util.JacksonUtil.fromJson;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.protocolsio.Protocol;
import com.researchspace.service.DefaultRecordContext;
import com.researchspace.service.FolderManager;
import com.researchspace.service.FormManager;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserLocaleService;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestFactory;
import com.researchspace.testutils.VelocityTestUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.velocity.app.VelocityEngine;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class ProtocolsIOConverterTest {

  private static final String SIMPLE_PIO_JSON = "p_io_8163.json";
  private static final String UTF_8 = "UTF-8";
  private static final String ALLOWED_IMAGE =
      "https://s3.amazonaws.com/protocols-files/public/71edb71d/bmzeh4hw.jpg";

  static class ProtocolsIOToDocumentConverterImplTSS extends ProtocolsIOToDocumentConverterImpl {
    int downloadCount;
    boolean folderCreateInvoked = false;
    boolean updateFieldContentInvoked = false;
    StructuredDocument document = null;

    boolean failDownload = false;
    boolean failUpdate = false;
    List<File> tempFiles = new ArrayList<>();
    Document importedDoc;
    List<String> importedImageRefs = new ArrayList<>();

    void downloadFile(URL url, File tempFile) throws IOException {
      downloadCount++;
      tempFiles.add(tempFile);
      if (failDownload) {
        throw new IOException("download failed");
      }
    }

    protected StructuredDocument updateFieldContent(
        User creator,
        Document jsoupDoc,
        Elements aResourceLinks,
        Folder imageFolder,
        StructuredDocument strucDoc,
        Function<Element, String> fileLocator) {

      updateFieldContentInvoked = true;
      importedDoc = jsoupDoc;
      aResourceLinks.forEach(el -> importedImageRefs.add(fileLocator.apply(el)));
      if (failUpdate) {
        throw new IllegalStateException("update failed");
      }
      return document;
    }
  }

  VelocityEngine vel;
  @Mock FormManager formMgr;
  @Mock FolderManager folderMgr;
  @Mock RecordManager recordMgr;
  @Mock ApplicationContext context;
  @Mock ApplicationEventPublisher publisher;
  @TempDir public File tempFolder;
  @InjectMocks ProtocolsIOToDocumentConverterImplTSS impl;
  User any = TestFactory.createAnyUser("any");
  RecordFactory rf = new RecordFactory();
  Folder importFolder;
  StructuredDocument doc;

  @BeforeEach
  public void setUp() throws Exception {
    vel = VelocityTestUtils.setupVelocity("src/main/resources/velocityTemplates/integrations");
    impl.setVelocity(vel);
    impl.setRecordFactory(new RecordFactory());
    ReflectionTestUtils.setField(impl, "messages", new MessageSourceUtils(new JsonMessageSource()));
    ReflectionTestUtils.setField(impl, "userLocaleService", new UserLocaleService());
    importFolder = TestFactory.createAnImportsFolder(any);
    importFolder.setId(-2L);
    doc = TestFactory.createAnySD();
    doc.setOwner(any);
  }

  @Test
  public void testGeneratePRotocol() throws IOException {
    File testFile = RSpaceTestUtils.getResource(SIMPLE_PIO_JSON);
    Protocol protocol = fromJson(readFileToString(testFile, UTF_8), Protocol.class);
    RSForm aform = rf.createNewForm();

    setupmocks(protocol, aform);
    impl.document = doc;
    assertEquals(doc, impl.generateFromProtocol(protocol, any, null));
    verify(publisher).publishEvent(Mockito.any(GenericEvent.class));
  }

  @Test
  public void testIngestImages() throws Exception {
    File testFile = RSpaceTestUtils.getResource("pio-with-images.html");
    String htmlString = FileUtils.readFileToString(testFile, UTF_8);
    Map<String, File> downloadedMap = new HashMap<>();
    impl.downloadImagesToTempFiles(impl.getImages(htmlString), any, downloadedMap);
    assertThat(downloadedMap).hasSize(5);
    assertEquals(5, impl.downloadCount);
    downloadedMap.values().forEach(FileUtils::deleteQuietly);
  }

  @Test
  public void testFileWithImages() throws Exception {
    File testFile = RSpaceTestUtils.getResource("pio-with-images.json");
    Protocol protocol = readJson(testFile);
    RSForm aform = rf.createNewForm();

    setupmocks(protocol, aform);
    impl.document = doc;
    assertEquals(doc, impl.generateFromProtocol(protocol, any, null));
    assertTrue(impl.updateFieldContentInvoked);
  }

  @Test
  public void testFileNameGeneration() throws Exception {
    // this just creates empty file with generated name
    File tempFile =
        impl.getTempFileFromURL(
            new URL(
                "https://s3.amazonaws.com/protocols-files/public/71edb71d34a3f900912c0cbb6075b211cc83bb9810591c5fca73217f74d99c52/bmzeh4hw.jpg"));
    assertThat(tempFile.getName()).endsWith(".jpg");
    FileUtils.deleteQuietly(tempFile);
  }

  @Test
  public void fileNameIgnoresQueryString() throws Exception {
    File tempFile =
        impl.getTempFileFromURL(
            new URL(
                "https://protocols-files.s3.amazonaws.com/files/bmzeh4hw.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=604800&X-Amz-Signature=abc123"));
    assertThat(tempFile.getName()).startsWith("bmzeh4hw").endsWith(".png");
    FileUtils.deleteQuietly(tempFile);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "file:///etc/passwd",
        "/etc/passwd",
        "jar:file:///opt/rspace.war!/WEB-INF/web.xml",
        "http://s3.amazonaws.com/protocols-files/public/a.png",
        "https://localhost/protocols-files/public/a.png",
        "https://169.254.169.254/latest/meta-data/",
        "https://10.0.0.1/protocols-files/public/a.png",
        "https://s3.amazonaws.com/another-bucket/a.png",
        "https://s3.amazonaws.com.attacker.example/protocols-files/public/a.png",
        "https://user@s3.amazonaws.com/protocols-files/public/a.png",
        "https://s3.amazonaws.com:8443/protocols-files/public/a.png",
        "https://s3.amazonaws.com/protocols-files/../another-bucket/a.png",
        "https://s3.amazonaws.com/protocols-files/%2e%2e/another-bucket/image.png",
        "http://content.protocols.io/files/a.png",
        "https://content.protocols.io.attacker.example/files/a.png",
        "https://another-bucket.s3.amazonaws.com/files/a.png",
        "https://files.protocols.io/external/a.png",
        "not a url"
      })
  public void imagesOutsideProtocolsIOFileHostsAreNotImported(String src) {
    impl.processHtml(imageHtml(src), doc, any);

    assertEquals(0, impl.downloadCount);
    assertThat(impl.importedImageRefs).isEmpty();
    assertThat(impl.importedDoc.select("img")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://s3.amazonaws.com/protocols-files/public/71edb71d/bmzeh4hw.jpg",
        "https://s3.amazonaws.com/pr-journal/bvbcn2iw.png",
        "https://content.protocols.io/files/bmzeh4hw.jpg",
        "https://content.protocols.io/public/71edb71d/bmzeh4hw.png",
        "https://content.protocols.io/bvbcn2iw.png",
        "https://protocols-files.s3.amazonaws.com/files/bmzeh4hw.png?X-Amz-Expires=604800&X-Amz-Signature=abc123",
        "https://pr-journal.s3.amazonaws.com/bvbcn2iw.png"
      })
  public void imagesOnProtocolsIOFileHostsAreImported(String src) {
    impl.processHtml(imageHtml(src), doc, any);

    assertEquals(1, impl.downloadCount);
    assertThat(impl.importedImageRefs).containsExactly(impl.tempFiles.get(0).getAbsolutePath());
  }

  @Test
  public void tempFilesAreDeletedAfterImport() {
    impl.processHtml(imageHtml(ALLOWED_IMAGE), doc, any);

    assertThat(impl.tempFiles).isNotEmpty().noneMatch(File::exists);
  }

  @Test
  public void tempFilesAreDeletedWhenImportFails() {
    impl.failUpdate = true;

    assertThrows(
        IllegalStateException.class, () -> impl.processHtml(imageHtml(ALLOWED_IMAGE), doc, any));
    assertThat(impl.tempFiles).isNotEmpty().noneMatch(File::exists);
  }

  @Test
  public void tempFilesAreDeletedWhenALaterImageThrows() {
    // a file name under 3 characters makes File.createTempFile throw IllegalArgumentException
    String html = imageHtml(ALLOWED_IMAGE) + imageHtml("https://content.protocols.io/a.png");

    assertThrows(IllegalArgumentException.class, () -> impl.processHtml(html, doc, any));
    assertThat(impl.tempFiles).hasSize(1).noneMatch(File::exists);
  }

  @Test
  public void failedDownloadIsNotImportedAndLeavesNoTempFile() {
    impl.failDownload = true;

    impl.processHtml(imageHtml(ALLOWED_IMAGE), doc, any);

    assertThat(impl.importedImageRefs).isEmpty();
    assertThat(impl.tempFiles).isNotEmpty().noneMatch(File::exists);
  }

  @Test
  public void downloadSavesResponseBody() throws Exception {
    HttpServer server = startServer();
    server.createContext("/image.png", exchange -> respond(exchange, 200, "png-bytes"));
    try {
      File target = new File(tempFolder, "image.png");

      new ProtocolsIOToDocumentConverterImpl().downloadFile(urlOf(server, "/image.png"), target);

      assertEquals("png-bytes", readFileToString(target, UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void downloadDoesNotFollowRedirects() throws Exception {
    HttpServer server = startServer();
    AtomicBoolean redirectTargetRequested = new AtomicBoolean();
    server.createContext(
        "/image.png",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "/internal");
          respond(exchange, 302, "");
        });
    server.createContext(
        "/internal",
        exchange -> {
          redirectTargetRequested.set(true);
          respond(exchange, 200, "secret");
        });
    try {
      File target = new File(tempFolder, "image.png");
      ProtocolsIOToDocumentConverterImpl converter = new ProtocolsIOToDocumentConverterImpl();
      URL url = urlOf(server, "/image.png");

      assertThrows(IOException.class, () -> converter.downloadFile(url, target));
      assertFalse(redirectTargetRequested.get());
    } finally {
      server.stop(0);
    }
  }

  private static String imageHtml(String src) {
    return "<p>text</p><p><img src=\"" + src + "\"></p>";
  }

  private static HttpServer startServer() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.start();
    return server;
  }

  private static URL urlOf(HttpServer server, String path) throws IOException {
    return new URL("http://localhost:" + server.getAddress().getPort() + path);
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      exchange.getResponseBody().write(bytes);
    }
    exchange.close();
  }

  private void setupmocks(Protocol protocol, RSForm aform) {
    when(formMgr.getCurrentSystemForm("ProtocolsIO")).thenReturn(Optional.ofNullable(aform));
    when(folderMgr.getImportsFolder(any)).thenReturn(importFolder);
    when(recordMgr.createNewStructuredDocument(
            Mockito.eq(importFolder.getId()),
            Mockito.eq(aform.getId()),
            Mockito.eq(protocol.getTitle()),
            Mockito.eq(any),
            Mockito.any(DefaultRecordContext.class)))
        .thenReturn(doc);
  }

  @Test
  public void testGenerateHtml() throws IOException {
    File testFile = RSpaceTestUtils.getResource(SIMPLE_PIO_JSON);
    Protocol protocol = fromJson(readFileToString(testFile, UTF_8), Protocol.class);
    log.info(
        protocol.getSteps().stream()
            .flatMap(s -> s.getComponents().stream())
            .map(s -> s.getId() + "type:" + s.getTypeId())
            .collect(Collectors.toList())
            .toString());
    String html = impl.generateHtml(protocol);
    log.info(html);
    assertThat(html).doesNotContain("$");
    FileUtils.write(new File(tempFolder, "pio.html"), html, UTF_8);
    log.info("File written to {}", tempFolder + "/" + "pio.html");
  }

  @Test
  public void testGenerateHtml2() throws IOException {
    File testFile = RSpaceTestUtils.getResource("rna.json");
    Protocol protocol = readJson(testFile);

    log.info(
        protocol.getSteps().stream()
            .flatMap(s -> s.getComponents().stream())
            .map(s -> s.getId() + "type:" + s.getTypeId())
            .collect(Collectors.toList())
            .toString());
    String html = impl.generateHtml(protocol);
    log.info(html);
    assertThat(html).doesNotContain("$");
    FileUtils.write(new File(tempFolder, "pio2.html"), html, UTF_8);
    log.info("File written to {}", tempFolder + "/" + "pio2.html");
  }

  @Test
  public void testHtmlGenerationForShakerStepComponent() throws IOException {
    File testFile = RSpaceTestUtils.getResource("/protocolsIO/shakerStepComponentProtocol.json");
    Protocol protocol = readJson(testFile);

    log.info(
        protocol.getSteps().stream()
            .flatMap(s -> s.getComponents().stream())
            .map(s -> s.getId() + "type:" + s.getTypeId())
            .collect(Collectors.toList())
            .toString());
    String html = impl.generateHtml(protocol);
    log.info(html);
    assertThat(html).doesNotContain("$");
    FileUtils.write(new File(tempFolder, "shakerStepComponentProtocol.html"), html, UTF_8);
    log.info("File written to {}", tempFolder + "/" + "shakerStepComponentProtocol.html");
  }

  private Protocol readJson(File testFile)
      throws IOException, JsonParseException, JsonMappingException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    Protocol protocol =
        mapper.readValue(FileUtils.readFileToString(testFile, UTF_8), Protocol.class);
    return protocol;
  }
}

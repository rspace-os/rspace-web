package com.researchspace.webapp.integrations.protocolsio;

import static com.researchspace.core.util.JacksonUtil.fromJson;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
import com.researchspace.model.record.TestFactory;
import com.researchspace.protocolsio.Protocol;
import com.researchspace.service.DefaultRecordContext;
import com.researchspace.service.FolderManager;
import com.researchspace.service.FormManager;
import com.researchspace.service.RecordManager;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.VelocityTestUtils;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.velocity.app.VelocityEngine;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

@Slf4j
public class ProtocolsIOConverterTest {

  private static final String SIMPLE_PIO_JSON = "p_io_8163.json";
  private static final String UTF_8 = "UTF-8";

  static class ProtocolsIOToDocumentConverterImplTSS extends ProtocolsIOToDocumentConverterImpl {
    int downloadCount;
    boolean folderCreateInvoked = false;
    boolean updateFieldContentInvoked = false;
    StructuredDocument document = null;

    void downloadFile(URL url, File tempFile) throws IOException {
      downloadCount++;
    }

    Folder doCreateTargetFolder(
        StructuredDocument rspaceDocument, User subject, Map<String, File> imagesMap) {
      folderCreateInvoked = true;
      return TestFactory.createAFolder("any", TestFactory.createAnyUser("any"));
    }

    protected StructuredDocument updateFieldContent(
        User creator,
        Document jsoupDoc,
        Elements aResourceLinks,
        Folder imageFolder,
        StructuredDocument strucDoc,
        Function<Element, String> fileLocator) {

      updateFieldContentInvoked = true;
      return document;
    }
  }

  VelocityEngine vel;
  @Mock FormManager formMgr;
  @Mock FolderManager folderMgr;
  @Mock RecordManager recordMgr;
  @Mock ApplicationContext context;
  @Mock ApplicationEventPublisher publisher;

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  public @Rule TemporaryFolder tempFolder = new TemporaryFolder();
  @InjectMocks ProtocolsIOToDocumentConverterImplTSS impl;
  User any = TestFactory.createAnyUser("any");
  RecordFactory rf = new RecordFactory();
  Folder importFolder;
  StructuredDocument doc;

  @Before
  public void setUp() throws Exception {
    vel = VelocityTestUtils.setupVelocity("src/main/resources/velocityTemplates/integrations");
    impl.setVelocity(vel);
    impl.setRecordFactory(new RecordFactory());
    importFolder = TestFactory.createAnImportsFolder(any);
    importFolder.setId(-2L);
    doc = TestFactory.createAnySD();
    doc.setOwner(any);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGeneratePRotocol() throws IOException {
    File testFile = RSpaceTestUtils.getResource(SIMPLE_PIO_JSON);
    Protocol protocol = fromJson(readFileToString(testFile, UTF_8), Protocol.class);
    RSForm aform = rf.createNewForm();

    setupmocks(protocol, aform);
    impl.document = doc;
    assertEquals(doc, impl.generateFromProtocol(protocol, any));
    verify(publisher).publishEvent(Mockito.any(GenericEvent.class));
  }

  @Test
  public void testIngestImages() throws Exception {
    File testFile = RSpaceTestUtils.getResource("pio-with-images.html");
    String htmlString = FileUtils.readFileToString(testFile, UTF_8);
    Map<String, File> downloadedMap = impl.downloadImagesToTempFiles(impl.getImages(htmlString));
    assertEquals(5, downloadedMap.size());
    assertEquals(5, impl.downloadCount);
  }

  @Test
  public void testFileWithImages() throws Exception {
    File testFile = RSpaceTestUtils.getResource("pio-with-images.json");
    Protocol protocol = readJson(testFile);
    RSForm aform = rf.createNewForm();

    setupmocks(protocol, aform);
    impl.document = doc;
    assertEquals(doc, impl.generateFromProtocol(protocol, any));
    assertTrue(impl.updateFieldContentInvoked);
  }

  @Test
  public void testFileNameGeneration() throws Exception {
    // this just creates empty file with generated name
    File tempFile =
        impl.getTempFileFromURL(
            new URL(
                "https://s3.amazonaws.com/protocols-files/public/71edb71d34a3f900912c0cbb6075b211cc83bb9810591c5fca73217f74d99c52/bmzeh4hw.jpg"));
    assertTrue(tempFile.getName().endsWith(".jpg"));
  }

  private void setupmocks(Protocol protocol, RSForm aform) {
    when(formMgr.getCurrentSystemForm("ProtocolsIO")).thenReturn(Optional.ofNullable(aform));
    when(folderMgr.getImportsFolder(any)).thenReturn(importFolder);
    when(recordMgr.createNewStructuredDocument(
            Mockito.eq(importFolder.getId()),
            Mockito.eq(aform.getId()),
            Mockito.eq(protocol.getTitle()),
            Mockito.eq(any),
            Mockito.any(DefaultRecordContext.class),
            Mockito.eq(false)))
        .thenReturn(doc);
    when(recordMgr.save(doc, any)).thenReturn(doc);
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
    assertFalse(html.contains("$"));
    FileUtils.write(new File(tempFolder.getRoot(), "pio.html"), html, UTF_8);
    log.info("File written to {}", tempFolder.getRoot() + "/" + "pio.html");
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
    assertFalse(html.contains("$"));
    FileUtils.write(new File(tempFolder.getRoot(), "pio2.html"), html, UTF_8);
    log.info("File written to {}", tempFolder.getRoot() + "/" + "pio2.html");
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
    assertFalse(html.contains("$"));
    FileUtils.write(
        new File(tempFolder.getRoot(), "shakerStepComponentProtocol.html"), html, UTF_8);
    log.info("File written to {}", tempFolder.getRoot() + "/" + "shakerStepComponentProtocol.html");
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

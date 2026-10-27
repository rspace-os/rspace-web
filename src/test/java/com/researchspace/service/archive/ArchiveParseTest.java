package com.researchspace.service.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.AllArchiveExternalWorkFlowMetaData;
import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalFileNotExistException;
import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveExternalWorkFlow;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ArchiveModel;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.core.util.ZipExtractionLimitException;
import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.archive.ImportArchiveReport.ValidationResult;
import com.researchspace.service.impl.DiskSpaceCheckerImpl;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

public class ArchiveParseTest {

  File V018 = RSpaceTestUtils.getResource("archives/v0-18/0.18Export");
  File V217 = RSpaceTestUtils.getResource("archives/v2-17.Export");
  ArchiveParserTSS parser;
  File anyFolder = new File("src/test/resources/TestResources");

  class ArchiveParserTSS extends ArchiveParserImpl {
    Long csum;
    Long calculated;
    String zipContentsChecksum;
    String zipContentsChecksumCalculated;

    ArchiveModelTSS model;
    boolean isZip;
    ZipExtractionLimitException extractionFailure;
    SemanticVersion appversion;

    protected Optional<ArchivalCheckSum> getCheckSum(String uid) {
      if (csum != null) {
        ArchivalCheckSum sum = new ArchivalCheckSum();
        sum.setCheckSum(csum);
        sum.setZipContentCheckSum(zipContentsChecksum);
        return Optional.of(sum);
      } else {
        return Optional.empty();
      }
    }

    protected long calculateChecksum(File zipFile) throws IOException {
      return calculated;
    }

    protected String calculateZipContentsChecksum(File zipFolder) throws IOException {
      return zipContentsChecksumCalculated;
    }

    public IArchiveModel parse(File rootFolder, ImportArchiveReport report) {
      return model;
    }

    protected boolean isZipFile(File zipFile) {
      return isZip;
    }

    protected File extractZipFolder(File zipFile, File outFolder) throws IOException {
      if (extractionFailure != null) {
        throw extractionFailure;
      }
      return anyFolder;
    }

    protected SemanticVersion getRSpaceVersion() {
      return appversion;
    }
  }

  class ArchiveModelTSS extends ArchiveModel {
    ArchiveManifest manifest;

    public ArchiveManifest getManifest() throws IOException {
      return manifest;
    }
  }

  @BeforeEach
  public void setUp() throws Exception {
    parser = new ArchiveParserTSS();
  }

  /*
   * A single exported experiment record from 0.18,
   */
  final String EXPORTED_RECORD = "Experiment 1";

  @Test
  public void testArchiveValidationOfZip() {
    parser.isZip = false;
    ImportArchiveReport report = new ImportArchiveReport();
    loadArchive(report, new File("any"));
    assertFalse(report.isValidationSuccessful());
    assertRuleValidationResult(
        report, ImportValidationRule.ZIP_FILE_CAN_UNZIP, ValidationTestResult.FAIL);
  }

  @ParameterizedTest
  @CsvSource({
    "EXPANDED_SIZE, 10485760000, extractionExpandedSizeExceeded, 10000",
    "FREE_DISK_SPACE, 2097152000, extractionInsufficientDiskSpace, 2000",
    "ENTRY_COUNT, 500000, extractionEntryCountExceeded, 500000",
    "COMPRESSION_RATIO, 100, extractionCompressionRatioExceeded, 100"
  })
  public void extractionLimitFailureReportsWhichLimitTripped(
      ZipExtractionLimitException.Limit limit,
      long limitValue,
      String messageKey,
      long expectedArgument) {
    parser.isZip = true;
    parser.extractionFailure = new ZipExtractionLimitException(limit, limitValue, "test");
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage("archiveImport.errors." + messageKey, Locale.getDefault(), "limit {0}");
    MessageSourceUtils messages = new MessageSourceUtils();
    messages.setMessageSource(source);
    ReflectionTestUtils.setField(parser, "messages", messages);
    ImportArchiveReport report = new ImportArchiveReport();

    loadArchive(report, new File("any"));

    assertFalse(report.isValidationSuccessful());
    assertRuleValidationResult(
        report, ImportValidationRule.ZIP_FILE_CAN_UNZIP, ValidationTestResult.FAIL);
    assertEquals(
        List.of(MessageFormat.format("limit {0}", expectedArgument)),
        report.getErrorList().getErrorMessages());
  }

  @ParameterizedTest
  @CsvSource({
    "1, 0, 0, 0, EXPANDED_SIZE, 1048576",
    "0, 1, 0, 0, ENTRY_COUNT, 1",
    "0, 0, 2147483647, 0, FREE_DISK_SPACE, 2251799812636672",
    "0, 0, 0, 50, COMPRESSION_RATIO, 50"
  })
  public void extractionUsesDeploymentLimits(
      int maxExpandedSizeMB,
      int maxEntries,
      int minFreeSpaceMB,
      int maxCompressionRatio,
      ZipExtractionLimitException.Limit expectedLimit,
      long expectedLimitValue,
      @TempDir File tempDir)
      throws IOException {
    File zip = new File(tempDir, "big.zip");
    try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zip)) {
      // past the 10 MiB floor, so the ratio is judged
      out.putArchiveEntry(new ZipArchiveEntry("big.bin"));
      out.write(new byte[12 * 1024 * 1024]);
      out.closeArchiveEntry();
      out.putArchiveEntry(new ZipArchiveEntry("small.txt"));
      out.closeArchiveEntry();
    }
    DiskSpaceCheckerImpl diskSpaceChecker = new DiskSpaceCheckerImpl();
    diskSpaceChecker.setMaxArchiveSizeMB(Integer.toString(maxExpandedSizeMB));
    diskSpaceChecker.setSpaceRequiredToStartArchiveExportMB(Integer.toString(minFreeSpaceMB));
    ArchiveParserImpl realParser = new ArchiveParserImpl();
    ReflectionTestUtils.setField(realParser, "diskSpaceChecker", diskSpaceChecker);
    ReflectionTestUtils.setField(realParser, "importMaxEntries", maxEntries);
    ReflectionTestUtils.setField(realParser, "importMaxCompressionRatio", maxCompressionRatio);

    ZipExtractionLimitException e =
        assertThrows(
            ZipExtractionLimitException.class,
            () -> realParser.extractZipFolder(zip, new File(tempDir, "out")));

    assertEquals(expectedLimit, e.getLimit());
    assertEquals(expectedLimitValue, e.getLimitValue());
    assertEquals(0, new File(tempDir, "out").list().length);
  }

  @Test
  public void testIncompatibleForwardMigrationRejected() {
    parser.isZip = true;
    parser.calculated = 2L;
    parser.csum = 1L;
    parser.model = new ArchiveModelTSS();
    parser.appversion = new SemanticVersion("1.32");

    createRSpaceARchiveMAnifest();
    // far future archive version
    parser.model.manifest.addItem(ArchiveManifest.RSPACE_APPLICATION_VERSION, "20.36");

    ImportArchiveReport report = new ImportArchiveReport();
    IArchiveModel model = loadArchive(report, new File("any"));
    assertFalse(report.isValidationSuccessful());
    assertRuleValidationResult(
        report, ImportValidationRule.ARCHIVE_NOT_TOO_NEW, ValidationTestResult.FAIL);
  }

  @Test
  public void testArchiveValidationOfCSumZipChecksumWrongContentsChecksumMissing() {
    parser.isZip = true;
    parser.calculated = 2L;
    parser.csum = 1L;
    parser.model = new ArchiveModelTSS();

    createRSpaceARchiveMAnifest();
    parser.model.manifest.addItem(ArchiveManifest.RSPACE_APPLICATION_VERSION, "1.35");
    parser.appversion = new SemanticVersion("1.36");

    ImportArchiveReport report = new ImportArchiveReport();
    IArchiveModel model =
        loadArchive(report, RSpaceTestUtils.getAnyAttachment()); // anything to make a checksum

    // is untested, because archive contents checksum isn't available
    assertTrue(report.isValidationSuccessful());
    assertRuleValidationResult(
        report, ImportValidationRule.CHECKSUM_MATCHES, ValidationTestResult.UNTESTED);
  }

  @Test
  public void testArchiveValidationOfCSum() {
    parser.isZip = true;
    parser.calculated = 2L;
    parser.csum = 2L;
    parser.model = new ArchiveModelTSS();

    createRSpaceARchiveMAnifest();
    parser.model.manifest.addItem(ArchiveManifest.RSPACE_APPLICATION_VERSION, "1.35");
    parser.appversion = new SemanticVersion("1.36");

    ImportArchiveReport report = new ImportArchiveReport();
    IArchiveModel model =
        loadArchive(report, RSpaceTestUtils.getAnyAttachment()); // anything to make a checksum

    // pass, because they match
    assertTrue(report.isValidationSuccessful());
    assertRuleValidationResult(
        report, ImportValidationRule.CHECKSUM_MATCHES, ValidationTestResult.PASS);
  }

  @Test
  public void testArchiveValidationOfCSumNoChecksumOnServer() {
    parser.isZip = true;
    parser.calculated = 2L;
    parser.csum = null;
    parser.model = new ArchiveModelTSS();

    createRSpaceARchiveMAnifest();
    parser.model.manifest.addItem(ArchiveManifest.RSPACE_APPLICATION_VERSION, "1.35");
    parser.appversion = new SemanticVersion("1.36");

    ImportArchiveReport report = new ImportArchiveReport();
    IArchiveModel model =
        loadArchive(report, RSpaceTestUtils.getAnyAttachment()); // anything to make a checksum

    // is true, because there is no checksum stored on the server, so it is UNTESTED
    assertTrue(report.isValidationSuccessful());
    assertRuleValidationResult(
        report, ImportValidationRule.CHECKSUM_MATCHES, ValidationTestResult.UNTESTED);
  }

  class ArchiveModelTSSMissingMAnifest extends ArchiveModelTSS {
    public ArchiveManifest getManifest() throws IOException {
      throw new IOException();
    }
  }

  @Test
  public void testArchiveValidationOfMissingArchiveFile() {
    parser.isZip = true;
    parser.model = new ArchiveModelTSSMissingMAnifest();
    parser.calculated = 2L;
    parser.csum = 1L;
    ImportArchiveReport report = new ImportArchiveReport();
    IArchiveModel model = loadArchive(report, new File("any"));

    // is false, since we always need an archive
    assertFalse(report.isValidationSuccessful());
    assertRuleValidationResult(
        report, ImportValidationRule.MANIFEST_FILE_PRESENT, ValidationTestResult.FAIL);
  }

  class ArchiveParserTSSNoCSum extends ArchiveParserTSS {
    protected long calculateChecksum(File zipFile) throws IOException {
      throw new IOException();
    }
  }

  @Test
  public void testArchiveValidationCantCalculateSum() {
    parser = new ArchiveParserTSSNoCSum();
    parser.isZip = true;
    parser.model = new ArchiveModelTSS();
    parser.calculated = 2L;
    parser.csum = 1L;
    createRSpaceARchiveMAnifest();
    parser.appversion = new SemanticVersion("1.36");

    ImportArchiveReport report = new ImportArchiveReport();
    parser.model.manifest.addItem(ArchiveManifest.RSPACE_APPLICATION_VERSION, "1.35");
    IArchiveModel model = loadArchive(report, new File("any"));

    // is false, since we always need an archive
    assertFalse(report.isValidationSuccessful());
    assertRuleValidationResult(
        report, ImportValidationRule.CHECKSUM_CALCULATED, ValidationTestResult.FAIL);
  }

  @Test
  public void testArchiveValidationOnlyContentsChecksumMatches() {
    parser.isZip = true;
    parser.calculated = 2L;
    parser.csum = 1L;
    parser.zipContentsChecksum = "123abc";
    parser.zipContentsChecksumCalculated = "123abc";
    parser.model = new ArchiveModelTSS();

    createRSpaceARchiveMAnifest();
    parser.model.manifest.addItem(ArchiveManifest.RSPACE_APPLICATION_VERSION, "1.35");
    parser.appversion = new SemanticVersion("1.36");

    ImportArchiveReport report = new ImportArchiveReport();
    IArchiveModel model =
        loadArchive(report, RSpaceTestUtils.getAnyAttachment()); // anything to make a checksum

    // is true, the zip contents checksums match
    assertTrue(report.isValidationSuccessful());
    assertRuleValidationResult(
        report, ImportValidationRule.CHECKSUM_MATCHES, ValidationTestResult.PASS);
  }

  @Test
  public void testArchiveValidationContentsChecksumDoesntMatch() {
    parser.isZip = true;
    parser.calculated = 2L;
    parser.csum = 1L;
    parser.zipContentsChecksum = "123abc";
    parser.zipContentsChecksumCalculated = "123abcd";
    parser.model = new ArchiveModelTSS();

    createRSpaceARchiveMAnifest();
    parser.model.manifest.addItem(ArchiveManifest.RSPACE_APPLICATION_VERSION, "1.35");
    parser.appversion = new SemanticVersion("1.36");

    ImportArchiveReport report = new ImportArchiveReport();
    IArchiveModel model =
        loadArchive(report, RSpaceTestUtils.getAnyAttachment()); // anything to make a checksum

    // is false, the zip contents checksum doesn't match
    assertFalse(report.isValidationSuccessful());
    assertRuleValidationResult(
        report, ImportValidationRule.CHECKSUM_MATCHES, ValidationTestResult.FAIL);
  }

  private void createRSpaceARchiveMAnifest() {
    parser.model.manifest = new ArchiveManifest();
    parser.model.manifest.addItem(ArchiveManifest.SOURCE, ArchiveManifest.RSPACE_SOURCE);
  }

  private IArchiveModel loadArchive(ImportArchiveReport report, File file) {
    ArchivalImportConfig cfg = new ArchivalImportConfig();
    cfg.setUnzipPath(anyFolder.getAbsolutePath());
    parser.initWorkDir(V018, report);
    IArchiveModel model = parser.loadArchive(file, report, cfg);
    report.setValidationComplete(true);
    return model;
  }

  private void assertRuleValidationResult(
      ImportArchiveReport report,
      ImportValidationRule expectedRule,
      ValidationTestResult validationResult) {
    boolean isExpected = false;
    for (ValidationResult result : report.getResults()) {
      if (result.getRule().equals(expectedRule) && result.getPassed().equals(validationResult)) {
        isExpected = true;
      }
    }
    assertTrue(isExpected);
  }

  @Test
  public void testParse() throws ArchivalFileNotExistException, IOException {
    ArchiveParserImpl realparser = new ArchiveParserImpl();
    IArchiveModel model = realparser.parse(V018, new ImportArchiveReport());
    assertEquals(1, model.getTotalRecordCount());
    assertEquals(1, model.getCurrentRecordCount());

    ArchivalDocumentParserRef ref = model.findCurrentDocArchiveByName(EXPORTED_RECORD).get(0);
    List<File> files = ref.getFileList();
    // annotation, chemd, image
    assertThat(files).hasSize(1);
    ArchivalDocument doc = ref.getArchivalDocument();
    assertThat(doc.getListFields()).hasSize(7);

    ArchiveManifest manifest = model.getManifest();
    final SemanticVersion EXPECTED_VERSION = new SemanticVersion("0.19.0.begin");
    assertEquals(EXPECTED_VERSION, manifest.getDatabaseVersion());
  }

  @Test
  public void testParseV2_17() throws ArchivalFileNotExistException, IOException {
    ArchiveParserImpl realparser = new ArchiveParserImpl();
    IArchiveModel model = realparser.parse(V217, new ImportArchiveReport());
    assertEquals(1, model.getTotalRecordCount());
    assertEquals(1, model.getCurrentRecordCount());

    ArchivalDocumentParserRef ref = model.findCurrentDocArchiveByName(EXPORTED_RECORD).get(0);
    List<File> files = ref.getFileList();
    // annotation, chemd, image
    assertThat(files).hasSize(1);
    ArchivalDocument doc = ref.getArchivalDocument();
    assertThat(doc.getListFields()).hasSize(7);

    ArchiveManifest manifest = model.getManifest();
    final SemanticVersion EXPECTED_VERSION = new SemanticVersion("0.19.0.begin");
    assertEquals(EXPECTED_VERSION, manifest.getDatabaseVersion());
    AllArchiveExternalWorkFlowMetaData testExtWF = ref.getArchiveExternalWorkFlowMetaData();
    assertNotNull(testExtWF);
    ArchiveExternalWorkFlow aewf = testExtWF.getWorkFlows().iterator().next();
    assertEquals("9ac5c918248cc7aa", aewf.getExtId());
    assertEquals("RNA-seq for Paired-end fastqs (release v1.1)", aewf.getName());
    assertEquals(1, aewf.getId());
  }
}

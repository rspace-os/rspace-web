package com.researchspace.service.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalFileNotExistException;
import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ArchiveModel;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.service.archive.ImportArchiveReport.ValidationResult;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArchiveParseTest {

  File V018 = RSpaceTestUtils.getResource("archives/v0-18/0.18Export");
  ArchiveParserTSS parser;
  File anyFolder = new File("src/test/resources/TestResources");

  class ArchiveParserTSS extends ArchiveParserImpl {
    Long csum;
    Long calculated;
    String zipContentsChecksum;
    String zipContentsChecksumCalculated;

    ArchiveModelTSS model;
    boolean isZip;
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

    protected File extractZipFolder(File zipFile, File outFolder) {
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

  @Before
  public void setUp() throws Exception {
    parser = new ArchiveParserTSS();
  }

  /*
   * A single exported experiment record from 0.18,
   */
  final String EXPORTED_RECORD = "Experiment 1";

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testArchiveValidationOfZip() {
    parser.isZip = false;
    ImportArchiveReport report = new ImportArchiveReport();
    loadArchive(report, new File("any"));
    assertFalse(report.isValidationSuccessful());
    assertRuleValidationResult(
        report, ImportValidationRule.ZIP_FILE_CAN_UNZIP, ValidationTestResult.FAIL);
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
    assertEquals(1, files.size());
    ArchivalDocument doc = ref.getArchivalDocument();
    assertEquals(7, doc.getListFields().size());
    // assertEquals(1,doc.getListFields().get(1).getAnnotMeta().size());
    // assertEquals(1,doc.getListFields().get(2).getChemMeta().size());
    // assertEquals(1,doc.getListFields().get(3).getComments().size());
    // assertEquals(1,doc.getListFields().get(4).getLinkMeta().size());

    ArchiveManifest manifest = model.getManifest();
    final SemanticVersion EXPECTED_VERSION = new SemanticVersion("0.19.0.begin");
    assertEquals(EXPECTED_VERSION, manifest.getDatabaseVersion());
  }
}

package com.researchspace.service.archive;

import static com.researchspace.core.util.progress.ProgressMonitor.NULL_MONITOR;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveImportScope;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.io.File;
import java.io.FileInputStream;
import java.util.Collection;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockMultipartFile;

/**
 * This test class is outside of the Spring tests transaction environment. This is because auditing
 * only happens after a transaction is really committed to the database, and regular Spring Tests
 * always roll back. <br>
 * Therefore it's really important to ensure that all entries made to the DB during these tests are
 * removed afterwards.
 */
@EnabledIfSystemProperty(named = "nightly", matches = "(|true)")
public class BackwardsCompatibilityTestIT extends RealTransactionSpringTestBase {

  @TempDir public File tempImportFolder;

  @Autowired ExportImport exportImportMger;
  private @Autowired @Qualifier("importUsersAndRecords") ImportStrategy importStrategy;

  File archiveRoot = new File("src/test/resources/TestResources/archives");

  @Test
  public void testImportOfOldVersions() throws Exception {
    Collection<File> zips =
        FileUtils.listFiles(
            archiveRoot,
            FileFilterUtils.and(
                FileFilterUtils.prefixFileFilter("v"), FileFilterUtils.fileFileFilter()),
            null);
    User importer = createAndSaveUser(getRandomAlphabeticString("import"));
    initUser(importer);
    logoutAndLoginAs(importer);
    for (File zip : zips) {
      FileInputStream fis = new FileInputStream(zip);
      ArchivalImportConfig cfg = new ArchivalImportConfig();
      cfg.setScope(ArchiveImportScope.IGNORE_USERS_AND_GROUPS);
      cfg.setUser(importer.getUsername());
      cfg.setUnzipPath(tempImportFolder.getAbsolutePath());
      MockMultipartFile multipart =
          new MockMultipartFile(zip.getName(), zip.getName(), "application/zip", fis);
      ImportArchiveReport report =
          exportImportMger.importArchive(
              multipart, importer.getUsername(), cfg, NULL_MONITOR, importStrategy::doImport);
      assertTrue(report.isSuccessful(), "Not successful import");
      assertFalse(report.getErrorList().hasErrorMessages());
      BaseRecord imported = report.getImportedRecords().iterator().next();
    }
  }
}

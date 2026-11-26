package com.researchspace.service.archive;

import static com.researchspace.core.util.progress.ProgressMonitor.NULL_MONITOR;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.RecordManager;
import com.researchspace.service.aws.S3ExportUtilities;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Archive import must not read files that the archive XML names outside the extracted archive.
 *
 * <p>Exports a real document with one attachment, then changes two values in the zip: the
 * attachment's {@code <linkFile>} climbs out of the archive with {@code ../} to a file this test
 * wrote, and the manifest's {@code archiveSource} is changed so the checksum check is skipped, as
 * for any archive this server did not make. Importing it must be refused, and the outside file must
 * not reach the Gallery.
 */
public class ArchiveImportLinkFileTraversalIT extends RealTransactionSpringTestBase {

  @TempDir public File tempExportFolder;
  @TempDir public File tempImportFolder;
  @TempDir public File outsideArchiveFolder;

  private @Autowired ExportImport exportImportMgr;
  private @Autowired RecordManager recordManager;
  private @Autowired FileStore fileStore;
  private @Autowired @Qualifier("importUsersAndRecords") ImportStrategy importStrategy;

  @Mock private S3ExportUtilities s3ExportUtilities;

  @Autowired
  @Qualifier("standardPostExportCompletionImpl")
  @InjectMocks
  private PostArchiveCompletion standardPostExport;

  private AutoCloseable mocks;

  @BeforeEach
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
    mocks.close();
  }

  @Test
  public void linkFileTraversalMustNotReadFileOutsideArchiveIntoGallery() throws Exception {
    User user = createInitAndLoginAnyUser();

    // a document with one file attachment in its field, so the export carries an attachment
    // <linkFile> we can tamper
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "traversal-target-doc");
    Field field = doc.getFields().get(0);
    addAttachmentDocumentToField(RSpaceTestUtils.getResource("testTxt.txt"), field, user);

    ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder);
    cfg.setArchiveType(ArchiveExportConfig.XML);
    ExportSelection selection =
        ExportSelection.createRecordsExportSelection(
            new Long[] {doc.getId()}, new String[] {"NORMAL"});
    ArchiveResult exportResult =
        exportImportMgr
            .asyncExportSelectionToArchive(
                selection, cfg, user, new URI("http://example.test"), standardPostExport)
            .get();
    File zip = exportResult.getExportFile();

    // the file the attacker wants to steal; it lives OUTSIDE the archive
    String marker = "ARCHIVE-IMPORT-TRAVERSAL-" + UUID.randomUUID();
    File secret = new File(outsideArchiveFolder, "secret.txt");
    Files.writeString(secret.toPath(), marker);
    // enough ../ to climb to the filesystem root from any temp depth, then back down to the secret
    String traversalLinkFile = "../".repeat(40) + secret.getAbsolutePath().substring(1);

    rewriteExportedArchive(zip, traversalLinkFile);

    ArchivalImportConfig importCfg = createDefaultArchiveImportConfig(user, tempImportFolder);
    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(zip.getName(), zip),
            user.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);

    assertTrue(
        report.getResults().stream()
            .anyMatch(
                r ->
                    r.getRule() == ImportValidationRule.FILE_NAMES_INSIDE_ARCHIVE
                        && r.getPassed() == ValidationTestResult.FAIL));
    assertFalse(
        galleryDocumentsContainMarker(user, marker),
        "archive import read a file from outside the archive into the Gallery");
  }

  /**
   * Rewrites just two values in the exported archive in place: the attachment's {@code <linkFile>}
   * (pointed outside the archive) and the manifest {@code archiveSource} (so the checksum check is
   * skipped, as it is for any archive this server did not create).
   */
  private void rewriteExportedArchive(File zip, String traversalLinkFile) throws Exception {
    try (FileSystem fs = FileSystems.newFileSystem(zip.toPath(), (ClassLoader) null)) {
      for (Path root : fs.getRootDirectories()) {
        try (Stream<Path> walk = Files.walk(root)) {
          List<Path> files = walk.filter(Files::isRegularFile).toList();
          for (Path entry : files) {
            String name = entry.getFileName().toString();
            if (name.equals(ExportImport.EXPORT_MANIFEST)) {
              String manifest = Files.readString(entry, StandardCharsets.UTF_8);
              manifest =
                  manifest.replaceAll("archiveSource:[^\\r\\n]*", "archiveSource:ForeignSystem");
              Files.writeString(entry, manifest, StandardCharsets.UTF_8);
            } else if (name.endsWith(".xml")
                && !name.endsWith("_form.xml")
                && !name.endsWith("_externalWorkflows.xml")) {
              String xml = Files.readString(entry, StandardCharsets.UTF_8);
              if (xml.contains("<linkFile>")) {
                xml =
                    xml.replaceAll(
                        "<linkFile>.*?</linkFile>",
                        "<linkFile>" + Matcher.quoteReplacement(traversalLinkFile) + "</linkFile>");
                Files.writeString(entry, xml, StandardCharsets.UTF_8);
              }
            }
          }
        }
      }
    }
  }

  private boolean galleryDocumentsContainMarker(User user, String marker) throws Exception {
    Folder docFolder =
        recordManager.getGalleryMediaFolderForUser(MediaUtils.DOCUMENT_MEDIA_FLDER_NAME, user);
    List<BaseRecord> records =
        recordManager
            .listFolderRecords(
                docFolder.getId(), PaginationCriteria.createDefaultForClass(BaseRecord.class))
            .getResults();
    openTransaction();
    try {
      for (BaseRecord record : records) {
        if (!record.isMediaRecord()) {
          continue;
        }
        EcatMediaFile media = (EcatMediaFile) recordManager.get(record.getId());
        Optional<FileInputStream> in = fileStore.retrieve(media.getFileProperty());
        if (in.isPresent()) {
          try (FileInputStream stream = in.get()) {
            if (new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains(marker)) {
              return true;
            }
          }
        }
      }
    } finally {
      commitTransaction();
    }
    return false;
  }
}

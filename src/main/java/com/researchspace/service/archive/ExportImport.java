package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.service.archive.export.ExportRemovalPolicy;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.multipart.MultipartFile;

/** Top-level interface for export /import operations */
public interface ExportImport {
  String SCHEMAS_FOLDER = "schemas";

  /** Name of record XSD file */
  String ZIP_SCHEMA = "documentSchema.xsd";

  /** Name of form XSD file */
  String ZIP_FORM_SCHEMA = "formSchema.xsd";

  /** Name of manifest file */
  String EXPORT_MANIFEST = "manifest.txt";

  /** Name of listing of Nfs links for XML export */
  String NFS_EXPORT_XML = "nfsExports.xml";

  /** Name of link resolve XML file */
  String ZIP_LINK_SOLVER = "linkResolver.xml";

  /** Name of XML file to store the folder relationships */
  String FOLDER_TREE = "folderTree.xml";

  /** NAme of folder to hold static resources - e.g., icons */
  String RESOURCES = "resources";

  /** XML file name for user/group information */
  String USERS = "users.xml";

  /** Schema file name for archive users. */
  String USERS_SCHEMA = "usersSchema.xsd";

  /** XML file name for messages information */
  String MESSAGES = "messages.xml";

  /** Schema file name for archive messages. */
  String MESSAGES_SCHEMA = "messagesSchemax.xsd";

  Pattern EXPORTED_ARCHIVE_NAME_PATTERN = Pattern.compile("^[\\w\\-]+(\\.zip)?(\\.csv)?(\\.eln)?$");

  /**
   * Exports one or more records identified by exportIds to PDF/Word, asynchronously.
   *
   * @param exportIds
   * @param exportNames
   * @param exportTypes
   * @param config
   * @param user
   * @return A future of generated PDF file, or <code>null</code> if not generated.
   * @throws IOException
   */
  @Async(value = "archiveTaskExecutor")
  Future<EcatDocumentFile> asynchExportFromSelection(
      Long[] exportIds,
      String[] exportNames,
      String[] exportTypes,
      ExportToFileConfig config,
      User user)
      throws IOException;

  @Async(value = "archiveTaskExecutor")
  void handlePossibleRollbackAsync(Future<EcatDocumentFile> doc) throws IOException;

  /** EXports one or more records identified by exportIds to PDF, synchronously. */
  EcatDocumentFile synchExportFromSelection(
      Long[] exportIds,
      String[] exportNames,
      String[] exportTypes,
      ExportToFileConfig config,
      User u)
      throws IOException;

  /**
   * Asynchronously exports one or more records identified by exportIds to RSpace archive format.
   *
   * @param exportSelection
   * @param config Configuration object for the export
   * @param user the exporter
   * @param baseURL
   * @param postArchiveCompletion a {@link PostArchiveCompletion} implementation
   * @return an {@link Future<ArchiveResult>}
   */
  @Async(value = "archiveTaskExecutor")
  Future<ArchiveResult> exportRecordSelection(
      ExportSelection exportSelection,
      ArchiveExportConfig config,
      User user,
      URI baseURL,
      PostArchiveCompletion postArchiveCompletion);

  ExportRecordList getExportRecordList(ExportSelection exportSelection, ArchiveExportConfig expCfg);

  List<Long> getExportIDsForTagRetrievalForPDFExportFromRoot(
      User exporter, ExportToFileConfig exportConfig);

  List<Long> getExportIDsForTagRetrievalForPDFExportFromFilesAndFolders(
      List<Long> ids, ExportToFileConfig exportConfig, User exporter);

  /**
   * Synchronously exports one or more records identified by exportIds to RSpace archive format.
   *
   * @param config Configuration object for the export
   * @param user the exporter
   * @param baseURL
   * @param postArchiveCompletion a {@link PostArchiveCompletion} implementation
   * @param planner a supplier of items to export
   * @return an {@link ArchiveResult}
   * @throws Exception
   */
  ArchiveResult exportSyncRecordSelection(
      ArchiveExportConfig config,
      User user,
      URI baseURL,
      PostArchiveCompletion postArchiveCompletion,
      Supplier<ExportRecordList> planner)
      throws Exception;

  /**
   * Imports an RSpace archive file
   *
   * @param file the archive file
   * @param importer
   * @param importCfg
   * @param progress
   * @param strategy
   * @return
   * @throws Exception
   */
  ImportArchiveReport importArchive(
      MultipartFile file,
      String importer,
      ArchivalImportConfig importCfg,
      ProgressMonitor progress,
      ImportStrategy strategy)
      throws Exception;

  /**
   * VAriant that will import already-existing zip file on the server.
   *
   * @param zipFile
   * @param importer
   * @param iconfig
   * @param monitor
   */
  ImportArchiveReport importArchive(
      File zipFile,
      String importer,
      ArchivalImportConfig iconfig,
      ProgressMonitor monitor,
      ImportStrategy strategy)
      throws Exception;

  /**
   * Exports all records for a user to RSpace archive format.
   *
   * @param config
   * @param toExport the user's folder to export.
   * @param baseURL
   * @param exporter the user who is performing the export.
   * @param postArchiveCompletion
   * @return
   * @throws IOException
   */
  @Async(value = "archiveTaskExecutor")
  Future<ArchiveResult> exportArchiveAsyncUserWork(
      ArchiveExportConfig config,
      User toExport,
      URI baseURL,
      User exporter,
      PostArchiveCompletion postArchiveCompletion)
      throws Exception;

  ArchiveResult exportArchiveSyncUserWork(
      ArchiveExportConfig expCfg,
      User toExport,
      URI baseURL,
      User exporter,
      PostArchiveCompletion postArchiveCompleter,
      Supplier<ExportRecordList> exportIdSupplier)
      throws Exception;

  /**
   * Exports all records for a group to RSpace archive format asynchronously as determined by a
   * TaskExecutor.
   *
   * @param config
   * @param exporter the user performing the export.
   * @param groupId the group's id
   * @return
   * @throws Exception
   */
  @Async(value = "archiveTaskExecutor")
  Future<ArchiveResult> exportAsyncGroup(
      ArchiveExportConfig config,
      User exporter,
      Long groupId,
      URI baseURL,
      PostArchiveCompletion postArchiveCompletion)
      throws Exception;

  /**
   * Exports all records for a group to RSpace archive format synchronously in calling thread.
   *
   * @param config
   * @param exporter the user performing the export.
   * @param groupId the group's id
   * @return
   * @throws Exception
   */
  ArchiveResult exportSyncGroup(
      ArchiveExportConfig config,
      User exporter,
      Long groupId,
      URI baseURL,
      PostArchiveCompletion postArchiveCompletion,
      Supplier<ExportRecordList> exportIdSupplier)
      throws Exception;

  /**
   * Removes archives based on the supplied {@link ExportRemovalPolicy}.
   *
   * <p>This method is scheduled; see applicationContext-service.xml for its configuration using
   * Spring Quartz scheduler.
   */
  public void removeOldArchives();

  /**
   * Exports a PDF of all the user's records.
   *
   * @param toExport the user whose work is to be exported
   * @param config PDF config
   * @param exporter the user performing the export
   * @return
   * @throws IOException AuthorizationException if <code>exporter</code> doesn't have permission to
   *     export <code>toExport's</code> data.
   */
  @Async(value = "archiveTaskExecutor")
  public Future<EcatDocumentFile> exportPdfOfAllUserRecords(
      User toExport, ExportToFileConfig config, User exporter) throws IOException;

  /**
   * Asynchronously exports a whole group's records into PDF format
   *
   * @param expCfg A {@link ExportToFileConfig}
   * @param exporter The user performing the export
   * @param groupId The id of the group to export
   * @return
   * @throws AuthorizationException if <code>exporter</code> doesn't have write permission on the
   *     group.
   */
  @Async(value = "archiveTaskExecutor")
  public Future<EcatDocumentFile> exportGroupPdf(
      ExportToFileConfig expCfg, User exporter, Long groupId);

  @Async(value = "archiveTaskExecutor")
  Future<File> asynchExportFromSelectionForSigning(
      Long[] exportIds,
      String[] exportNames,
      String[] exportTypes,
      ExportToFileConfig config,
      User exporter)
      throws IOException;

  /**
   * Stream archive to response. Limited to XML/HTML zip archives
   *
   * @param zipname
   * @param response
   */
  public void streamArchiveDownload(String zipname, HttpServletResponse response);

  /**
   * Gets {@link ArchivalCheckSum} for archives that are not deleted, i.e that are still available
   */
  List<ArchivalCheckSum> getCurrentArchiveMeta();

  /**
   * Permissions test for exporting another user's work
   *
   * @param userToExport
   * @param exporter
   * @throws AuthorizationException if <code>exporter</code> not authorised.
   */
  void assertExporterCanExportUsersWork(User userToExport, User exporter);
}

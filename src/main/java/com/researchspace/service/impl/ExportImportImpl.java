package com.researchspace.service.impl;

import com.google.common.io.Files;
import com.researchspace.archive.ArchivalFileNotExistException;
import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchivalMeta;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.core.util.ResponseUtil;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.events.ImportCompleted;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.repository.spi.ExternalId;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.RSMetaDataManager;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.service.UserManager;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import com.researchspace.service.archive.ArchiveImporterManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.ImportArchiveReport;
import com.researchspace.service.archive.ImportStrategy;
import com.researchspace.service.archive.PdfWordExportManager;
import com.researchspace.service.archive.PostArchiveCompletion;
import com.researchspace.service.archive.export.ArchiveExportPlanner;
import com.researchspace.service.archive.export.ArchiveRemover;
import com.researchspace.service.archive.export.ExportFailureException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * Top-level service interface for all export, archive and import activities. This is <b>NOT</b>
 * transactional - calls to individual services are currently run in separate transactions, so
 * methods in this class may not be atomic.
 */
public class ExportImportImpl extends AbstractExporter implements ExportImport {

  private static final String SOURCE_URL = "Source URL";
  private static final int BUFFER_SIZE = 16384;

  private Logger log = LoggerFactory.getLogger(ExportImportImpl.class);

  // from rs.properties
  @Value("${rsversion}")
  private String rsversion;

  private @Autowired IPermissionUtils permissions;

  private @Autowired CommunicationManager commMgr;
  private @Autowired RSMetaDataManager metaDataMgr;
  private @Autowired FolderManager folderManager;
  private @Autowired @Lazy GroupManager grpMgr;

  private @Autowired Collection<ArchiveExportServiceManager> archiverServiceManagers;
  private @Autowired ArchiveImporterManager archiveImporter;
  private @Autowired PdfWordExportManager pdfWordExportManager;

  private @Autowired UserExternalIdResolver extIdResolver;
  private @Autowired MessageSource messageSource;
  private @Autowired IPropertyHolder properties;
  private @Autowired ResponseUtil responseUtil;
  private @Autowired UserManager userManager;
  private @Autowired OperationFailedMessageGenerator authGenerator;
  private @Autowired ArchiveRemover archiveRemover;
  private @Autowired ApplicationEventPublisher publisher;

  private @Autowired ArchiveExportPlanner archivePlanner;

  void setResponseUtil(ResponseUtil responseUtil) {
    this.responseUtil = responseUtil;
  }

  public Future<EcatDocumentFile> exportPdfOfAllUserRecords(
      User toExport, ExportToFileConfig config, User exporter) throws IOException {
    Folder rootRecord = folderManager.getRootFolderForUser(toExport);
    config.setExportScope(ExportScope.USER);
    if (rootRecord == null) {
      throw new IllegalStateException(
          "User "
              + toExport.getFullName()
              + "'s account has not been initialised - there is nothing to export!");
    }
    Long[] exportIds = new Long[] {rootRecord.getId()};
    String[] exportTypes = new String[] {RecordType.FOLDER.name()};
    String[] exportNames = new String[] {rootRecord.getName()};
    EcatDocumentFile ecatdoc =
        pdfWordExportManager.doExport(
            toExport, exportIds, exportNames, exportTypes, config, exporter);
    return new AsyncResult<>(ecatdoc);
  }

  @Override
  public Future<EcatDocumentFile> asynchExportFromSelection(
      Long[] exportIds,
      String[] exportNames,
      String[] exportTypes,
      ExportToFileConfig config,
      User exporter)
      throws IOException {
    EcatDocumentFile ecatdoc =
        pdfWordExportManager.doExport(
            exporter, exportIds, exportNames, exportTypes, config, exporter);
    return new AsyncResult<>(ecatdoc);
  }

  @Override
  public Future<File> asynchExportFromSelectionForSigning(
      Long[] exportIds,
      String[] exportNames,
      String[] exportTypes,
      ExportToFileConfig config,
      User exporter)
      throws IOException {
    File pdfExport =
        pdfWordExportManager.doExportForSigning(
            exporter, exportIds, exportNames, exportTypes, config, exporter);
    return new AsyncResult<>(pdfExport);
  }

  @Override
  public EcatDocumentFile synchExportFromSelection(
      Long[] exportIds,
      String[] exportNames,
      String[] exportTypes,
      ExportToFileConfig config,
      User exporter)
      throws IOException {
    return pdfWordExportManager.doExport(
        exporter, exportIds, exportNames, exportTypes, config, exporter);
  }

  private void postArchiveExportFailure(String exportName, User u, String detailMsg) {
    String msg =
        messageSource.getMessage(
            "workspace.export.msgFailure",
            new String[] {StringEscapeUtils.escapeHtml(exportName), detailMsg},
            null);
    commMgr.systemNotify(NotificationType.PROCESS_COMPLETED, msg, u.getUsername(), true);
  }

  /** Runs asynchronously */
  @Override
  public Future<ArchiveResult> exportRecordSelection(
      ExportSelection exportSelection,
      ArchiveExportConfig expCfg,
      User exporter,
      URI baseURL,
      PostArchiveCompletion postArchiveCompleter) {
    preArchiveSelection(expCfg, exporter);
    Supplier<ExportRecordList> exportListSupplier =
        () -> archivePlanner.createExportRecordList(expCfg, exportSelection);
    ArchiveResult result =
        doArchiveSelection(expCfg, exporter, baseURL, postArchiveCompleter, exportListSupplier);
    return new AsyncResult<>(result);
  }

  @Override
  public ExportRecordList getExportRecordList(
      ExportSelection exportSelection, ArchiveExportConfig expCfg) {
    return archivePlanner.createExportRecordList(expCfg, exportSelection);
  }

  @Override
  public List<Long> getExportIDsForTagRetrievalForPDFExportFromRoot(
      User exporter, ExportToFileConfig exportConfig) {
    Folder rootRecord = folderManager.getRootFolderForUser(exporter);
    return getExportIDsForTagRetrievalForPDFExportFromFilesAndFolders(
        List.of(rootRecord.getId()), exportConfig, exporter);
  }

  @Override
  public List<Long> getExportIDsForTagRetrievalForPDFExportFromFilesAndFolders(
      List<Long> ids, ExportToFileConfig exportConfig, User exporter) {
    return pdfWordExportManager.getExportIDsForTagRetrievalFromFilesAndFolders(
        ids, exportConfig, exporter);
  }

  @Override
  public ArchiveResult exportSyncRecordSelection(
      ArchiveExportConfig expCfg,
      User user,
      URI baseURL,
      PostArchiveCompletion postArchiveCompleter,
      Supplier<ExportRecordList> planner) {
    preArchiveSelection(expCfg, user);
    return doArchiveSelection(expCfg, user, baseURL, postArchiveCompleter, planner);
  }

  private void preArchiveSelection(ArchiveExportConfig expCfg, User user) {
    try {
      createTopLevelExportFolder(expCfg);
    } catch (IOException ie) {
      throw new ExportFailureException("Could not create archive folder", ie);
    }
    expCfg.setExporter(user);
  }

  private ArchiveResult doArchiveSelection(
      ArchiveExportConfig expCfg,
      User user,
      URI baseURL,
      PostArchiveCompletion postArchiveCompleter,
      Supplier<ExportRecordList> planner) {
    try {
      ArchiveResult result = doArchive(expCfg, baseURL, planner);
      postArchiveCompletionOperations(postArchiveCompleter, expCfg, user, result);
      return result;
    } catch (Exception e) {
      log.error("Export attempt failed", e);
      postArchiveExportFailure(expCfg.getDescription(), expCfg.getExporter(), e.getMessage());
      throw e;
    }
  }

  private void postArchiveCompletionOperations(
      PostArchiveCompletion postArchiveCompleter,
      IArchiveExportConfig expCfg,
      User user,
      ArchiveResult result) {
    try {
      postArchiveCompleter.postArchiveCompletionOperations(expCfg, user, result);
    } catch (Exception e) {
      log.warn("Error during Post Archive Completion Operations: {}", e.getMessage());
      postArchiveExportFailure(expCfg.getDescription(), expCfg.getExporter(), e.getMessage());
    }
  }

  private void getAppDBVersion(ArchiveManifest props) {
    props.addItem(ArchiveManifest.DB_VERSIONKEY, metaDataMgr.getDatabaseVersion().toString());
    props.addItem(ArchiveManifest.RSPACE_APPLICATION_VERSION, rsversion);
  }

  @Override
  public ImportArchiveReport importArchive(
      MultipartFile file,
      String importer,
      ArchivalImportConfig iconfig,
      ProgressMonitor monitor,
      ImportStrategy importStrategy)
      throws Exception {
    String tempDir = Files.createTempDir().getAbsolutePath();
    File oufolder = new File(tempDir);
    File zipFile = multipartToFile(file, oufolder);
    ImportArchiveReport report =
        doImport(zipFile, importer, iconfig, monitor, tempDir, importStrategy);
    publisher.publishEvent(new ImportCompleted(report, importer));
    return report;
  }

  @Override
  public ImportArchiveReport importArchive(
      File zipFile,
      String importer,
      ArchivalImportConfig iconfig,
      ProgressMonitor monitor,
      ImportStrategy importStrategy)
      throws Exception {
    String tempDir = Files.createTempDir().getAbsolutePath();
    ImportArchiveReport report =
        doImport(zipFile, importer, iconfig, monitor, tempDir, importStrategy);
    publisher.publishEvent(new ImportCompleted(report, importer));
    return report;
  }

  private ImportArchiveReport doImport(
      File zipFile,
      String importer,
      ArchivalImportConfig iconfig,
      ProgressMonitor monitor,
      String tempDir,
      ImportStrategy importStrategy) {
    iconfig.setZipPath(tempDir);
    iconfig.setUnzipPath(tempDir);
    log.info("Unzipping archive into {}", tempDir);
    iconfig.setUser(importer);

    return archiveImporter.importArchive(zipFile, iconfig, monitor, importStrategy);
  }

  private File multipartToFile(MultipartFile multipart, File ouFolder)
      throws IllegalStateException, IOException {
    File tmpFile = new File(ouFolder, multipart.getOriginalFilename());
    multipart.transferTo(tmpFile);
    return tmpFile;
  }

  @Override
  public Future<ArchiveResult> exportArchiveAsyncUserWork(
      ArchiveExportConfig expCfg,
      User toExport,
      URI baseURL,
      User exporter,
      PostArchiveCompletion postArchiveCompleter)
      throws Exception {
    createTopLevelExportFolder(expCfg);
    ExportSelection userSelection =
        ExportSelection.createUserExportSelection(toExport.getUsername());
    expCfg.setExporter(exporter);
    expCfg.setProgressMonitor(ProgressMonitor.NULL_MONITOR);
    Supplier<ExportRecordList> rcdList =
        () -> archivePlanner.createExportRecordList(expCfg, userSelection);
    ArchiveResult result =
        doSynchUserArchive(expCfg, baseURL, postArchiveCompleter, userSelection, rcdList);
    return new AsyncResult<>(result);
  }

  @Override
  public ArchiveResult exportArchiveSyncUserWork(
      ArchiveExportConfig expCfg,
      User toExport,
      URI baseURL,
      User exporter,
      PostArchiveCompletion postArchiveCompleter,
      Supplier<ExportRecordList> exportIdSupplier)
      throws Exception {

    createTopLevelExportFolder(expCfg);
    ExportSelection userSelection =
        ExportSelection.createUserExportSelection(toExport.getUsername());
    expCfg.setExporter(exporter);

    return doSynchUserArchive(
        expCfg, baseURL, postArchiveCompleter, userSelection, exportIdSupplier);
  }

  private ArchiveResult doSynchUserArchive(
      IArchiveExportConfig expCfg,
      URI baseURL,
      PostArchiveCompletion postArchiveCompleter,
      ExportSelection userSelection,
      Supplier<ExportRecordList> rcdList)
      throws Exception {
    try {
      ArchiveResult result = doArchive(expCfg, baseURL, rcdList);
      postArchiveCompletionOperations(postArchiveCompleter, expCfg, expCfg.getExporter(), result);
      return result;
    } catch (Exception e) {
      log.error("Export attempt failed", e);
      postArchiveExportFailure(expCfg.getDescription(), expCfg.getExporter(), e.getMessage());
      throw e;
    }
  }

  private ArchiveResult doArchive(
      final IArchiveExportConfig expCfg,
      URI baseURL,
      Supplier<ExportRecordList> exportListSupplier) {

    ArchiveManifest manif = initializeArchiveManifest(expCfg.getExporter());
    ImmutableExportRecordList rcdList = exportListSupplier.get();
    configureArchiveMetadata(expCfg, baseURL, manif);
    try {
      ArchiveExportServiceManager mgr = getManager(expCfg);
      return mgr.exportArchive(manif, rcdList, expCfg);
    } catch (Exception ex) {
      throw new ExportFailureException(ex);
    }
  }

  private void configureArchiveMetadata(
      IArchiveExportConfig expCfg, URI baseURL, ArchiveManifest manif) {
    ArchivalMeta am = expCfg.getArchivalMeta();
    am.setSource(baseURL.toString());
    manif.addItem(SOURCE_URL, baseURL.toString());
  }

  private ArchiveExportServiceManager getArchiveManager() {
    return archiverServiceManagers.iterator().next();
  }

  private ArchiveExportServiceManager getManager(IArchiveExportConfig expCfg) {
    for (ArchiveExportServiceManager mgr : archiverServiceManagers) {
      if (mgr.isArchiveType(expCfg.getArchiveType())) {
        return mgr;
      }
    }
    return null;
  }

  private ArchiveManifest initializeArchiveManifest(User exporter) {
    ArchiveManifest manifest = new ArchiveManifest();
    manifest.addItem(ArchiveManifest.SOURCE, ArchiveManifest.RSPACE_SOURCE);
    manifest.addItem("Exported by", exporter.getFullName());
    // RSPAC-1023
    Optional<ExternalId> extId =
        extIdResolver.getExternalIdForUser(exporter, IdentifierScheme.ORCID);
    if (extId.isPresent()) {
      manifest.addItem("OrcidID", extId.get().getIdentifier());
    }
    getAppDBVersion(manifest);
    return manifest;
  }

  @Override
  public Future<EcatDocumentFile> exportGroupPdf(
      ExportToFileConfig expCfg, User exporter, Long groupId) {

    expCfg.setExportScope(ExportScope.GROUP);
    Group grp = grpMgr.getGroup(groupId);

    archivePlanner.checkGroupExportPermissions(exporter, grp);
    List<Long> ids = new ArrayList<>();
    List<String> types = new ArrayList<>();
    List<String> names = new ArrayList<>();
    archivePlanner.getGroupMembersRootFolderIds(grp, exporter, ids, types, names);

    try {
      EcatDocumentFile ecatdoc =
          pdfWordExportManager.doExport(
              exporter,
              ids.toArray(new Long[0]),
              names.toArray(new String[0]),
              types.toArray(new String[0]),
              expCfg,
              exporter);
      return new AsyncResult<>(ecatdoc);
    } catch (IOException e) {
      log.error("Error performing export.", e);
    }
    return null;
  }

  @Override
  public Future<ArchiveResult> exportAsyncGroup(
      ArchiveExportConfig expCfg,
      User exporter,
      Long groupId,
      URI baseURL,
      PostArchiveCompletion postArchiveCompleter)
      throws Exception {
    createTopLevelExportFolder(expCfg);
    ExportSelection exportSelection = configureGroupExportCfg(expCfg, exporter, groupId);
    ArchiveResult result =
        doGroupArchive(
            expCfg,
            exporter,
            baseURL,
            postArchiveCompleter,
            exportSelection,
            () -> archivePlanner.createExportRecordList(expCfg, exportSelection));
    return new AsyncResult<>(result);
  }

  @Override
  public ArchiveResult exportSyncGroup(
      ArchiveExportConfig expCfg,
      User exporter,
      Long groupId,
      URI baseURL,
      PostArchiveCompletion postArchiveCompleter,
      Supplier<ExportRecordList> exportIdSupplier)
      throws Exception {
    createTopLevelExportFolder(expCfg);
    ExportSelection exportSelection = configureGroupExportCfg(expCfg, exporter, groupId);
    return doGroupArchive(
        expCfg, exporter, baseURL, postArchiveCompleter, exportSelection, exportIdSupplier);
  }

  private ArchiveResult doGroupArchive(
      ArchiveExportConfig expCfg,
      User exporter,
      URI baseURL,
      PostArchiveCompletion postArchiveCompleter,
      ExportSelection exportSelection,
      Supplier<ExportRecordList> exportIdSupplier) {
    try {
      ArchiveResult result = doArchive(expCfg, baseURL, exportIdSupplier);
      postArchiveCompletionOperations(postArchiveCompleter, expCfg, exporter, result);
      return result;

    } catch (ExportFailureException exception) {
      handleGrpExportFailure(expCfg, exception);
      throw exception;
    }
  }

  private void handleGrpExportFailure(
      ArchiveExportConfig expCfg, ExportFailureException exception) {
    log.error("Export attempt failed", exception);
    postArchiveExportFailure(expCfg.getDescription(), expCfg.getExporter(), exception.getMessage());
  }

  private ExportSelection configureGroupExportCfg(
      ArchiveExportConfig expCfg, User exporter, Long groupId) {
    ExportSelection exportSelection = ExportSelection.createGroupExportSelection(groupId);
    Group grp = grpMgr.getGroup(exportSelection.getGroupId());
    expCfg.setUserOrGroupId(grp.getOid());
    expCfg.setExporter(exporter);
    return exportSelection;
  }

  private void createTopLevelExportFolder(ArchiveExportConfig expCfg) throws IOException {
    if (expCfg.getTopLevelExportFolder() == null) {
      File createdDir = exportUtils.createFolder(properties.getExportFolderLocation());
      expCfg.setTopLevelExportFolder(createdDir);
    }
  }

  @Override
  public void removeOldArchives() {
    archiveRemover.removeOldArchives(getArchiveManager());
  }

  @Override
  public List<ArchivalCheckSum> getCurrentArchiveMeta() {
    return getArchiveManager().getCurrentArchiveMetadatas();
  }

  @Override
  public void streamArchiveDownload(String zipname, HttpServletResponse response) {
    String fnm = zipname;
    if (!EXPORTED_ARCHIVE_NAME_PATTERN.matcher(fnm).matches()) {
      throw new IllegalArgumentException(
          messageSource.getMessage(
              "errors.invalidstringformat",
              new String[] {zipname, "zipname", EXPORTED_ARCHIVE_NAME_PATTERN.toString()},
              null));
    }
    if ((fnm.indexOf("zip") <= 0) && (fnm.indexOf(".csv") <= 0) && (fnm.indexOf(".eln") <= 0)) {
      fnm = fnm + ".zip"; // sometimes zip out.
    }
    File folder = new File(properties.getExportFolderLocation());
    File file = new File(folder, fnm);

    try (FileInputStream in = new FileInputStream(file)) {
      log.info("Streaming export file {} of size {}", file.getAbsolutePath(), file.length());
      response.setContentType(MediaType.APPLICATION_OCTET_STREAM.toString());
      // RSPAC-1
      response.setHeader("Content-Length", String.valueOf(file.length()));
      responseUtil.setContentDisposition(response, file.getName());

      OutputStream outStream = response.getOutputStream();
      byte[] buffer = new byte[BUFFER_SIZE];
      int bytesRead = -1;

      while ((bytesRead = in.read(buffer)) != -1) {
        outStream.write(buffer, 0, bytesRead);
      }
    } catch (Exception ie) {
      log.warn("Export download failure: {}", ie.getMessage());
      String msg = messageSource.getMessage("archive.download.failure.msg", new String[] {}, null);
      throw new ArchivalFileNotExistException(msg);
    }
  }

  @Override
  public void assertExporterCanExportUsersWork(User userToExport, User exporter) {
    // shortcut if is sysadmin
    if (exporter.hasRole(Role.SYSTEM_ROLE)) {
      return;
    }
    if (exporter.hasRole(Role.ADMIN_ROLE)
        && userManager.isUserInAdminsCommunity(exporter, userToExport.getUsername())) {
      return;
    }
    try {
      // Get root record throws AuthorizationException, in case, exporter is not authorized to
      // access it
      Folder userToExportRootFolder = folderManager.getRootRecordForUser(exporter, userToExport);

      // Let's check READ permission explicitly
      if (!permissions.isPermitted(userToExportRootFolder, PermissionType.READ, exporter)) {
        throw new AuthorizationException();
      }
    } catch (AuthorizationException e) {
      String msg =
          authGenerator.getFailedMessage(
              exporter.getUsername(), " export records of [" + userToExport.getUsername() + "]");
      throw new AuthorizationException(msg);
    }
  }

  void setMessageSource(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @Override
  public void handlePossibleRollbackAsync(Future<EcatDocumentFile> doc) throws IOException {
    try {
      doc.get();
    } catch (Exception e) {
      log.error(
          "Export failed: {} - root cause is {}",
          e.getMessage(),
          e.getCause() != null ? e.getCause().getMessage() : "unknown");
    }
  }
}

package com.researchspace.webapp.controller;

import static com.researchspace.session.SessionAttributeUtils.RS_IMPORT_XML_ARCHIVE_PROGRESS;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.util.ResponseUtil;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.export.pdf.ExportConfigurer;
import com.researchspace.export.pdf.ExportFormat;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.dtos.export.ExportArchiveDialogConfigDTO;
import com.researchspace.model.dtos.export.ExportDialogConfigDTO;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.repository.RepoDepositConfig;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.DiskSpaceChecker;
import com.researchspace.service.DocumentTagManager;
import com.researchspace.service.RepositoryDepositHandler;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.IExportUtils;
import com.researchspace.service.archive.ImportArchiveReport;
import com.researchspace.service.archive.ImportFailureException;
import com.researchspace.service.archive.ImportStrategy;
import com.researchspace.service.archive.PostArchiveCompletion;
import com.researchspace.service.archive.export.ArchiveExportPlanner;
import com.researchspace.service.impl.OntologyDocManager;
import com.researchspace.session.SessionAttributeUtils;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Handles export and import-related actions. */
@Controller
@RequestMapping("/export")
public class ExportController extends BaseController {

  public static final String SUBMITTED_SUCCESS_MSG = "Submitted";

  public static final String IMPORT_FORM_ERROR_ATTR_NAME = "importFormError";
  public static final String IMPORT_ONTOLOGY_ERROR_ATTR_NAME = "importOntologyError";
  public static final String PLEASE_UPLOAD_A_CSV_FILE = "Please upload a CSV file";

  public static final int maxIdsToProcess = 100;

  private @Autowired ExportImport exportManager;
  private @Autowired UserAppConfigManager userAppConfigMgr;
  private @Autowired CommunicationManager commMgr;
  private @Autowired IExportUtils pdfUtils;
  private @Autowired ExportConfigurer configurer;
  @Autowired private ArchiveExportPlanner archiveExportPlanner;

  @Qualifier("standardPostExportCompletionImpl")
  private @Autowired PostArchiveCompletion standardPostExport;

  private @Autowired IGroupPermissionUtils groupPermUtils;
  private @Autowired UserExportHandler userExportHandler;
  private @Autowired DiskSpaceChecker diskSpaceChecker;
  private @Autowired NfsController nfsController;
  private @Autowired @Qualifier("importUsersAndRecords") ImportStrategy importStrategy;
  @Autowired private OntologyDocManager ontologyImportManager;

  private @Autowired ResponseUtil responseUtil;
  @Lazy @Autowired private DocumentTagManager documentTagManager;

  protected void setResponseUtil(ResponseUtil responseUtil) {
    this.responseUtil = responseUtil;
  }

  private @Autowired RepositoryDepositHandler depositHandler;

  void setDepositHandler(RepositoryDepositHandler depositHandler) {
    this.depositHandler = depositHandler;
  }

  private void validateAppIsRepository(StringBuilder erbf, App app) {
    if (!app.isRepositoryApp()) {
      erbf.append(getText("invalid.app.choice", new String[] {app.getName(), "Repository"}));
    }
  }

  private void validateCfgIfExists(
      Long appConfigSetId, StringBuilder erbf, Optional<AppConfigElementSet> cfg) {
    if (appConfigSetId > 0 && !cfg.isPresent()) {
      erbf.append(getResourceNotFoundMessage("Dataverse config", appConfigSetId));
    }
  }

  // zipname syntax now validated
  @GetMapping("/ajax/downloadArchive/{zipname}")
  @ResponseBody
  public void downloadArchive(@PathVariable("zipname") String zipname, HttpServletResponse response)
      throws IOException {

    doDownload(zipname, response);
  }

  private void doDownload(String zipname, HttpServletResponse response) {
    exportManager.streamArchiveDownload(zipname, response);
  }

  /** These filenames are generated by RSpace, not by user,s so should always match */
  public static final Pattern VALID_PDF_FILE_CHARS = Pattern.compile("^[A-Za-z0-9_\\.\\-]+$");

  /**
   * Method to display PDF (get pdf from filestore) and display the PDF (pdfname).
   *
   * @param pdfname
   * @param displayName
   * @param principal
   * @param res
   * @throws URISyntaxException
   * @throws IOException
   * @throws IllegalArgumentException if <code>pdfname</code> doesn't match valid syntax
   */
  @GetMapping("/pdfs/{pdfname}")
  @ResponseBody
  public void displayPdf(
      @PathVariable("pdfname") String pdfname,
      @RequestParam(required = false, value = "displayName") String displayName,
      Principal principal,
      HttpServletResponse res)
      throws IOException, URISyntaxException {
    if (StringUtils.isEmpty(pdfname)) {
      throw new IllegalArgumentException(getText("errors.required", new String[] {"pdfName"}));
    }
    // we have to validate this as we might ending up looking up a file path from here
    Matcher m = VALID_PDF_FILE_CHARS.matcher(pdfname);
    if (!m.matches()) {
      throw new IllegalArgumentException(
          getText(
              "errors.invalidstringformat",
              new String[] {pdfname, "pdfName", VALID_PDF_FILE_CHARS.toString()}));
    }
    User user = getUserByUsername(principal.getName());
    responseUtil.setCacheTimeInBrowser(ResponseUtil.YEAR, null, res);
    if (displayName != null) {
      responseUtil.setContentDisposition(res, displayName);
    }
    pdfUtils.display(pdfname, user, res);
  }

  @PostMapping("/importArchive")
  public String importArchive(
      @RequestParam("zipfile") MultipartFile file,
      HttpSession session,
      RedirectAttributes ra,
      Principal principal) {

    ProgressMonitor progress =
        createProgressMonitor(
            RS_IMPORT_XML_ARCHIVE_PROGRESS, 100_000, "Importing XML archive", session);
    if (file == null || file.isEmpty()) {
      return returnToDashboardPageWithErrorMsg(
          ra, getText("importArchive.badformat.msg"), IMPORT_FORM_ERROR_ATTR_NAME);
    }
    if (!file.getOriginalFilename().endsWith("zip")
        && !file.getOriginalFilename().endsWith(".eln")) {
      return returnToDashboardPageWithErrorMsg(
          ra, getText("importArchive.badformat.msg"), IMPORT_FORM_ERROR_ATTR_NAME);
    }

    return doImport(
        () -> performMultipartFileImport(principal, progress, file),
        session,
        ra,
        progress,
        principal);
  }

  @PostMapping("/importOntology")
  public String importOntology(
      @RequestParam("csvfile") MultipartFile file,
      @RequestParam("dataColumn") int dataColumn,
      @RequestParam("urlColumn") int urlColumn,
      @RequestParam("ontologyName") String ontologyName,
      @RequestParam("ontologyVersion") String ontologyVersion,
      HttpSession session,
      RedirectAttributes ra) {

    ProgressMonitor progress =
        createProgressMonitor(
            RS_IMPORT_XML_ARCHIVE_PROGRESS, 100_000, "Importing Ontology File", session);
    if (file == null || file.isEmpty()) {
      return returnToDashboardPageWithErrorMsg(
          ra, PLEASE_UPLOAD_A_CSV_FILE, IMPORT_ONTOLOGY_ERROR_ATTR_NAME);
    }
    if (!file.getOriginalFilename().endsWith("csv")) {
      return returnToDashboardPageWithErrorMsg(
          ra, PLEASE_UPLOAD_A_CSV_FILE, IMPORT_ONTOLOGY_ERROR_ATTR_NAME);
    }

    return doOntologyImport(
        file, ra, progress, dataColumn, urlColumn, ontologyName, ontologyVersion);
  }

  @SneakyThrows
  private String doOntologyImport(
      MultipartFile multipartfile,
      RedirectAttributes model,
      ProgressMonitor progress,
      int dataColumn,
      int urlColumn,
      String ontologyName,
      String ontologyVersion) {
    try {
      ontologyImportManager.writeImportToOntologyDoc(
          multipartfile.getInputStream(), dataColumn, urlColumn, ontologyName, ontologyVersion);
      progress.setDescription("Import completed, redirecting");
      return "redirect:/workspace";
    } catch (ImportFailureException e) {
      return returnToDashboardPageWithErrorMsg(
          model, e.getMessage(), IMPORT_ONTOLOGY_ERROR_ATTR_NAME);
    } finally {
      progress.done();
    }
  }

  private String doImport(
      Supplier<ImportArchiveReport> importer,
      HttpSession session,
      RedirectAttributes model,
      ProgressMonitor progress,
      Principal principal) {
    try {
      ImportArchiveReport report = importer.get();
      progress.setDescription("Import completed, redirecting");
      session.setAttribute(SessionAttributeUtils.LATEST_IMPORT_REPORT, report);
      return "redirect:/import/archiveImportReport"; // redirect after post
    } catch (Exception e) {
      return returnToDashboardPageWithErrorMsg(
          model,
          getText("importArchive.failure.msg") + e.getMessage(),
          IMPORT_FORM_ERROR_ATTR_NAME);
    } finally {
      ontologyImportManager.updateImportedOntologiesWithCorrectForm(principal.getName());
      progress.done();
    }
  }

  /* Validates incoming server path. Must be a valid file path for a .zip file,
   *  omitting some shell characters.
   */
  @Data
  static class ServerPath {
    @NotNull
    @Size(max = 1024) // in case of 4 byte unicode chars; max path length is 4096 bytes on Linux
    @javax.validation.constraints.Pattern(regexp = "^(/[^;\\*\\?'\"\\./]{1,251})+\\.zip")
    String serverFilePath;
  }

  @PostMapping("/importServerArchive") // RSPAC-1684
  public String importArchive(
      @Valid ServerPath serverPath,
      BindingResult errors,
      HttpSession session,
      RedirectAttributes ra,
      Principal principal) {
    if (errors.hasErrors()) {
      return returnToDashboardPageWithErrorMsg(
          ra,
          "File "
              + serverPath
              + " is not a valid archive path on the server - must be a readable .zip file"
              + errors.getAllErrors().stream()
                  .map(ObjectError::getDefaultMessage)
                  .collect(Collectors.joining(",")),
          IMPORT_FORM_ERROR_ATTR_NAME);
    }

    File file = new File(serverPath.getServerFilePath());
    if (!file.exists() || !file.canRead()) {
      return returnToDashboardPageWithErrorMsg(
          ra,
          "File " + serverPath.getServerFilePath() + " either does not exist, or cannot be read.",
          IMPORT_FORM_ERROR_ATTR_NAME);
    }
    ProgressMonitor progress =
        createProgressMonitor(
            RS_IMPORT_XML_ARCHIVE_PROGRESS, 100_000, "Importing XML archive", session);
    return doImport(
        () -> performFileImport(principal, progress, file), session, ra, progress, principal);
  }

  @SneakyThrows // lets us throw checked exception from a lambda
  private ImportArchiveReport performFileImport(
      Principal principal, ProgressMonitor progress, File file) {
    return exportManager.importArchive(
        file, principal.getName(), new ArchivalImportConfig(), progress, importStrategy::doImport);
  }

  @SneakyThrows
  private ImportArchiveReport performMultipartFileImport(
      Principal principal, ProgressMonitor progress, MultipartFile multipartfile) {
    return exportManager.importArchive(
        multipartfile,
        principal.getName(),
        new ArchivalImportConfig(),
        progress,
        importStrategy::doImport);
  }

  @ResponseBody
  @GetMapping("/ajax/latestImportResults")
  public ImportArchiveReport getLatestImportReport(HttpSession session) {
    Object report = session.getAttribute(SessionAttributeUtils.LATEST_IMPORT_REPORT);
    if (report != null) {
      return (ImportArchiveReport) report;
    }
    return null;
  }

  private String returnToDashboardPageWithErrorMsg(
      RedirectAttributes ra, String msg, String flashAttr) {
    ra.addFlashAttribute(flashAttr, msg);
    log.warn(msg);
    return "redirect:/import/archiveImport";
  }

  private void handleUnexpectedRollbackException(Exception e) {
    if (e instanceof UnexpectedRollbackException) {
      UnexpectedRollbackException e1 = (UnexpectedRollbackException) e;
      log.warn(e1.getRootCause().getMessage());
      log.warn(e1.getMostSpecificCause().getMessage());
    } else {
      log.error(e.getMessage());
    }
  }

  /**
   * Return whether there is any missing or invalid data in exportSelection.
   *
   * @param exportSelection
   * @param errorBuffer
   * @return
   */
  private boolean checkForExportSelectionErrors(
      ExportSelection exportSelection, StringBuilder errorBuffer) {
    switch (exportSelection.getType()) {
      case SELECTION:
        if (exportSelection.getExportIds() == null) {
          errorBuffer.append(
              getText("errors.required.field", new String[] {"exportSelection.exportIds"}));
          return true;
        }
        if (exportSelection.getExportNames() == null) {
          errorBuffer.append(
              getText("errors.required.field", new String[] {"exportSelection.exportNames"}));
          return true;
        }
        if (exportSelection.getExportTypes() == null) {
          errorBuffer.append(
              getText("errors.required.field", new String[] {"exportSelection.exportTypes"}));
          return true;
        }
        if (exportSelection.getExportTypes().length > maxIdsToProcess
            || exportSelection.getExportIds().length > maxIdsToProcess
            || exportSelection.getExportNames().length > maxIdsToProcess) {
          errorBuffer.append(getText("errors.too.manyitems", new String[] {maxIdsToProcess + ""}));
          return true;
        }
        break;
      case USER:
        if (exportSelection.getUsername() == null || exportSelection.getUsername().isEmpty()) {
          errorBuffer.append(
              getText("errors.required.field", new String[] {"exportSelection.username"}));
          return true;
        }
        if (exportSelection.getUsername().length() > User.MAX_UNAME_LENGTH) {
          errorBuffer.append(
              getText(
                  "errors.maxlength",
                  new String[] {"userId", Integer.toString(User.MAX_UNAME_LENGTH)}));
          return true;
        }
        break;
      case GROUP:
        if (exportSelection.getGroupId() == null) {
          errorBuffer.append(
              getText("errors.required.field", new String[] {"exportSelection.group"}));
          return true;
        }
        break;
      default:
        return true;
    }
    return false;
  }

  /**
   * Exports a selection of documents to PDF or Word and optionally deposits the file in a
   * repository.
   *
   * @param exportConfig
   * @param errors
   * @param principal
   * @return
   */
  @PostMapping("/ajax/export")
  @ResponseBody
  public String export(
      @Valid @RequestBody ExportDialogConfigDTO exportConfig,
      BindingResult errors,
      Principal principal) {

    ExportSelection exportSelection = exportConfig.getExportSelection();
    ExportToFileConfig exportToFileConfig = exportConfig.getExportConfig();
    RepoDepositConfig repositoryConfig = exportConfig.getRepositoryConfig();

    // Checks for JSON binding errors, like fields that must not be null
    if (errors.hasErrors()) {
      ErrorList el = new ErrorList();
      inputValidator.populateErrorList(errors, el);
      return getText(
          "workspace.export.msgFailure",
          new String[] {"Export", el.getAllErrorMessagesAsStringsSeparatedBy(",")});
    }

    // Checks for any missing or invalid data in exportSelection
    StringBuilder errorBuffer = new StringBuilder();
    if (checkForExportSelectionErrors(exportSelection, errorBuffer)) {
      return getText(
          "workspace.export.msgFailure", new String[] {"Export", errorBuffer.toString()});
    }

    // RSPAC-900 only single files currently supported
    if (isMultiDocWordExport(exportSelection.getExportIds(), exportToFileConfig)) {
      return getText(
          "workspace.export.msgFailure",
          new String[] {"Export", getMultiDocExportErrorMsg(errorBuffer)});
    }

    updatePageSizePrefs(
        exportToFileConfig.getSetPageSizeAsDefault(), exportToFileConfig, principal);

    User exporter = getUserByUsername(principal.getName());

    try {
      Future<EcatDocumentFile> futureExportDocument = null;
      switch (exportSelection.getType()) {
        case SELECTION:
          futureExportDocument =
              exportManager.asynchExportFromSelection(
                  exportSelection.getExportIds(),
                  exportSelection.getExportNames(),
                  exportSelection.getExportTypes(),
                  exportToFileConfig,
                  exporter);
          exportManager.handlePossibleRollbackAsync(
              futureExportDocument, "some export name", exporter);
          break;
        case USER:
          if (isWordExport(exportToFileConfig)) {
            return getText(
                "workspace.export.msgFailure",
                new String[] {"Export", getMultiDocExportErrorMsg(errorBuffer)});
          }

          User userToExport = userManager.getUserByUsername(exportSelection.getUsername());

          if (!userToExport.getUsername().equals(exporter.getUsername())) {
            exportManager.assertExporterCanExportUsersWork(userToExport, exporter);
          }

          futureExportDocument =
              exportManager.exportPdfOfAllUserRecords(userToExport, exportToFileConfig, exporter);
          break;
        case GROUP:
          if (isWordExport(exportToFileConfig)) {
            return getText(
                "workspace.export.msgFailure",
                new String[] {"Export", getMultiDocExportErrorMsg(new StringBuilder())});
          }

          if (groupPermUtils.userCanExportGroup(
              exporter, groupManager.getGroup(exportSelection.getGroupId()))) {
            futureExportDocument =
                exportManager.exportGroupPdf(
                    exportToFileConfig, exporter, exportSelection.getGroupId());
          } else {
            return getText(
                "workspace.export.msgFailure",
                new String[] {
                  "Export", "only PIs or LabAdmins with view all permissions can export a group"
                });
          }
          break;
      }

      // Depositing to a repository if needed
      if (repositoryConfig.isDepositToRepository()) {
        RepoDepositPreDepositValidation va =
            validatePreDeposit(errorBuffer, repositoryConfig, exporter);
        if (!StringUtils.isBlank(va.getErrorMsg())) {
          return va.getErrorMsg();
        }
        depositHandler.sendDocumentToRepository(
            repositoryConfig, va.getOptionalAppConfig(), va.getApp(), futureExportDocument);
      }
    } catch (Exception e) {
      handleUnexpectedRollbackException(e);
      return getText("workspace.export.msgFailure", new String[] {"Export", e.getMessage()});
    }
    return getText("pdfArchiving.submission.successMsg");
  }

  @Data
  private static class RepoDepositPreDepositValidation {
    private String errorMsg;
    private App app;
    Optional<AppConfigElementSet> optionalAppConfig;

    RepoDepositPreDepositValidation(String errMsg) {
      this.errorMsg = errMsg;
    }

    RepoDepositPreDepositValidation() {}
  }

  private RepoDepositPreDepositValidation validatePreDeposit(
      StringBuilder errorBuffer, RepoDepositConfig repositoryConfig, User exporter) {
    Optional<AppConfigElementSet> optionalAppConfig =
        userAppConfigMgr.findByAppConfigElementSetId(repositoryConfig.getRepoCfg());
    validateCfgIfExists(repositoryConfig.getRepoCfg(), errorBuffer, optionalAppConfig);
    String msg = "";
    if (errorBuffer.length() > 0) {
      msg = getText("workspace.export.msgFailure", new String[] {"Export", errorBuffer.toString()});
      return new RepoDepositPreDepositValidation(msg);
    }

    App app = userAppConfigMgr.getByAppName(repositoryConfig.getAppName(), exporter).getApp();
    validateAppIsRepository(errorBuffer, app);
    if (errorBuffer.length() > 0) {
      msg = getText("workspace.export.msgFailure", new String[] {"Export", errorBuffer.toString()});
      return new RepoDepositPreDepositValidation(msg);
    }
    RepoDepositPreDepositValidation rc = new RepoDepositPreDepositValidation();
    rc.setApp(app);
    rc.setOptionalAppConfig(optionalAppConfig);
    return rc;
  }

  @PostMapping("/ajax/exportRecordTagsArchive")
  @ResponseBody
  public AjaxReturnObject<Set<String>> getExportArchiveRecordTags(
      @Valid @RequestBody ExportArchiveDialogConfigDTO exportDialogConfig, Principal principal) {
    ExportSelection exportSelection = exportDialogConfig.getExportSelection();
    ArchiveExportConfig exportCfg = exportDialogConfig.toArchiveExportConfig();
    User exporter = getUserByUsername(principal.getName());
    exportCfg.setExporter(exporter);
    ExportRecordList exportRecordList =
        exportManager.getExportRecordList(exportSelection, exportCfg);
    List<Long> ids =
        exportRecordList.getRecordsToExport().stream()
            .map(r -> r.getDbId())
            .collect(Collectors.toList());
    ids.addAll(
        exportRecordList.getFolderTree().stream()
            .map(af -> af.getId())
            .collect(Collectors.toList()));
    Set<String> allTags =
        new TreeSet<>(documentTagManager.getTagMetaDataForRecordIds(ids, exporter));
    return new AjaxReturnObject<>(allTags, null);
  }

  @PostMapping("/ajax/exportRecordTagsPdfsAndDocs")
  @ResponseBody
  public AjaxReturnObject<Set<String>> getExportDocsAndPdfsRecordTags(
      @Valid @RequestBody ExportDialogConfigDTO exportDialogConfig, Principal principal) {
    ExportSelection exportSelection = exportDialogConfig.getExportSelection();
    User exporter = getUserByUsername(principal.getName());
    List<Long> exportIDS;
    if (exportSelection.getType() == ExportSelection.ExportType.USER) {
      exportIDS =
          exportManager.getExportIDsForTagRetrievalForPDFExportFromRoot(
              exporter, exportDialogConfig.getExportConfig());
    } else {
      exportIDS =
          exportManager.getExportIDsForTagRetrievalForPDFExportFromFilesAndFolders(
              Arrays.asList(exportSelection.getExportIds()),
              exportDialogConfig.getExportConfig(),
              exporter);
    }
    Set<String> allTags =
        new TreeSet<>(documentTagManager.getTagMetaDataForRecordIds(exportIDS, exporter));
    return new AjaxReturnObject<>(allTags, null);
  }

  /**
   * Exports selected files / all user files to either XML or HTML archive and optionally deposits
   * in a repository.
   *
   * @param exportDialogConfig
   * @param errors
   * @param principal
   * @return A String 'Submitted' on success, or an error list
   * @throws IOException, URISyntaxException
   */
  @PostMapping("/ajax/exportArchive")
  @ResponseBody
  public String exportArchive(
      @Valid @RequestBody ExportArchiveDialogConfigDTO exportDialogConfig,
      BindingResult errors,
      HttpServletRequest request,
      Principal principal)
      throws IOException, URISyntaxException {

    ExportSelection exportSelection = exportDialogConfig.getExportSelection();

    // Checks for JSON binding errors, like fields that must not be null
    if (errors.hasErrors()) {
      ErrorList el = new ErrorList();
      inputValidator.populateErrorList(errors, el);
      return getText(
          "workspace.export.msgFailure",
          new String[] {"Export", el.getAllErrorMessagesAsStringsSeparatedBy(",")});
    }

    // Checks for any missing or invalid data in exportSelection
    StringBuilder errorBuffer = new StringBuilder();
    if (checkForExportSelectionErrors(exportSelection, errorBuffer)) {
      return getText(
          "workspace.export.msgFailure", new String[] {"Export", errorBuffer.toString()});
    }

    // Check enough disk space for an archive
    if (!diskSpaceChecker.canStartArchiveProcess()) {
      return getText("workspace.export.noDiskSpace");
    }

    try {
      URI baseUri = new URI(properties.getServerUrl());
      User exporter = getUserByUsername(principal.getName());
      ArchiveExportConfig exportCfg = exportDialogConfig.toArchiveExportConfig();

      if (exportCfg.isIncludeNfsLinks()) {
        exportCfg.setAvailableNfsClients(nfsController.retrieveNfsClientsMapFromSession(request));
      }

      Future<ArchiveResult> futureArchive = null;
      switch (exportSelection.getType()) {
        case SELECTION:
          futureArchive =
              exportManager.exportRecordSelection(
                  exportSelection, exportCfg, exporter, baseUri, standardPostExport);
          break;
        case USER:
          futureArchive =
              userExportHandler.doUserArchive(exportSelection, exportCfg, exporter, baseUri);
          break;
        case GROUP:
          Long groupId = exportSelection.getGroupId();
          if (groupPermUtils.userCanExportGroup(exporter, groupManager.getGroup(groupId))) {
            futureArchive =
                exportManager.exportAsyncGroup(
                    exportCfg, exporter, groupId, baseUri, standardPostExport);
          } else {
            return getText(
                "workspace.export.msgFailure",
                new String[] {
                  "Export", "only PIs or LabAdmins with view all permissions can export a group"
                });
          }
          break;
      }

      // Depositing to a repository if needed
      RepoDepositConfig repositoryConfig = exportDialogConfig.getRepositoryConfig();
      if (repositoryConfig.isDepositToRepository()) {
        RepoDepositPreDepositValidation va =
            validatePreDeposit(errorBuffer, repositoryConfig, exporter);
        if (!StringUtils.isBlank(va.getErrorMsg())) {
          return va.getErrorMsg();
        }
        depositHandler.sendArchiveToRepository(
            repositoryConfig, va.getOptionalAppConfig(), va.getApp(), futureArchive);
      }
    } catch (Exception e) {
      handleUnexpectedRollbackException(e);
      return getText("workspace.export.msgFailure", new String[] {"Export", e.getMessage()});
    }

    return getText("pdfArchiving.submission.successMsg");
  }

  private String getMultiDocExportErrorMsg(StringBuilder erbf) {
    erbf.append(
        getText("errors.unsupported", new String[] {"'Export to Word of multiple documents'"}));
    return erbf.toString();
  }

  private boolean isMultiDocWordExport(Long[] exportIds, ExportToFileConfig exportCfg) {
    return exportIds != null && isWordExport(exportCfg) && exportIds.length > 1;
  }

  private boolean isWordExport(ExportToFileConfig exportCfg) {
    return ExportFormat.WORD.equals(exportCfg.getExportFormat());
  }

  private void updatePageSizePrefs(
      Boolean setPageSizeAsDefault, ExportToFileConfig config, Principal principal) {
    if (setPageSizeAsDefault != null
        && setPageSizeAsDefault
        && Preference.UI_PDF_PAGE_SIZE.isValid(config.getPageSize())) {
      userManager.setPreference(
          Preference.UI_PDF_PAGE_SIZE, config.getPageSize(), principal.getName());
    }
  }

  @GetMapping("/ajax/defaultPDFConfig")
  @ResponseBody
  public AjaxReturnObject<ExportToFileConfig> getPdfConfig() {
    User subject = userManager.getAuthenticatedUserInSession();
    ExportToFileConfig info = configurer.getExportConfigWithDefaultPageSizeForUser(subject);
    return new AjaxReturnObject<ExportToFileConfig>(info, null);
  }

  @GetMapping("/ajax/getOtherExportDialogs")
  public ModelAndView getOtherExportDialogs() {
    return new ModelAndView("export/export_otherDialogs");
  }

  @GetMapping("/report/{id}")
  public String getExportReport(@PathVariable("id") Long notificationId, Model model) {
    User subject = userManager.getAuthenticatedUserInSession();

    Communication communication = commMgr.getIfOwnerOrTarget(notificationId, subject);
    if (!communication.isNotification()) {
      throw new IllegalArgumentException("not a notification id");
    }
    Notification notification = (Notification) communication;
    if (!NotificationType.ARCHIVE_EXPORT_COMPLETED.equals(notification.getNotificationType())) {
      throw new IllegalArgumentException("not an export notification id");
    }

    model.addAttribute("notification", notification);
    model.addAttribute("notificationData", notification.getNotificationDataObject());
    model.addAttribute("notificationDataJson", notification.getNotificationData());

    return "import/archiveExportReport";
  }

  /*
   * ==============
   *  for testing
   * ==============
   */

  protected void setUserAppConfigMgr(UserAppConfigManager userAppConfigMgr) {
    this.userAppConfigMgr = userAppConfigMgr;
  }

  protected void setDiskSpaceChecker(DiskSpaceChecker diskSpaceChecker) {
    this.diskSpaceChecker = diskSpaceChecker;
  }
}

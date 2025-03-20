package com.researchspace.webapp.controller;

import static com.researchspace.model.record.StructuredDocument.MAX_TAG_LENGTH;
import static com.researchspace.service.impl.DocumentTagManagerImpl.RSPACTAGS_FORSL__;
import static com.researchspace.service.impl.DocumentTagManagerImpl.allGroupsAllowBioOntologies;
import static com.researchspace.service.impl.DocumentTagManagerImpl.anyGroupEnforcesOntologies;
import static com.researchspace.session.SessionAttributeUtils.BATCH_WORDIMPORT_PROGRESS;
import static java.lang.String.format;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.lang.StringUtils.isBlank;

import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.core.util.progress.ProgressMonitorImpl;
import com.researchspace.document.importer.ExternalFileImporter;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EditStatus;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Signature;
import com.researchspace.model.SignatureInfo;
import com.researchspace.model.User;
import com.researchspace.model.Witness;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.DuplicateAuditEvent;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.RenameAuditEvent;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dto.UserPublicInfo;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Breadcrumb;
import com.researchspace.model.record.EditInfo;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.model.views.FolderRecordPair;
import com.researchspace.model.views.MessagedServiceOperationResult;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.model.views.SigningResult;
import com.researchspace.service.AuditManager;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.DocumentHTMLPreviewHandler;
import com.researchspace.service.DocumentTagManager;
import com.researchspace.service.EcatCommentManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.RecordSigningManager;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.impl.DocumentTagManagerImpl;
import com.researchspace.service.impl.RecordEditorTracker;
import com.researchspace.session.UserSessionTracker;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

/** Main controller for loading, displaying and handling edits for StructuredDocuments */
@Controller
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.NEVER)
@RequestMapping({
  "/workspace/editor/structuredDocument",
  "/public/publicView/workspace/editor/structuredDocument"
})
public class StructuredDocumentController extends BaseController {

  /** View name and redirect URLs */
  public static final String STRUCTURED_DOCUMENT_EDITOR_VIEW_NAME =
      "workspace/editor/structuredDocument";

  public static final String STRUCTURED_DOCUMENT_EDITOR_URL =
      "/" + STRUCTURED_DOCUMENT_EDITOR_VIEW_NAME;

  public static final String STRUCTURED_DOCUMENT_MS_TEAMS_SIMPLE_VIEW_NAME =
      "connect/msteams/msTeamsSimpleDocView";
  public static final String STRUCTURED_DOCUMENT_AUDIT_VIEW_URL =
      "/workspace/editor/structuredDocument/audit/view";

  private @Autowired RichTextUpdater updater;
  private @Autowired RecordDeletionManager deletionManager;
  private @Autowired RecordSigningManager signingManager;
  private @Autowired RecordEditorTracker tracker;
  private @Autowired DocumentHTMLPreviewHandler htmlGenerator;
  private @Autowired SystemPropertyPermissionManager systemPropertyMgr;
  private @Autowired SharingHandler recordShareHandler;

  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;
  @Autowired private DocumentTagManager documentTagManager;

  public void setdocumentTagManager(DocumentTagManager documentTagManager) {
    this.documentTagManager = documentTagManager;
  }

  private @Autowired MediaManager mediaManager;

  public void setMediaManager(MediaManager mediaManager) {
    this.mediaManager = mediaManager;
  }

  private @Autowired AuditManager auditMgr;

  public void setAuditManager(AuditManager auditMgr) {
    this.auditMgr = auditMgr;
  }

  private @Autowired FieldManager fieldManager;

  void setFieldManager(FieldManager fieldManager) {
    this.fieldManager = fieldManager;
  }

  private @Autowired EcatCommentManager commentManager;

  void setCommentManager(EcatCommentManager commentManager) {
    this.commentManager = commentManager;
  }

  private ExternalFileImporter externalWordFileImporter;

  @Autowired
  @Qualifier("externalWordFileImporter")
  void setExternalWordFileImporter(ExternalFileImporter externalFileImporter) {
    this.externalWordFileImporter = externalFileImporter;
  }

  @Qualifier("evernoteFileImporter")
  @Autowired
  private ExternalFileImporter evernoteFileImporter;

  private @Autowired WorkspaceHandler workspaceHandler;

  void setWorkspaceHandler(WorkspaceHandler workspaceHandler) {
    this.workspaceHandler = workspaceHandler;
  }

  @PostMapping("/ajax/createFromWord/{parentId}")
  @ResponseBody
  public AjaxReturnObject<List<RecordInformation>> createSDFromWordFile(
      @PathVariable("parentId") Long parentFolderId,
      @RequestParam("wordXfile") List<MultipartFile> mswordOrEvernoteFile,
      @RequestParam(value = "recordToReplaceId", required = false) Long recordToReplaceId,
      HttpSession session)
      throws IOException {

    User user = userManager.getAuthenticatedUserInSession();
    log.info("Creating RSpace docs from {} submitted files", mswordOrEvernoteFile.size());

    ErrorList el = new ErrorList();
    if (!isFileUploaded(mswordOrEvernoteFile)) {
      el.addErrorMsg(getText("workspace.word.import.nofiles.error.msg"));
      return new AjaxReturnObject<List<RecordInformation>>(null, el);
    }

    StructuredDocument toReplace = null;
    if (recordToReplaceId != null) {
      log.info("want to save into existing document: " + recordToReplaceId);

      Record record = recordManager.get(recordToReplaceId);
      if (record == null || !permissionUtils.isPermitted(record, PermissionType.WRITE, user)) {
        el.addErrorMsg(
            getText("error.authorization.failure.polite", new String[] {"overwrite document"}));
        return new AjaxReturnObject<List<RecordInformation>>(null, el);
      }
      if (record.isStructuredDocument()) {
        toReplace = (StructuredDocument) record;
        if (!toReplace.isBasicDocument()) {
          el.addErrorMsg(getText("workspace.create.fromMSWord.replace.error.notbasic"));
          return new AjaxReturnObject<List<RecordInformation>>(null, el);
        }
      } else {
        el.addErrorMsg(getText("errors.strucdoc.required", new String[] {recordToReplaceId + ""}));
        return new AjaxReturnObject<List<RecordInformation>>(null, el);
      }
      if (mswordOrEvernoteFile.size() > 1) {
        el.addErrorMsg(getText("workspace.create.fromMSWord.replace.error.1only"));
        return new AjaxReturnObject<List<RecordInformation>>(null, el);
      }
    }

    Folder parent = folderManager.getFolder(parentFolderId, user);
    assertAuthorisation(user, parent, PermissionType.READ);
    List<RecordInformation> rc = new ArrayList<>();
    ProgressMonitor progress =
        new ProgressMonitorImpl(mswordOrEvernoteFile.size() * 10, "File import progress");
    session.setAttribute(BATCH_WORDIMPORT_PROGRESS, progress);
    for (MultipartFile mf : mswordOrEvernoteFile) {
      try {
        BaseRecord createdOrUpdated = null;
        Optional<ExternalFileImporter> importer = getFileImporterForMultipartFile(mf);
        if (!importer.isPresent()) {
          log.warn("No importer for file type '{}' ", getExtension(mf.getOriginalFilename()));
          String error =
              String.format("No importer for file type %s", getExtension(mf.getOriginalFilename()));
          el.addErrorMsg(error);
          log.warn(error);
          continue;
        }
        if (recordToReplaceId == null) {
          createdOrUpdated =
              importer
                  .get()
                  .create(mf.getInputStream(), user, parent, null, mf.getOriginalFilename());
        } else {
          createdOrUpdated =
              importer
                  .get()
                  .replace(mf.getInputStream(), user, recordToReplaceId, mf.getOriginalFilename());
        }
        if (createdOrUpdated != null) {
          rc.add(createdOrUpdated.toRecordInfo());
          publisher.publishEvent(createGenericEvent(user, createdOrUpdated, AuditAction.CREATE));
        } else {
          String error =
              String.format("Could not create document from %s ", mf.getOriginalFilename());
          el.addErrorMsg(error);
          log.error(error);
        }
      } catch (Exception e) {
        String error =
            String.format(
                "Could not create document from %s - %s", mf.getOriginalFilename(), e.getMessage());
        el.addErrorMsg(error);
        log.error(error);
      }
      progress.worked(10);
      progress.setDescription(
          String.format(
              "Processed file '%s'. Import is  %d%% complete.",
              mf.getOriginalFilename(), (int) progress.getPercentComplete()));
    }
    progress.done();
    return new AjaxReturnObject<List<RecordInformation>>(rc, el);
  }

  private Optional<ExternalFileImporter> getFileImporterForMultipartFile(MultipartFile mf) {
    if (getExtension(mf.getOriginalFilename()).equalsIgnoreCase("enex")) {
      return Optional.of(evernoteFileImporter);
    } else if (isDoc(FilenameUtils.getExtension(mf.getOriginalFilename()))) {
      return Optional.of(externalWordFileImporter);
    }
    return Optional.empty();
  }

  private boolean isDoc(String extension) {
    return "doc".equalsIgnoreCase(extension)
        || "rtf".equalsIgnoreCase(extension)
        || "txt".equalsIgnoreCase(extension)
        || "docx".equalsIgnoreCase(extension)
        || "odt".equalsIgnoreCase(extension);
  }

  /**
   * Creates a new StructuredDocument from client request.
   *
   * @param parentRecordId The parent record to hold the new record. Currently this works just with
   *     folders.
   * @param formid Form ID from which to create a {@link StructuredDocument}.
   * @return A {@link ModelAndView} to the record editor
   * @throws RecordAccessDeniedException
   */
  @PostMapping("/create/{recordid}")
  public String createSD(
      @PathVariable("recordid") Long parentRecordId,
      @RequestParam(value = "template") long formid,
      Principal principal)
      throws RecordAccessDeniedException {

    User user = getUserByUsername(principal.getName());
    StructuredDocument newRecord = null;
    List<RecordGroupSharing> sharedWithGroup = null;
    try {
      newRecord = recordManager.createNewStructuredDocument(parentRecordId, formid, user, true);
      if (newRecord == null) {
        throw new RecordAccessDeniedException(getResourceNotFoundMessage("Form", formid));
      }
      Folder originalParentFolder = folderManager.getFolder(parentRecordId, user);
      if (originalParentFolder.isSharedFolder()) {
        // shareDocument into the current parentFolder
        ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> sharingResult =
            recordShareHandler.shareIntoSharedFolder(user, originalParentFolder, newRecord.getId());
        sharedWithGroup = sharingResult.getResults();
      }
    } catch (AuthorizationException ae) {
      throw new RecordAccessDeniedException(getResourceNotFoundMessage("Record", parentRecordId));
    }
    publisher.publishEvent(createGenericEvent(user, newRecord, AuditAction.CREATE));
    return redirectToDocumentEditorInEditMode(newRecord, sharedWithGroup);
  }

  @PostMapping("/createEntry/{notebookid}")
  public String createEntryAndRedirect(
      @PathVariable("notebookid") Long notebookId, Principal principal)
      throws RecordAccessDeniedException {
    StructuredDocument newEntry = createNamedEntry(notebookId, null, principal);
    return redirectToDocumentEditorInEditMode(newEntry);
  }

  @PostMapping("/ajax/createEntry")
  @ResponseBody
  public Long createEntry(
      @RequestParam(value = "notebookId") Long notebookId,
      @RequestParam(value = "entryName", required = false) String entryName,
      Principal principal)
      throws RecordAccessDeniedException {
    StructuredDocument newEntry = createNamedEntry(notebookId, entryName, principal);
    return newEntry.getId();
  }

  private StructuredDocument createNamedEntry(
      Long notebookId, String entryName, Principal principal) throws RecordAccessDeniedException {
    User user = userManager.getUserByUsername(principal.getName());
    Notebook notebook = folderManager.getNotebook(notebookId);
    if (StringUtils.isBlank(entryName)) {
      entryName = StructuredDocument.DEFAULT_NAME;
    }
    StructuredDocument newEntry = workspaceHandler.createEntry(notebook, entryName, user);
    publisher.publishEvent(createGenericEvent(user, newEntry, AuditAction.CREATE));
    return newEntry;
  }

  /**
   * Creates a new StructuredDocument from template.
   *
   * @param parentRecordId The parent record to hold the new record. Currently this works just with
   *     folders.
   * @param templateid Template ID from which to create the record.
   * @return A {@link ModelAndView} to the record editor
   * @throws RecordAccessDeniedException
   */
  @PostMapping("/createFromTemplate/{recordid}")
  public String createSDFromTemplate(
      @PathVariable("recordid") Long parentRecordId,
      @RequestParam(value = "template") long templateid,
      @RequestParam(value = "newname", required = false) String newname,
      Principal principal)
      throws RecordAccessDeniedException {
    User user = getUserByUsername(principal.getName());
    Long targetFolderId = parentRecordId;
    StructuredDocument rc = null;
    Folder originalParentFolder = null;
    List<RecordGroupSharing> sharedWithGroup = null;
    try {
      if (targetFolderId != null) {
        originalParentFolder = folderManager.getFolder(parentRecordId, user);
      }
      if ((targetFolderId == null || targetFolderId == -1)
          || (originalParentFolder != null && originalParentFolder.isSharedFolder())) {
        targetFolderId = folderManager.getRootFolderForUser(user).getId();
      }

      if (StringUtils.isBlank(newname) || newname.contains("/")) {
        newname = StructuredDocument.DEFAULT_NAME;
      }

      RecordCopyResult fromTemplate =
          recordManager.createFromTemplate(templateid, newname, user, targetFolderId);
      rc = (StructuredDocument) fromTemplate.getUniqueCopy();

      if (rc != null && (originalParentFolder != null && originalParentFolder.isSharedFolder())) {
        ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> sharingResult =
            recordShareHandler.shareIntoSharedFolder(user, originalParentFolder, rc.getId());
        sharedWithGroup = sharingResult.getResults();
      } else {
        throw new RecordAccessDeniedException(getResourceNotFoundMessage("Template", templateid));
      }

    } catch (AuthorizationException ae) {
      throw new RecordAccessDeniedException(getResourceNotFoundMessage("Record", null));
    }
    publisher.publishEvent(createGenericEvent(user, rc, AuditAction.CREATE));
    return redirectToDocumentEditorInEditMode(rc, sharedWithGroup);
  }

  private String redirectToDocumentEditorInEditMode(StructuredDocument newRecord) {
    return redirectToDocumentEditorInEditMode(newRecord, null);
  }

  private String redirectToDocumentEditorInEditMode(
      StructuredDocument newRecord, List<RecordGroupSharing> sharedWithGroup) {
    String redirectUrl = "redirect:" + STRUCTURED_DOCUMENT_EDITOR_URL + "/" + newRecord.getId();
    if (newRecord.isNotebookEntry()) {
      redirectUrl += "?fromNotebook=" + newRecord.getParentNotebook().getId();
    } else {
      redirectUrl += "?editMode=true";
    }
    if (!(sharedWithGroup == null || sharedWithGroup.isEmpty())) {
      redirectUrl +=
          "&sharedWithGroup="
              + URLEncoder.encode(
                  sharedWithGroup.get(0).getSharee().getDisplayName(), StandardCharsets.UTF_8);
    }
    return redirectUrl;
  }

  /**
   * Method called to rename a record using a new name.
   *
   * @param recordId
   * @param newname
   * @param principal
   * @return AjaxReturnObject<String>
   */
  @PostMapping("/ajax/rename")
  @ResponseBody
  public AjaxReturnObject<String> rename(
      @RequestParam("recordId") long recordId,
      @RequestParam("newName") String newname,
      Principal principal) {

    String validationError = validateNewRecordName(newname);
    if (validationError != null) {
      return new AjaxReturnObject<String>(null, ErrorList.of(validationError));
    }

    User subject = getUserByUsername(principal.getName());
    boolean recordRenamed = false;
    BaseRecord record = baseRecordManager.get(recordId, subject);
    String oldName = record.getName();
    try {
      recordRenamed = recordManager.renameRecord(newname, recordId, subject);
    } catch (AuthorizationException ae) {
      return new AjaxReturnObject<String>(
          null,
          getErrorListFromMessageCode("error.authorization.failure.polite", "rename this record."));
    }
    if (recordRenamed) {
      auditService.notify(new RenameAuditEvent(subject, record, oldName, newname));
    } else {
      return new AjaxReturnObject<String>(
          null, getErrorListFromMessageCode("rename.failed.msg", new Object[] {}));
    }
    return new AjaxReturnObject<String>("Success", null);
  }

  /**
   * @param newName name to validate
   * @return error message, or null if no error
   */
  public String validateNewRecordName(String newName) {
    String message = null;
    if (StringUtils.isBlank(newName)) {
      message = getText("errors.required", "Name");
    } else if (newName.contains("/")) {
      message = getText("errors.invalidchars", new Object[] {"/", "name"});
    }
    return message;
  }

  /**
   * Method called on the Media Gallery to set the description to different elements (Images,
   * Videos, Audios, Documents, Templates).
   *
   * @param recordId
   * @param desc
   * @param principal
   * @return AjaxReturnObject<String>
   */
  @ResponseBody
  @PostMapping("/ajax/description")
  public AjaxReturnObject<Boolean> setDocumentDescription(
      @RequestParam("recordId") long recordId,
      @RequestParam("description") String desc,
      Principal principal) {

    if (StringUtils.length(desc) > EditInfo.DESCRIPTION_LENGTH) {
      throw new IllegalArgumentException("description too long, should be max 250 chars");
    }
    User u = getUserByUsername(principal.getName());
    BaseRecord recordOrFolder = baseRecordManager.get(recordId, u);

    if (!permissionUtils.isPermitted(recordOrFolder, PermissionType.WRITE, u)) {
      throw new AuthorizationException(
          getText("error.authorization.failure.polite", new String[] {"edit description"}));
    }
    recordOrFolder.setDescription(desc);
    baseRecordManager.save(recordOrFolder, u);
    return new AjaxReturnObject<>(true, null);
  }

  @GetMapping("/ajax/preview/{recordId}")
  public @ResponseBody String openDocumentPreview(@PathVariable("recordId") long recordId)
      throws RecordAccessDeniedException {
    User user = userManager.getAuthenticatedUserInSession();
    return htmlGenerator.generateHtmlPreview(recordId, user).getHtmlContent();
  }

  /**
   * Opens document for viewing or editing
   *
   * @param recordId
   * @param editMode boolean variable to check if the basic document should be open in edit mode.
   * @param model
   * @param principal
   * @return
   * @throws RecordAccessDeniedException
   */
  @GetMapping("/{recordId}")
  public ModelAndView openDocument(
      @PathVariable("recordId") long recordId,
      @RequestParam(value = "settingsKey", required = false) String settingsKey,
      @RequestParam(value = "editMode", required = false, defaultValue = "false") boolean editMode,
      @RequestParam(value = "msTeamsDocView", required = false, defaultValue = "false")
          boolean msTeamsDocView,
      @RequestParam(value = "fromNotebook", required = false) Long fromNotebook,
      Model model,
      HttpSession session,
      Principal principal)
      throws RecordAccessDeniedException {

    User user = userManager.getUserByUsername(principal.getName(), true);
    DocumentEditContext docEditContext = getDocEditContext(recordId, user);
    StructuredDocument structuredDocument = docEditContext.getStructuredDocument();

    // notebook entries should be opened in notebook view (RSPAC-501)
    if (structuredDocument.isNotebookEntry() && fromNotebook == null && !msTeamsDocView) {
      boolean redirectToNotebook = false;
      if (user.equals(structuredDocument.getOwner())) {
        // if you're an owner, and the doc is part of your notebook
        redirectToNotebook = structuredDocument.getNonSharedParentNotebook() != null;
      } else {
        // user is not an owner, but one of the parent notebook was shared with him
        redirectToNotebook = canUserAccessAnyOfParentNotebooks(user, structuredDocument);
      }
      if (redirectToNotebook) {
        return new ModelAndView(redirectToNotebookView(structuredDocument, settingsKey));
      }
    }
    EditStatus res = docEditContext.getEditStatus();
    // Opening Basic Documents in "Edit Mode" when editMode flag is true or coming
    // from notebook view
    if (structuredDocument.isBasicDocument()
        && (editMode || fromNotebook != null)
        && (EditStatus.VIEW_MODE.equals(res))) {
      res = recordManager.requestRecordEdit(recordId, user, docEditContext.getUserTracker());
    }

    prepareDocumentForView(model, structuredDocument);
    addBreadcrumbsToView(structuredDocument, user, model, settingsKey, session);

    SignatureInfo signatureInfo = null;
    boolean canWitness = false;
    if (structuredDocument.isSigned()) {
      signatureInfo = signingManager.getSignatureForRecord(recordId).toSignatureInfo();
      canWitness = signingManager.canWitness(recordId, user);
    }

    model.addAttribute("structuredDocument", structuredDocument);
    if (structuredDocument.getDocTag() != null) {
      structuredDocument.setDocTag(
          structuredDocument.getDocTag().replaceAll(RSPACTAGS_FORSL__, "/"));
    }
    if (structuredDocument.getTagMetaData() != null) {
      structuredDocument.setTagMetaData(
          structuredDocument.getTagMetaData().replaceAll(RSPACTAGS_FORSL__, "/"));
    }
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(user, "public_sharing"));
    model.addAttribute("editStatus", res);
    if (EditStatus.CANNOT_EDIT_OTHER_EDITING.equals(res)) {
      model.addAttribute("editor", tracker.getEditingUserForRecord(recordId));
    }
    model.addAttribute("id", recordId);
    model.addAttribute("isPublished", structuredDocument.isPublished());
    model.addAttribute("template", structuredDocument.getForm().getName());
    model.addAttribute("signatureInfo", signatureInfo);
    model.addAttribute("canWitness", canWitness);
    model.addAttribute("fromNotebook", fromNotebook);
    model.addAttribute("user", user);
    if (isValidSettingsKey(settingsKey)) {
      model.addAttribute("settingsKey", settingsKey);
    }
    model.addAttribute("extMessaging", getExternalMessagingIntegrationInfos(user));
    addGroupAttributes(model, user);
    model.addAttribute(
        "clientUISettingsPref", getUserPreferenceValue(user, Preference.UI_CLIENT_SETTINGS));
    auditService.notify(new GenericEvent(user, structuredDocument, AuditAction.READ));

    boolean inventoryEnabled = systemPropertyMgr.isPropertyAllowed(user, "inventory.available");
    model.addAttribute("dmpEnabled", isDMPEnabled(user));
    model.addAttribute("inventoryAvailable", inventoryEnabled);
    model.addAttribute("enforce_ontologies", anyGroupEnforcesOntologies(user));
    model.addAttribute("allow_bioOntologies", allGroupsAllowBioOntologies(user));

    boolean isGoogleDriveAppEnabled =
        integrationsHandler.getIntegration(user, "GOOGLEDRIVE").isEnabled();
    model.addAttribute("isGoogleDriveAppEnabled", isGoogleDriveAppEnabled);
    String view =
        msTeamsDocView
            ? STRUCTURED_DOCUMENT_MS_TEAMS_SIMPLE_VIEW_NAME
            : STRUCTURED_DOCUMENT_EDITOR_VIEW_NAME;
    return new ModelAndView(view, model.asMap());
  }

  @Getter
  @AllArgsConstructor
  static class DocumentEditContext {

    private EditStatus editStatus;
    private UserSessionTracker userTracker;
    private StructuredDocument structuredDocument;
  }

  private DocumentEditContext getDocEditContext(long recordId, User user)
      throws RecordAccessDeniedException {
    UserSessionTracker users = getCurrentActiveUsers();
    EditStatus res = recordManager.requestRecordView(recordId, user, users);
    assertViewPermission(recordId, res);
    // now we can load full record, after access check
    StructuredDocument structuredDocument =
        recordManager.getRecordWithFields(recordId, user).asStrucDoc();
    return new DocumentEditContext(res, users, structuredDocument);
  }

  private void assertViewPermission(long recordId, EditStatus res)
      throws RecordAccessDeniedException {
    if (EditStatus.ACCESS_DENIED.equals(res)) {
      throw new RecordAccessDeniedException(getResourceNotFoundMessage("Record", recordId));
    }
  }

  private void addBreadcrumbsToView(
      StructuredDocument document,
      User user,
      Model model,
      String settingsKey,
      HttpSession session) {

    // if we have a settings key, then we use it to show better breadcrumbs
    WorkspaceListingConfig cfg = null;
    Folder parent = null;
    if (isValidSettingsKey(settingsKey)
        && (cfg = (WorkspaceListingConfig) session.getAttribute(settingsKey)) != null) {
      Long parentId = cfg.getParentFolderId();
      parent = folderManager.getFolder(parentId, user);
    }
    Folder rootRecord = folderManager.getRootFolderForUser(user);
    createBreadcrumb(model, document, rootRecord, parent);
  }

  private boolean canUserAccessAnyOfParentNotebooks(User user, StructuredDocument doc) {
    for (Notebook nb : doc.getParentNotebooks()) {
      if (permissionUtils.isPermitted(nb, PermissionType.READ, user)) {
        return true;
      }
    }
    return false;
  }

  private void addGroupAttributes(Model model, User usr) {
    Set<Group> groups = groupManager.listGroupsForUser();
    model.addAttribute("groups", groups);
    List<User> users = Group.getUniqueUsersInGroups(groups, User.LAST_NAME_COMPARATOR, usr);
    model.addAttribute("uniqueUsers", users);
  }

  private void prepareDocumentForView(Model model, StructuredDocument doc) {
    Record tempRecord = doc == null ? null : doc.getTempRecord();
    boolean hasAutosave = tempRecord != null;
    model.addAttribute("hasAutosave", hasAutosave);
    model.addAttribute(
        "modificationDate",
        hasAutosave ? tempRecord.getModificationDate() : doc.getModificationDate());
  }

  private Breadcrumb createBreadcrumb(
      Model model, StructuredDocument document, Folder rootRecord, Folder via) {
    Breadcrumb breadcrumb = breadcrumbGenerator.generateBreadcrumbToHome(document, rootRecord, via);
    if (breadcrumb.isContainLinks()) {
      model.addAttribute("bcrumb", breadcrumb);
      model.addAttribute("parentId", breadcrumb.getParentFolderId());
    }
    return breadcrumb;
  }

  protected String redirectToNotebookView(StructuredDocument notebookEntry, String settingsKey) {
    return "redirect:"
        + NotebookEditorController.getNotebookViewUrl(
            notebookEntry.getParentNotebook().getId(), notebookEntry.getId(), settingsKey);
  }

  /**
   * @param recordId
   * @param principal
   * @return EditStatus
   */
  @PostMapping("ajax/requestEdit")
  @ResponseBody
  public EditStatus requestEdit(@RequestParam("recordId") long recordId, Principal principal) {
    UserSessionTracker activeUsers = getCurrentActiveUsers();
    User user = getUserByUsername(principal.getName());
    return recordManager.requestRecordEdit(recordId, user, activeUsers);
  }

  /**
   * Returns copied content of temporary fields. The fields are disconnected.
   *
   * @param recordId
   * @return List<Field>
   */
  @GetMapping("/getAutoSavedFields")
  @ResponseBody
  public List<Field> getAutoSavedFields(@RequestParam("recordId") long recordId) {
    List<Field> fieldList = fieldManager.getFieldsByRecordId(recordId, null);
    List<Field> tempFieldList = Field.getNewListOfTempFields(fieldList, false);
    for (Field field : tempFieldList) {
      disconnectField(field);
    }
    return tempFieldList;
  }

  private void disconnectField(Field field) {
    field.setStructuredDocument(null);
    field.getFieldForm().setForm(null);
    field.getFieldForm().setTempFieldForm(null); // RSPAC-1656
    if (field.getTempField() != null) {
      disconnectField(field.getTempField());
    }
  }

  /**
   * Determines if someone other than the subject is editing the record.
   *
   * @param recordId The record ID
   * @param principal
   * @return AjaxReturnObject<PublicUserInfo>. The payload of the return object may be <code>null
   * </code> if either:
   *     <ol>
   *       <li>Noone is currently editing, or
   *       <li>The subject is the current editor.
   *     </ol>
   */
  @IgnoreInLoggingInterceptor(ignoreAll = true)
  @GetMapping("/ajax/otherUserEditingRecord")
  @ResponseBody
  public AjaxReturnObject<UserPublicInfo> getOtherUserEditingRecord(
      @RequestParam("recordId") long recordId, Principal principal) {
    String currEditor = tracker.getEditingUserForRecord(recordId);
    if (currEditor != null && !currEditor.equals(principal.getName())) {
      User rc = userManager.getUserByUsername(currEditor);
      return new AjaxReturnObject<UserPublicInfo>(rc.toPublicInfo(), null);
    }
    return new AjaxReturnObject<UserPublicInfo>(null, null);
  }

  /**
   * Get a list of updated fiels.
   *
   * @param docId
   * @param modificationDate
   * @param principal
   * @return {@link AjaxReturnObject} containing fields.
   */
  @ResponseBody
  @GetMapping("/ajax/getUpdatedFields")
  public AjaxReturnObject<List<Field>> getUpdatedFields(
      @RequestParam("recordId") long docId,
      @RequestParam("modificationDate") long modificationDate,
      Principal principal) {

    User user = getUserByUsername(principal.getName());
    Long modDateStructuredDocument = recordManager.getModificationDate(docId, user);
    List<Field> result = new ArrayList<Field>();
    if (modDateStructuredDocument > modificationDate) {

      List<Field> fields = fieldManager.getFieldsByRecordId(docId, user);
      for (Field field : fields) {
        field.setStructuredDocument(null);
        field.setTempField(null);
        // setting field form to null doesn't work, it is ignored in setter.
        field.getFieldForm().setForm(null);
        field.getFieldForm().setTempFieldForm(null);
        result.add(field);
      }
    }
    return new AjaxReturnObject<List<Field>>(result, null);
  }

  /**
   * @param recordId
   * @return true
   */
  @ResponseBody
  @PostMapping("/ajax/unlockrecord")
  public AjaxReturnObject<Boolean> unlockRecord(
      @RequestParam("id") long recordId, Principal principal) {
    recordManager.unlockRecord(recordId, principal.getName());
    return new AjaxReturnObject<Boolean>(true, null);
  }

  /**
   * Returns an Ajax Page object. This method is used by "Save and Clone" button in structured
   * document page.
   *
   * @param structuredDocumentId
   * @param principal
   * @return AjaxReturnObject<String> url of the copy the document.
   * @throws DocumentAlreadyEditedException
   */
  @ResponseBody
  @PostMapping("ajax/saveCopyStructuredDocument")
  public AjaxReturnObject<String> saveCopyStructuredDocument(
      @RequestParam("structuredDocumentId") long structuredDocumentId,
      @RequestParam("recordName") String recordName,
      Principal principal)
      throws DocumentAlreadyEditedException {

    String uname = principal.getName();
    User user = getUserByUsername(uname);
    String newName = recordName + "-copy";

    BaseRecord oldRecord = baseRecordManager.get(structuredDocumentId, user);
    FolderRecordPair folderRecordPair =
        recordManager.saveStructuredDocument(structuredDocumentId, uname, true, null);
    auditService.notify(new GenericEvent(user, folderRecordPair.getRecord(), AuditAction.WRITE));
    RecordCopyResult copyResult = recordManager.copy(structuredDocumentId, newName, user, null);
    Record copy = (Record) copyResult.getUniqueCopy();
    auditService.notify(
        new DuplicateAuditEvent(
            user, copyResult.getOriginalToCopy(), oldRecord, oldRecord.getName(), newName));
    String urlToReturn = getDocumentEditorUrlForRecord(copy);
    return new AjaxReturnObject<String>(urlToReturn, null);
  }

  private String getDocumentEditorUrlForRecord(Record record) {
    String url = null;
    if (record != null) {
      url = STRUCTURED_DOCUMENT_EDITOR_URL + "/" + record.getId();
      if (record.isNotebookEntry()) {
        url += "?fromNotebook=" + ((StructuredDocument) record).getParentNotebook().getId();
      }
    }
    return url;
  }

  /**
   * Returns an Ajax Page object. This method is used by "Save and New" button in structured
   * document.
   *
   * @param structuredDocumentId
   * @param principal
   * @return AjaxReturnObject<String> url
   * @throws DocumentAlreadyEditedException
   */
  @ResponseBody
  @PostMapping("ajax/saveNewStructuredDocument")
  public AjaxReturnObject<String> saveNewStructuredDocument(
      @RequestParam("structuredDocumentId") long structuredDocumentId, Principal principal)
      throws DocumentAlreadyEditedException {

    String uname = principal.getName();
    User user = getUserByUsername(uname);
    FolderRecordPair folderRecordPair =
        recordManager.saveStructuredDocument(structuredDocumentId, uname, true, null);
    StructuredDocument sd = (StructuredDocument) folderRecordPair.getRecord();

    publisher.publishEvent(createGenericEvent(user, sd, AuditAction.WRITE));
    Record newInstanceRecord =
        recordManager.createNewStructuredDocument(
            sd.getParent().getId(), sd.getForm().getId(), user);
    publisher.publishEvent(createGenericEvent(user, newInstanceRecord, AuditAction.CREATE));
    if (newInstanceRecord == null) {
      ErrorList el = ErrorList.of("Could not create new document");
      return new AjaxReturnObject<String>(null, el);
    }
    String urlToReturn = getDocumentEditorUrlForRecord(newInstanceRecord);
    return new AjaxReturnObject<String>(urlToReturn, null);
  }

  /**
   * Cancels autosave edits by deleting temporary fields and data. Doesn't unlock the record.
   *
   * @param structuredDocumentId
   * @param principal
   * @return AjaxReturnObject<String> url
   * @throws DocumentAlreadyEditedException
   */
  @ResponseBody
  @PostMapping("ajax/cancelAutosavedEdits")
  public AjaxReturnObject<String> cancelAutosavedEdits(
      @RequestParam("structuredDocumentId") long structuredDocumentId, Principal principal)
      throws DocumentAlreadyEditedException {

    recordManager.cancelStructuredDocumentAutosavedEdits(structuredDocumentId, principal.getName());
    String urlToReturn = "/workspace/editor/structuredDocument/" + structuredDocumentId;
    return new AjaxReturnObject<String>(urlToReturn, null);
  }

  /**
   * Returns an Ajax Page object with the document's parent's ID to load the folder.
   *
   * @param structuredDocumentId
   * @return AjaxReturnObject<String> url
   * @throws DocumentAlreadyEditedException
   */
  @ResponseBody
  @PostMapping("ajax/saveStructuredDocument")
  public AjaxReturnObject<String> saveStructuredDocument(
      @RequestParam("structuredDocumentId") long structuredDocumentId,
      @RequestParam(value = "settingsKey", required = false) String settingsKey,
      @RequestParam("unlock") boolean unlock,
      Principal principal,
      HttpSession session)
      throws DocumentAlreadyEditedException {

    User subject = userManager.getAuthenticatedUserInSession();
    ErrorList warningsList = new ErrorList();
    FolderRecordPair fldRecPair =
        recordManager.saveStructuredDocument(
            structuredDocumentId, principal.getName(), unlock, warningsList);
    publisher.publishEvent(createGenericEvent(subject, fldRecPair.getRecord(), AuditAction.WRITE));

    Folder parent = fldRecPair.getParent();
    String urlToReturn = "";
    if (parent.isNotebook()) {
      urlToReturn =
          NotebookEditorController.getNotebookViewUrl(
              parent.getId(), fldRecPair.getRecord().getId(), settingsKey);
    } else if (parent.isFolder()) {
      WorkspaceListingConfig cfg = null;
      if (isValidSettingsKey(settingsKey)
          && (cfg = (WorkspaceListingConfig) session.getAttribute(settingsKey)) != null) {
        Long parentId = cfg.getParentFolderId();
        parent = folderManager.getFolder(parentId, subject);
      }
      urlToReturn = "/workspace/" + parent.getId();
      if (isValidSettingsKey(settingsKey)) {
        urlToReturn = urlToReturn + "?settingsKey=" + settingsKey;
      }
    }

    if (parent.hasType(RecordType.TEMPLATE)) {
      urlToReturn = "/workspace";
    }
    return new AjaxReturnObject<String>(urlToReturn, warningsList);
  }

  /**
   * Returns an Ajax Page object with the document's parent's ID to load the folder.
   *
   * @param recordId
   * @return AjaxReturnObject<String> url
   * @throws DocumentAlreadyEditedException
   * @throws RecordAccessDeniedException
   */
  @ResponseBody
  @PostMapping("/ajax/deleteStructuredDocument/{recordid}")
  public AjaxReturnObject<String> deleteStructuredDocument(
      @PathVariable("recordid") Long recordId, Principal principal)
      throws DocumentAlreadyEditedException {

    User subject = userManager.getUserByUsername(principal.getName());
    Folder parent = recordManager.getParentFolderOfRecordOwner(recordId, subject);

    // subject is not owner; only owner can delete from here.
    if (parent == null) {
      throw new IllegalStateException(getText("document.deletebyuseronly.msg"));
    }
    Long parentid = parent.getId();

    UserSessionTracker users = getCurrentActiveUsers();
    EditStatus es = recordManager.requestRecordEdit(recordId, subject, users);
    if (!EditStatus.ACCESS_DENIED.equals(es) && !EditStatus.CANNOT_EDIT_OTHER_EDITING.equals(es)) {
      try {
        CompositeRecordOperationResult result =
            deletionManager.deleteRecord(parentid, recordId, subject);
        if (result != null) {
          auditService.notify(new GenericEvent(subject, result, AuditAction.DELETE));
        } else {
          log.warn("Delete document returned null, not auditing");
        }
      } finally {
        recordManager.unlockRecord(recordId, subject.getUsername());
      }
    } else {
      log.warn("Couldn't delete document .. edit status is {}" + es);
      if (EditStatus.ACCESS_DENIED.equals(es)) {
        return new AjaxReturnObject<String>(
            null,
            getErrorListFromMessageCode(
                "error.authorization.failure.polite", new Object[] {" delete this document."}));
      }
      String editor = tracker.getEditingUserForRecord(recordId);
      return new AjaxReturnObject<String>(
          null,
          getErrorListFromMessageCode(
              "document.delete.failure.msg", new Object[] {recordId, editor}));
    }
    String urlToReturn = "/workspace/" + parentid;
    return new AjaxReturnObject<String>(urlToReturn, null);
  }

  /**
   * Saves data into a temporary field, or returns an error list if data can't be validated,
   *
   * @param data
   * @param fieldId
   * @param principal
   * @return AjaxReturnObject<Boolean> true or error list
   */
  @ResponseBody
  @PostMapping("ajax/autosaveField")
  public AjaxReturnObject<Boolean> autosaveField(
      @RequestParam("dataValue") String data,
      @RequestParam("fieldId") long fieldId,
      Principal principal) {
    User user = getUserByUsername(principal.getName());

    Field field = fieldManager.get(fieldId, user).get();
    ErrorList el = field.getFieldForm().validate(data);
    if (el.hasErrorMessages()) {
      return new AjaxReturnObject<Boolean>(null, el);
    }
    // we have to pass field ID here, and not pass existing field into a new
    // transaction
    recordManager.saveTemporaryDocument(field.getId(), user, data);
    log.info("Field {}  was autosaved", fieldId);
    log.debug(" with data {}", data);
    return new AjaxReturnObject<Boolean>(Boolean.TRUE, null);
  }

  /**
   * Called to add a comment to another comment.
   *
   * @param fieldId
   * @param commentId
   * @param comment
   * @return AjaxReturnObject<Boolean> true or error msg if comment too long
   */
  @PostMapping("/addComment")
  @ResponseBody
  public AjaxReturnObject<Boolean> addComment(
      @RequestParam("fieldId") String fieldId,
      @RequestParam("commentId") String commentId,
      @RequestParam("comment") String comment) {

    ErrorList el = validateComment(comment);
    if (el != null) {
      return new AjaxReturnObject<Boolean>(null, el);
    }
    User user = userManager.getAuthenticatedUserInSession();
    mediaManager.addEcatComment(fieldId, commentId, comment, user);
    return new AjaxReturnObject<Boolean>(true, null);
  }

  /**
   * Called to add a new comment
   *
   * @param fieldId
   * @param comment max length 1000 chars
   * @return AjaxReturnObject<Long>
   */
  @PostMapping("/insertComment")
  @ResponseBody
  public AjaxReturnObject<Long> insertComment(
      @RequestParam("fieldId") String fieldId, @RequestParam("comment") String comment) {

    ErrorList el = validateComment(comment);
    if (el != null) {
      return new AjaxReturnObject<Long>(null, el);
    }
    User user = userManager.getAuthenticatedUserInSession();
    EcatComment ecatComment = mediaManager.insertEcatComment(fieldId, comment, user);
    return new AjaxReturnObject<Long>(ecatComment.getComId(), null);
  }

  private ErrorList validateComment(String comment) {
    if (StringUtils.isEmpty(comment)) {
      return getErrorListFromMessageCode("errors.emptyString.polite", "your comment");
    } else if (!EcatComment.validateLength(comment)) {
      return getErrorListFromMessageCode(
          "errors.maxlength", "Comment", EcatComment.MAX_COMMENT_LENGTH);
    }
    return null;
  }

  /**
   * Called to get a list of commentItems from a comment
   *
   * @param principal
   * @param commentId
   * @param revision revision number if we're looking at an old revision.
   * @return List<EcatCommentItem>
   */
  @SuppressWarnings("unchecked")
  @GetMapping("/getComments")
  @ResponseBody
  public List<EcatCommentItem> getComments(
      @RequestParam("commentId") Long commentId,
      @RequestParam(value = "revision", required = false) Integer revision,
      Principal principal) {

    User subject = getUserByUsername(principal.getName());
    List<EcatCommentItem> commentsItems = null;

    if (revision == null) {
      commentsItems = commentManager.getCommentItems(commentId);
    } else {
      commentsItems = auditMgr.getCommentItemsForCommentAtDocumentRevision(commentId, revision);
    }

    assertCommentReadPermission(subject, commentsItems);
    // probably to prevent endless cycles in json conversion //
    for (EcatCommentItem item : commentsItems) {
      item.setEcatComment(null);
    }
    return commentsItems;
  }

  private void assertCommentReadPermission(User subject, List<EcatCommentItem> commentsItems) {
    // we just need to check read permission of parent comment once
    // can read comment if can read parent record
    if (!commentsItems.isEmpty()) {
      EcatComment comm = commentsItems.get(0).getEcatComment();
      assertAccessToRecord(
          recordAdapter.getAsBaseRecord(comm).get().getId(), PermissionType.READ, subject);
    }
  }

  /**
   * Views a specific version of a document for read-only display.
   *
   * @param recordId
   * @param revision
   * @param settingsKey (optional)
   * @param model
   * @param principal
   * @param session
   * @return
   */
  @GetMapping(
      value = "/audit/view",
      params = {"recordId", "revision"})
  public ModelAndView getDocumentRevision(
      @RequestParam("recordId") long recordId,
      @RequestParam("revision") Integer revision,
      @RequestParam(value = "settingsKey", required = false) String settingsKey,
      Model model,
      Principal principal,
      HttpSession session) {

    User user = getUserByUsername(principal.getName());
    Record record = assertAccessToRecord(recordId, PermissionType.READ, user);
    if (!record.isStructuredDocument()) {
      throw new IllegalStateException(
          "Viewing record's audit history only works with StructuredDocuments!");
    }

    StructuredDocument currentDoc = (StructuredDocument) record;
    AuditedRecord auditedRecord = null;
    if (!currentDoc.isDeleted()) {
      auditedRecord = auditMgr.getDocumentRevisionOrVersion(currentDoc, revision, null);
    } else {
      auditedRecord = auditMgr.restoredDeletedForView(recordId);
    }
    StructuredDocument auditedDoc = auditedRecord.getRecordAsDocument();
    // PRT-385 Set temp record to null here to prevent errors when accessing temp records that dont
    // exist.
    if (auditedDoc.getTempRecord() != null) {
      auditedDoc.setTempRecord(null);
    }
    updater.updateLinksWithRevisions(auditedDoc, revision);
    prepareDocumentForView(model, auditedDoc);
    if (!currentDoc.isDeleted()) {
      addBreadcrumbsToView(currentDoc, user, model, settingsKey, session);
    }

    SignatureInfo signatureInfo = null;
    if (auditedDoc.isSigned()) {
      signatureInfo = signingManager.getSignatureForRecord(recordId).toSignatureInfo();
    }

    model.addAttribute("structuredDocument", auditedDoc);
    model.addAttribute("editStatus", EditStatus.CAN_NEVER_EDIT);
    model.addAttribute("signatureInfo", signatureInfo);
    model.addAttribute("id", auditedDoc.getId());
    model.addAttribute("docRevision", revision);
    model.addAttribute("isDeleted", currentDoc.isDeleted());
    model.addAttribute("user", user);
    if (isValidSettingsKey(settingsKey)) {
      model.addAttribute("settingsKey", settingsKey);
    }
    model.addAttribute(
        "clientUISettingsPref", getUserPreferenceValue(user, Preference.UI_CLIENT_SETTINGS));
    model.addAttribute("enforce_ontologies", anyGroupEnforcesOntologies(user));
    model.addAttribute("allow_bioOntologies", allGroupsAllowBioOntologies(user));

    return new ModelAndView(STRUCTURED_DOCUMENT_EDITOR_VIEW_NAME);
  }

  /**
   * Views a specific version of a document for read-only display.
   *
   * <p>Retrieves requested document by versioned globalId, then redirects to original /audit/view
   * endpoint that uses record id and revision number as parameters.
   *
   * @param oidString required, document global id with version number
   * @return
   */
  @GetMapping(
      value = "/audit/view",
      params = {"globalId"})
  public String getDocumentVersion(
      @RequestParam("globalId") String oidString, Principal principal) {

    if (!GlobalIdentifier.isValid(oidString)) {
      throw new IllegalArgumentException(format("Invalid syntax of oid [%s]", oidString));
    }
    GlobalIdentifier oid = new GlobalIdentifier(oidString);
    if (!oid.hasVersionId()) {
      throw new IllegalArgumentException(
          format("Unexpected call to audit view without document version [%s]", oidString));
    }

    User user = getUserByUsername(principal.getName());
    Long recordId = oid.getDbId();
    Long userVersion = oid.getVersionId();

    Record record = assertAccessToRecord(recordId, PermissionType.READ, user);
    if (!record.isStructuredDocument()) {
      throw new IllegalArgumentException(
          "Viewing record's audit history only works with StructuredDocuments!");
    }

    Number revision;
    if (!record.isDeleted()) {
      revision = auditMgr.getRevisionNumberForDocumentVersion(record.getId(), userVersion);
    } else {
      revision = auditMgr.restoredDeletedForView(recordId).getRevision();
    }

    return "redirect:"
        + STRUCTURED_DOCUMENT_AUDIT_VIEW_URL
        + "?recordId="
        + recordId
        + "&revision="
        + revision;
  }

  private Record assertAccessToRecord(long recordId, PermissionType permType, User user) {
    Record record = null; // handle unauthorized access in a consistent way
    boolean throwException = false;
    try {
      record = recordManager.get(recordId);
      if (record == null || !permissionUtils.isPermitted(record, permType, user)) {
        throwException = true;
      }
    } catch (Exception e) {
      log.warn("Attempted access to non-existent document by " + user.getUsername());
      throwException = true;
    }
    if (throwException) {
      throw new AuthorizationException(getResourceNotFoundMessage("Record", null));
    }
    return record;
  }

  /**
   * Changed tag string and modify the modification date of the record.
   *
   * @param recordId
   * @param tagtext
   * @return AjaxReturnObject<Boolean>
   */
  @ResponseBody
  @PostMapping("/tagRecord")
  public AjaxReturnObject<Boolean> tagRecord(
      @RequestParam Long recordId, @RequestParam String tagtext, Principal principal) {
    tagtext = tagtext.trim();
    String joinedTagValues =
        String.join(",", DocumentTagManagerImpl.getAllTagValuesFromAllTagsPlusMeta(tagtext));
    ErrorList errorList =
        inputValidator.validateAndGetErrorList(new RSpaceTag(joinedTagValues), new TagValidator());
    if (errorList != null) {
      return new AjaxReturnObject<>(null, errorList);
    }
    User user = getUserByUsername(principal.getName());
    MessagedServiceOperationResult<BaseRecord> result =
        documentTagManager.saveTag(recordId, tagtext, user);
    return result.isSucceeded()
        ? new AjaxReturnObject<>(true, null)
        : new AjaxReturnObject<>(null, ErrorList.of(result.getMessage()));
  }

  @GetMapping("userTags")
  @ResponseBody // used to autocomplete when searching for an existing tag
  public AjaxReturnObject<Set<String>> getTags(
      @RequestParam(value = "tagFilter", required = false, defaultValue = "") String tagFilter) {

    if (!isBlank(tagFilter)) {
      Validate.isTrue(
          tagFilter.length() <= MAX_TAG_LENGTH,
          getText("errors.maxlength", new String[] {"tagFilter", MAX_TAG_LENGTH + ""}));
    }
    User user = userManager.getAuthenticatedUserInSession();
    return new AjaxReturnObject<>(
        documentTagManager.getTagsPlusMetaForViewableELNDocuments(user, tagFilter), null);
  }

  @GetMapping("/userTagsAndOntologies")
  @ResponseBody // used to autocomplete when creating a new tag
  public AjaxReturnObject<Set<String>> getTagsAndOntologies(
      @RequestParam(value = "tagFilter", required = false, defaultValue = "") String tagFilter,
      @RequestParam(value = "pos", defaultValue = "0") String pos) {

    if (!isBlank(tagFilter)) {
      Validate.isTrue(
          tagFilter.length() <= MAX_TAG_LENGTH,
          getText("errors.maxlength", new String[] {"tagFilter", MAX_TAG_LENGTH + ""}));
    }
    User user =
        userManager.getUserByUsername(
            userManager.getAuthenticatedUserInSession().getUsername(), true);
    return new AjaxReturnObject<>(
        documentTagManager.getTagsPlusOntologiesForViewableDocuments(
            user, tagFilter, Integer.parseInt(pos)),
        null);
  }

  /**
   * Creates a template from a document
   *
   * @param recordId the id of the record from which we're making a template
   * @param templateName
   * @param principal
   * @return success/failure message
   * @throws DocumentAlreadyEditedException
   * @throws AuthorizationException if user does not have copy permission on the record
   */
  @PostMapping("/saveTemplate")
  @ResponseBody
  public String saveTemplate(
      @RequestParam(value = "fieldCompositeIds[]") String fieldCompositeIds[],
      @RequestParam("recordId") Long recordId,
      @RequestParam("templateName") String templateName,
      Principal principal)
      throws DocumentAlreadyEditedException {

    User user = getUserByUsername(principal.getName());
    if (fieldCompositeIds.length < 1) {
      return getText("template.creation.nofields.msg");
    }
    List<Long> fieldIds = new ArrayList<Long>();
    for (int i = 0; i < fieldCompositeIds.length; i++) {
      String stx = fieldCompositeIds[i];
      String[] parts = stx.split("_");
      fieldIds.add(Long.parseLong(parts[1]));
    }
    StructuredDocument template =
        recordManager.createTemplateFromDocument(recordId, fieldIds, user, templateName);
    if (template == null) {
      return getText("template.creation.failure.msg");
    }
    publisher.publishEvent(createGenericEvent(user, template, AuditAction.CREATE));

    return getText("template.creation.success.msg");
  }

  @ResponseBody
  @GetMapping("/getPotentialWitnesses")
  public AjaxReturnObject<UserPublicInfo[]> getPotentialWitnesses(
      @RequestParam("recordId") Long recordId, Principal principal) {
    User user = getUserByUsername(principal.getName());
    Record record = recordManager.get(recordId);
    UserPublicInfo[] witnesses = signingManager.getPotentialWitnesses(record, user);
    return new AjaxReturnObject<UserPublicInfo[]>(witnesses, null);
  }

  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"password"})
  @PostMapping("/ajax/proceedSigning")
  @ResponseBody
  public AjaxReturnObject<SignatureInfo> proceedSigning(
      @RequestParam("recordId") Long recordId,
      @RequestParam("statement") String statement,
      @RequestParam("witnesses[]") String witnesses[],
      @RequestParam("password") String password,
      Principal principal) {

    User signer = getUserByUsername(principal.getName());
    User signerOrOperateAsSysadmin = userManager.getOriginalUserForOperateAs(signer);
    Record record = recordManager.get(recordId);

    String errorMsg = null;
    if (!record.getOwner().equals(signerOrOperateAsSysadmin)) {
      errorMsg = "error.authorization.signing";
    } else if (!signingManager.isReauthenticated(signer, password)) {
      errorMsg = "errors.password.invalid";
    } else if (signingManager.isSigned(recordId)) {
      errorMsg = "authorisation.document.signed";
    }
    if (errorMsg != null) {
      return new AjaxReturnObject<SignatureInfo>(null, getErrorListFromMessageCode(errorMsg));
    }

    /* Sign document and Send request to witnesses */
    SigningResult result = signingManager.signRecord(recordId, signer, witnesses, statement);
    Optional<Signature> sig = result.getSignature();
    if (!sig.isPresent()) {
      return new AjaxReturnObject<SignatureInfo>(null, ErrorList.of("Signing failed"));
    }
    return new AjaxReturnObject<SignatureInfo>(sig.get().toSignatureInfo(), null);
  }

  @GetMapping(value = "/ajax/currentContentHash/{docId}")
  @ResponseBody
  public String getCurrentContentHash(@PathVariable("docId") Long docId, Principal principal) {
    User user = getUserByUsername(principal.getName());
    Record record = assertAccessToRecord(docId, PermissionType.READ, user);
    StructuredDocument doc = recordManager.getRecordWithFields(record.getId(), user).asStrucDoc();
    String currentHash = doc.getRecordContentHashForSigning().toHex();
    return currentHash;
  }

  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"password"})
  @PostMapping("/ajax/proceedWitnessing")
  @ResponseBody
  public AjaxReturnObject<SignatureInfo> proceedWitnessing(
      @RequestParam("recordId") Long recordId,
      @RequestParam("option") Boolean option,
      @RequestParam(value = "declineMsg", required = false) String declineOption,
      @RequestParam("password") String password,
      Principal principal) {

    User witnessUser = getUserByUsername(principal.getName());

    /* 2 Check Username and Password */
    if (!signingManager.isReauthenticated(witnessUser, password)) {
      return new AjaxReturnObject<SignatureInfo>(
          null, getErrorListFromMessageCode("errors.password.invalid"));
    }

    /* 3 Update witness */
    Witness witness = signingManager.updateWitness(recordId, witnessUser, option, declineOption);
    Record witnessed = witness.getSignature().getRecordSigned();
    auditService.notify(new GenericEvent(witnessUser, witnessed, AuditAction.WITNESSED));

    SignatureInfo info = witness.getSignature().toSignatureInfo();
    return new AjaxReturnObject<SignatureInfo>(info, null);
  }

  /**
   * Returns copy of passed content adjusted for the target field. Also duplicates RSpace field
   * elements like annotations or chemicals on database level, so the returned copy point to the new
   * copied elements, not to original ones (RSPAC-1957).
   */
  @ResponseBody
  @PostMapping("/copyContentIntoField")
  public String copyContentIntoField(
      @RequestParam("content") String content,
      @RequestParam("fieldId") Long fieldId,
      Principal principal) {
    User user = userManager.getUserByUsername(principal.getName());
    String copiedContent = recordManager.copyRSpaceContentIntoField(content, fieldId, user);
    return copiedContent;
  }
}

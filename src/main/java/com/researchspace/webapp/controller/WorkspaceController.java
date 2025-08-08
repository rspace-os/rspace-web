package com.researchspace.webapp.controller;

import static com.researchspace.core.util.MediaUtils.getContentTypeForFileExtension;
import static com.researchspace.core.util.MediaUtils.getExtension;
import static com.researchspace.session.SessionAttributeUtils.RS_DELETE_RECORD_PROGRESS;

import com.axiope.search.SearchManager;
import com.axiope.search.SearchUtils;
import com.axiope.search.WorkspaceSearchInputValidator;
import com.researchspace.core.util.DefaultURLPaginator;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.core.util.PaginationUtil;
import com.researchspace.core.util.ResponseUtil;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.core.util.URLGenerator;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EditStatus;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditSearchEvent;
import com.researchspace.model.audittrail.CreateAuditEvent;
import com.researchspace.model.audittrail.DuplicateAuditEvent;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.audittrail.MoveAuditEvent;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dto.SharingResult;
import com.researchspace.model.dto.UserPublicInfo;
import com.researchspace.model.dtos.RecordTagData;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.dtos.WorkspaceSettings;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.frontend.CreateMenuFormEntry;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Breadcrumb;
import com.researchspace.model.record.DetailedRecordInformation;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.RecordTypeFilter;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.AuditManager;
import com.researchspace.service.DetailedRecordInformationProvider;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.DocumentTagManager;
import com.researchspace.service.FormManager;
import com.researchspace.service.PostLoginHandler;
import com.researchspace.service.PreWorkspaceViewRecordStatusManager;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.RecordFavoritesManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.impl.CustomFormAppInitialiser;
import com.researchspace.service.impl.DocumentTagManagerImpl;
import com.researchspace.service.impl.RecordDeletionManagerImpl.DeletionSettings;
import com.researchspace.service.impl.RecordEditorTracker;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.session.UserSessionTracker;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import lombok.Setter;
import lombok.Value;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/** Main controller for the Workspace section. */
@Controller
@RequestMapping("/workspace")
public class WorkspaceController extends BaseController {

  private static final String NUMBER_RECORDS = "numberRecords";
  private static final Map<Long, Boolean> USERS_CUSTOM_FORMS_ADDED_TO_MENU = new HashMap<>();

  public static final String ROOT_URL = "/workspace";
  public static final String ROOT_URL_VIEW = "workspace";
  protected static final String WORKSPACE_AJAX = "workspace_ajax";

  static final String WORKSPACE_DELETED_HISTORY_VIEW = "workspace/workspacedeleted_history";
  static final String WORKSPACE_DELETED_HISTORY_VIEW_AJAX =
      "workspace/workspaceDeletedHistory_ajax";

  protected static final int NAME_MAX_LENGTH = 100;
  protected static final int NESTED_NAME_MAX_LENGTH = 1000;
  protected static final int NESTED_NAME_MAX_FOLDERS = 10;
  private static final String BCRUMB = "bcrumb";
  private static final String SEARCH_RESULTS = "searchResults";

  @Autowired private ResponseUtil responseUtil;

  @Autowired private SearchManager searchManager;

  @Autowired private RecordSharingManager sharingManager;

  // Setters are for test code
  @Setter @Autowired private RecordFavoritesManager favoritesManager;

  @Autowired private RecordEditorTracker tracker;

  @Setter @Autowired private RecordDeletionManager deletionManager;

  @Autowired private PreWorkspaceViewRecordStatusManager preWorkspaceViewRecordStatusManager;

  @Setter @Autowired private PostLoginHandler firstLoginHandler;

  @Autowired private WorkspaceHandler workspaceHandler;

  @Autowired private DetailedRecordInformationProvider infoProvider;

  @Autowired private SharingHandler recordShareHandler;

  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  @Setter @Autowired private FormManager formManager;

  @Lazy @Autowired private DocumentTagManager documentTagManager;

  @Autowired private WorkspaceViewModePreferences workspaceViewModePreferences;

  @Autowired private WorkspacePermissionsDTOBuilder permDTObuilder;

  void setAuditManager(AuditManager auditManager) {
    this.auditManager = auditManager;
  }

  /**
   * Main method of workspace controller. Handles initial workspace page load after clicking on the
   * 'Workspace' tab.
   */
  @GetMapping
  public ModelAndView listRootFolder(
      @RequestParam(value = "settingsKey", required = false) String settingsKey,
      Model model,
      Principal principal,
      HttpServletRequest request,
      HttpSession session,
      HttpServletResponse response,
      WorkspaceSettings workspaceSettings)
      throws IOException {

    User user = getUserByUsername(principal.getName());
    setAllowPublishOwnDocumentsByRoleAsPIOrByGroupPermission(model, user);
    if (USERS_CUSTOM_FORMS_ADDED_TO_MENU.get(user.getId()) == null) {
      // add Ontology doc to users menu if required
      RSForm ontologyForm =
          formManager.findOldestFormByName(CustomFormAppInitialiser.ONTOLOGY_FORM_NAME);
      if (!formManager.formExistsInMenuForUser(ontologyForm.getStableID(), user)) {
        formManager.addFormToUserCreateMenu(user, ontologyForm.getId(), user);
      }
      USERS_CUSTOM_FORMS_ADDED_TO_MENU.put(user.getId(), Boolean.TRUE);
    }

    String firstLoginRedirect = firstLoginHandler.handlePostLogin(user, session);
    if (firstLoginRedirect != null) {
      log.info("redirecting new user to: {}", firstLoginRedirect);
      return new ModelAndView("redirect:" + firstLoginRedirect);
    }

    Folder rootRecord = folderManager.getRootFolderForUser(user); // default
    if (rootRecord == null) {
      log.error(
          "INITIALISATION ERROR - Home folder for user {} is null -"
              + "  problem with initialising account? lastLogin=[{}]",
          user.getUsername(),
          user.getLastLogin());
      throw new IllegalStateException(
          String.format(
              "Home folder should be present, but is not for user %s", user.getUsername()));
    }
    // if we have a settings key, then we use that to configure workspace reload
    if (isValidSettingsKey(settingsKey)) {
      WorkspaceListingConfig cfg =
          (WorkspaceListingConfig) request.getSession().getAttribute(settingsKey);
      if (cfg != null) {
        rootRecord = folderManager.getFolder(cfg.getParentFolderId(), user);
      }
    }

    relist(workspaceSettings, settingsKey, model, request, session, response, user, rootRecord);
    setPublicationAllowed(model, principal.getName());
    return new ModelAndView(ROOT_URL_VIEW);
  }

  private void setAllowPublishOwnDocumentsByRoleAsPIOrByGroupPermission(Model model, User user) {
    boolean publishOwnDocsAllowed = false;
    if (user.hasRole(Role.PI_ROLE)) {
      publishOwnDocsAllowed = true;
    } else {
      user = userManager.getUserByUsername(user.getUsername(), true);
      publishOwnDocsAllowed = false;
      Set<Group> collaborationGroups = user.getCollaborationGroups();
      for (Group userGroup : user.getGroups()) {
        if (!collaborationGroups.contains(userGroup)) {
          if (userGroup.isPublicationAllowed() || userGroup.isProjectGroup()) {
            publishOwnDocsAllowed = true;
          } else { // if ANY group forbids publication, set this to false
            publishOwnDocsAllowed = false;
            break;
          }
        }
      }
    }
    model.addAttribute("publish_own_documents", publishOwnDocsAllowed);
  }

  /** Loads workspace page opened on a given folder, i.e. after following global id FL link */
  @GetMapping("/{folderId}")
  public ModelAndView listWorkspaceFolderById(
      @PathVariable("folderId") Long folderId,
      WorkspaceSettings workspaceSettings,
      @RequestParam(value = "settingsKey", required = false) String settingsKey,
      Model model,
      HttpServletRequest request,
      HttpSession session,
      HttpServletResponse response,
      Principal principal)
      throws IOException {

    User user = getUserByUsername(principal.getName());
    setAllowPublishOwnDocumentsByRoleAsPIOrByGroupPermission(model, user);
    Folder folder = folderManager.getFolder(folderId, user);
    relist(workspaceSettings, settingsKey, model, request, session, response, user, folder);
    setPublicationAllowed(model, principal.getName());
    return new ModelAndView(ROOT_URL_VIEW);
  }

  /**
   * Retrieves file list in current folder as well as persists workspace settings such as pagination
   * criteria and view mode to the database, and updates workspace settings accordingly.
   */
  private void relist(
      WorkspaceSettings workspaceSettings,
      String settingsKey,
      Model model,
      HttpServletRequest request,
      HttpSession session,
      HttpServletResponse response,
      User user,
      Folder folder)
      throws IOException {

    WorkspaceListingConfig cfg = null;
    Long grandparentFolderId = workspaceSettings.getParentFolderId();

    // if we have a settings key, then we use that to configure workspace reload
    if (isValidSettingsKey(settingsKey)
        && (cfg = (WorkspaceListingConfig) request.getSession().getAttribute(settingsKey))
            != null) {
      if (WorkspaceSettings.isSearchTermsSet(cfg.getSrchOptions(), cfg.getSrchTerms())) {
        doSearch(cfg, model, request, response, user);
      } else {
        cfg.setCurrentViewMode(
            listFolderAndSetWorkspaceAttributes(
                folder,
                model,
                cfg.getFilters(),
                cfg.getPgCrit(),
                cfg.getCurrentViewMode(),
                user,
                grandparentFolderId));
      }

      model.addAttribute("settingsKey", settingsKey);
      model.addAttribute("workspaceConfigJson", cfg.toJson());
    } else {
      // We use the settings coming into the request. We always list records in a folder
      // instead of searching, because search uses ajax, except when going back from a
      // record, which would have triggered the first if statement.
      workspaceSettings.setCurrentViewMode(
          listFolderAndSetWorkspaceAttributes(
              folder,
              model,
              workspaceSettings.createWorkspaceFilter(),
              workspaceSettings.createPaginationCriteria(),
              workspaceSettings.getCurrentViewMode(),
              user,
              grandparentFolderId));

      workspaceSettings.setParentFolderId(folder.getId());
      cfg = new WorkspaceListingConfig(workspaceSettings);
      cfg.setGrandparentFolderId(grandparentFolderId);
      addWorkspaceConfigToSessionAndKeyModel(cfg, model, session);
      model.addAttribute("workspaceConfigJson", workspaceSettings.toJson());
    }
  }

  private void addWorkspaceConfigToSessionAndKeyModel(
      WorkspaceListingConfig config, Model model, HttpSession session) {
    String settingsKey = RandomStringUtils.randomAlphabetic(10);
    model.addAttribute("settingsKey", settingsKey);
    session.setAttribute(settingsKey, config);
  }

  private List<String> parseNestedFolderPath(String nestedFolderPath) {
    // Split nested folder path
    List<String> folderNames = Arrays.asList(nestedFolderPath.split("/+"));
    // Strip and abbreviate folder names
    return folderNames.stream()
        .map(String::trim)
        .map(this::abbreviateName)
        .collect(Collectors.toList());
  }

  private Folder doNewFolderCreate(long parentRecordId, String folderName, Principal principal) {
    folderName = abbreviateName(folderName);
    User user = getUserByUsername(principal.getName());
    Folder newFolder = folderManager.createNewFolder(parentRecordId, folderName, user);

    folderManager.save(newFolder, user);
    publisher.publishEvent(createGenericEvent(user, newFolder, AuditAction.CREATE));

    return newFolder;
  }

  @PostMapping("/ajax/create_folder/{parentId}")
  @ResponseBody
  public AjaxReturnObject<Long> createFolderAjax(
      @PathVariable("parentId") long parentFolderId,
      @RequestParam("folderNameField") String folderName,
      Principal principal) {
    ErrorList error = new ErrorList();

    if (folderName.length() > NESTED_NAME_MAX_LENGTH) {
      error.addErrorMsg(
          String.format(
              "Nested folder name above %d characters is not allowed.", NESTED_NAME_MAX_LENGTH));
      return new AjaxReturnObject<>(null, error);
    }

    List<String> nestedFolderNames = parseNestedFolderPath(folderName);
    if (nestedFolderNames.size() > NESTED_NAME_MAX_FOLDERS) {
      error.addErrorMsg(
          String.format("More than %d nested folders are not allowed.", NESTED_NAME_MAX_FOLDERS));
      return new AjaxReturnObject<>(null, error);
    }

    long newFolderId = parentFolderId;
    for (String newFolderName : nestedFolderNames) {
      newFolderId = doNewFolderCreate(newFolderId, newFolderName, principal).getId();
    }

    return new AjaxReturnObject<>(newFolderId, null);
  }

  @PostMapping("/create_notebook/{recordid}")
  public String createNotebookAndRedirect(
      @PathVariable("recordid") long parentRecordId,
      @RequestParam("notebookNameField") String notebookName,
      Principal principal) {
    User user = getUserByUsername(principal.getName());
    Long targetFolderId = parentRecordId;
    Folder originalParentFolder = null;
    List<RecordGroupSharing> sharedWithGroup = null;

    originalParentFolder = folderManager.getFolder(parentRecordId, user);
    if (originalParentFolder != null && originalParentFolder.isSharedFolder()) {
      targetFolderId = folderManager.getRootFolderForUser(user).getId();
    }
    Long newNotebookId = createNotebook(targetFolderId, notebookName, principal);

    if (originalParentFolder != null && originalParentFolder.isSharedFolder()) {
      sharedWithGroup =
          recordShareHandler.shareIntoSharedFolderOrNotebook(
              user, originalParentFolder, newNotebookId);
    }

    return getNotebookRedirectUrl(newNotebookId, sharedWithGroup);
  }

  @NotNull
  private static String getNotebookRedirectUrl(
      Long newNotebookId, List<RecordGroupSharing> sharedWithGroup) {
    String redirectUrl = "redirect:/notebookEditor/" + newNotebookId;
    if (!(sharedWithGroup == null || sharedWithGroup.isEmpty())) {
      redirectUrl +=
          "?sharedWithGroup="
              + URLEncoder.encode(
                  sharedWithGroup.get(0).getSharee().getDisplayName(), StandardCharsets.UTF_8);
    }
    return redirectUrl;
  }

  @PostMapping("/ajax/createNotebook")
  @ResponseBody
  public Long createNotebook(
      @RequestParam(value = "parentFolderId", required = false) Long parentRecordId,
      @RequestParam(value = "notebookName", required = false) String notebookName,
      Principal principal) {

    User user = getUserByUsername(principal.getName());
    if (parentRecordId == null) {
      parentRecordId = user.getRootFolder().getId();
    }
    Notebook newNotebook = workspaceHandler.createNotebook(parentRecordId, notebookName, user);
    log.info("notebook created and named: {}", notebookName);

    HistoricalEvent event = createGenericEvent(user, newNotebook, AuditAction.CREATE);
    publisher.publishEvent(event);
    return newNotebook.getId();
  }

  protected String abbreviateName(String folderName) {
    return StringUtils.abbreviate(folderName, NAME_MAX_LENGTH);
  }

  /**
   * Copies one or more records, all with the same parent.
   *
   * @param idToCopy A Long [] of record ids of records to copy
   * @param newName A String of new names for the records
   * @return A {@link ModelAndView}
   */
  @PostMapping("/ajax/copy")
  public ModelAndView copy(
      @RequestParam("idToCopy[]") Long[] idToCopy,
      @RequestParam("newName[]") String[] newName,
      @RequestParam(value = "settingsKey", required = false) String settingsKey,
      WorkspaceSettings workspaceSettings,
      Model model,
      HttpServletRequest request,
      HttpSession session,
      HttpServletResponse response,
      Principal principal)
      throws IOException {

    User subject = getUserByUsername(principal.getName());

    RecordCopyResult result = null;
    Long currentIdToCopy = null;
    String currentNewName = null;
    for (int indx = 0; indx < idToCopy.length; indx++) {
      currentIdToCopy = idToCopy[indx];
      currentNewName = newName[indx];
      boolean isRecord = isRecord(currentIdToCopy);
      if (currentNewName == null) {
        currentNewName = "Unknown Name_Copy";
      }
      // RSPAC-1558
      fitNameToMaxSize(newName, indx);
      BaseRecord oldRecord = baseRecordManager.get(currentIdToCopy, subject);
      if (isRecord) {
        result = recordManager.copy(currentIdToCopy, currentNewName, subject, null);
      } else {
        result = folderManager.copy(currentIdToCopy, subject, currentNewName);
      }
      auditService.notify(
          new DuplicateAuditEvent(
              subject, result.getOriginalToCopy(), oldRecord, oldRecord.getName(), currentNewName));
      auditService.notify(new CreateAuditEvent(subject, result.getCopy(oldRecord), currentNewName));
    }
    Folder parent = folderManager.getFolder(workspaceSettings.getParentFolderId(), subject);
    relist(workspaceSettings, settingsKey, model, request, session, response, subject, parent);
    setPublicationAllowed(model, principal.getName());
    return new ModelAndView(WORKSPACE_AJAX);
  }

  void fitNameToMaxSize(String[] newname, int indx) {
    if (newname[indx].length() > BaseRecord.DEFAULT_VARCHAR_LENGTH) {
      newname[indx] =
          StringUtils.substring(newname[indx], 0, BaseRecord.DEFAULT_VARCHAR_LENGTH - 6) + "_Copy";
    }
  }

  @ResponseBody
  @GetMapping("/getEcatMediaFile/{id}")
  public void displayEcatMediaFile(
      @PathVariable("id") Long id, Principal principal, HttpServletResponse res)
      throws IOException {
    Record mediaRecord = recordManager.get(id);
    if (!mediaRecord.isMediaRecord()) {
      throw new IllegalArgumentException("Not a media record");
    }
    User user = getUserByUsername(principal.getName());
    assertAuthorisation(user, mediaRecord, PermissionType.READ);

    EcatMediaFile mediaFile = (EcatMediaFile) mediaRecord;

    responseUtil.setCacheTimeInBrowser(ResponseUtil.YEAR, null, res);
    display(mediaFile, res);
  }

  private void display(EcatMediaFile mediaFile, HttpServletResponse res) throws IOException {

    res.setContentType(getContentTypeForFileExtension(getExtension(mediaFile.getExtension())));
    res.setHeader("Content-Disposition", "attachment; filename=" + mediaFile.getFileName());
    ServletOutputStream outStream = res.getOutputStream();
    Optional<FileInputStream> fisOpt = getFileFromFileStore(mediaFile);
    if (fisOpt.isPresent()) {
      try (FileInputStream fis = fisOpt.get();
          BufferedOutputStream bos = new BufferedOutputStream(outStream)) {
        IOUtils.copy(fis, bos);
        bos.flush();
      }
    } else {
      log.error("Could not retrieve mediafile {}", mediaFile.getId());
    }
  }

  private Optional<FileInputStream> getFileFromFileStore(EcatMediaFile mediaFile) {
    FileProperty fp = mediaFile.getFileProperty();
    return fileStore.retrieve(fp);
  }

  /**
   * Moves one or more records, all with the same parent.
   *
   * @return A {@link ModelAndView}
   */
  @PostMapping("/ajax/move")
  public ModelAndView move(
      @RequestParam("toMove[]") Long[] toMove,
      @RequestParam("target") String targetFolderId,
      @RequestParam(value = "settingsKey", required = false) String settingsKey,
      WorkspaceSettings settings,
      Model model,
      Principal principal,
      HttpServletRequest request,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {

    if (toMove == null
        || toMove.length == 0
        || targetFolderId == null
        || targetFolderId.isEmpty()) {
      throw new IllegalArgumentException("No records to move");
    }

    User user = getUserByUsername(principal.getName());
    Folder usersRootFolder = folderManager.getRootFolderForUser(user);
    Folder target = null;

    // handle input which might contain a /
    if ("/".equals(targetFolderId)) {
      target = usersRootFolder;
    } else {
      if (targetFolderId.endsWith("/")) {
        targetFolderId = targetFolderId.substring(0, targetFolderId.length() - 1);
      }
      target = folderManager.getFolder(Long.parseLong(targetFolderId), user);
    }

    int moveCounter = 0;
    for (Long recordIdToMove : toMove) {
      if (target.getId().equals(recordIdToMove)) {
        continue;
      }
      Folder sourceFolder =
          getMoveSourceFolderId(
              recordIdToMove, settings.getParentFolderId(), user, usersRootFolder);
      boolean isFolder = !isRecord(recordIdToMove) || recordManager.get(recordIdToMove).isFolder();
      ServiceOperationResult<? extends BaseRecord> moveResult = null;
      if (isFolder) {
        moveResult = folderManager.move(recordIdToMove, target.getId(), sourceFolder.getId(), user);
      } else {
        BaseRecord baseRecordToMove = recordManager.get(recordIdToMove);
        if (recordManager.isSharedNotebookWithoutCreatePermission(user, target)) {
          try {
            Group group = groupManager.getGroupFromAnyLevelOfSharedFolder(user, sourceFolder);
            SharingResult sharingResult =
                recordShareHandler.moveIntoSharedNotebook(
                    group, baseRecordToMove, (Notebook) target);
            if (!sharingResult.getSharedIds().isEmpty()) {
              moveCounter = moveCounter + sharingResult.getSharedIds().size();
            }
          } catch (Exception ex) {
            log.error(
                "It was not possible to move the record [{}] into the shared notebook [{}]: {}",
                baseRecordToMove.getId(),
                target.getId(),
                ex.getMessage());
            break;
          }
        } else {
          moveResult =
              recordManager.move(recordIdToMove, target.getId(), sourceFolder.getId(), user);
        }
      }
      if (moveResult != null && moveResult.isSucceeded()) {
        moveCounter++;
        auditService.notify(new MoveAuditEvent(user, moveResult.getEntity(), sourceFolder, target));
      }
    }

    if (moveCounter == toMove.length) {
      model.addAttribute("successMsg", getText("workspace.move.success"));
    } else {
      String msgKey;
      if (moveCounter == 0) {
        msgKey = getText("workspace.move.nothing.moved");
      } else {
        msgKey = getText("workspace.move.some.not.moved");
      }
      model.addAttribute("errorMsg", getText(msgKey));
    }

    Folder source = folderManager.getFolder(settings.getParentFolderId(), user);
    relist(settings, settingsKey, model, request, session, response, user, source);
    setPublicationAllowed(model, principal.getName());
    return new ModelAndView(WORKSPACE_AJAX);
  }

  private Folder getMoveSourceFolderId(
      Long baseRecordId, Long workspaceParentId, User user, Folder usersRootFolder) {
    /* if workspaceParentId is among parent folders, then use it */
    BaseRecord baseRecord = baseRecordManager.get(baseRecordId, user);
    for (RecordToFolder recToFolder : baseRecord.getParents()) {
      if (recToFolder.getFolder().getId().equals(workspaceParentId)) {
        return recToFolder.getFolder();
      }
    }
    /* workspace parent may be incorrect i.e. for search results. in that case
     * return the parent which would appear in getInfo, or after opening the document */
    RSPath pathToRoot = baseRecord.getShortestPathToParent(usersRootFolder);
    return pathToRoot
        .getImmediateParentOf(baseRecord)
        .orElseThrow(
            () -> new IllegalAddChildOperation("Attempted to get parent folder of root folder"));
  }

  @PostMapping("/ajax/delete")
  public ModelAndView delete(
      @RequestParam("toDelete[]") Long[] toDelete,
      @RequestParam(value = "settingsKey", required = false) String settingsKey,
      Model model,
      WorkspaceSettings settings,
      HttpServletRequest request,
      Principal principal,
      HttpSession session,
      HttpServletResponse response)
      throws IllegalAddChildOperation, DocumentAlreadyEditedException, IOException {

    UserSessionTracker users = getCurrentActiveUsers();
    User user = getUserByUsername(principal.getName());
    Long parentFolderId = settings.getParentFolderId();
    Folder parent = parentFolderId == null ? null : folderManager.getFolder(parentFolderId, user);
    boolean isNotebookEntryDeletion = parent != null && parent.isNotebook();

    ProgressMonitor progress =
        createProgressMonitor(
            RS_DELETE_RECORD_PROGRESS, toDelete.length * 10, "Deleting records", session);
    DeletionSettings delContext =
        DeletionSettings.builder()
            .grandParentFolderId(settings.getGrandparentFolderId())
            .notebookEntryDeletion(isNotebookEntryDeletion)
            .parent(parent)
            .currentUsers(users)
            .noAccessHandler(new NoAccessHandler(model))
            .build();
    ServiceOperationResultCollection<CompositeRecordOperationResult, Long> result =
        deletionManager.doDeletion(
            toDelete, SessionAttributeUtils::getSessionId, delContext, user, progress);

    if (isNotebookEntryDeletion) {
      /* let's set up proper grandparent for listFilesInFolder (RSPAC-991) */
      settings.setParentFolderId(settings.getGrandparentFolderId());
    }
    if (parent == null) {
      parent =
          result.getResults().stream()
              .filter(res -> res.getParentContainer() != null)
              .findFirst()
              .map(CompositeRecordOperationResult::getParentContainer)
              .get();
    }
    relist(settings, settingsKey, model, request, session, response, user, parent);
    progress.done();
    setPublicationAllowed(model, principal.getName());
    return new ModelAndView(WORKSPACE_AJAX);
  }

  /** Handles case when document can't be accessed for edit */
  @Value
  class NoAccessHandler implements BiConsumer<Long, EditStatus> {

    Model model;

    @Override
    public void accept(Long id, EditStatus es) {
      log.warn("Document cannot be deleted: edit status is {}", es);
      String editor = tracker.getEditingUserForRecord(id);
      model.addAttribute(
          "errorMsg", getText("document.delete.failure.msg", new Object[] {id, editor}));
    }
  }

  @PostMapping("/ajax/favorites/add")
  public @ResponseBody AjaxReturnObject<List<Long>> addToFavorites(
      @RequestParam("recordsIds[]") Long[] recordsIds, Principal principal) {
    List<Long> favorites = new ArrayList<>();
    User user = getUserByUsername(principal.getName());
    for (Long id : recordsIds) {
      favoritesManager.saveFavoriteRecord(id, user.getId());
      favorites.add(id);
    }
    return new AjaxReturnObject<>(favorites, null);
  }

  @PostMapping("/ajax/favorites/remove")
  public @ResponseBody AjaxReturnObject<List<Long>> removeFromFavorites(
      @RequestParam("recordsIds[]") Long[] recordsIds, Principal principal) {
    List<Long> removed = new ArrayList<>();
    User user = getUserByUsername(principal.getName());
    for (Long id : recordsIds) {
      favoritesManager.deleteFavoriteRecord(id, user.getId());
      removed.add(id);
    }
    return new AjaxReturnObject<>(removed, null);
  }

  @ResponseBody
  @PostMapping("/ajax/shareRecord")
  public AjaxReturnObject<SharingResult> shareRecord(
      @RequestBody ShareConfigCommand shareConfig, Principal principal) {
    User sharer = userManager.getUserByUsername(principal.getName(), true);
    ErrorList error = new ErrorList();
    if (shareConfig.isPublish()) {
      User anonymous = userManager.getUserByUsername(RecordGroupSharing.ANONYMOUS_USER);
      ShareConfigElement anonymousShare = new ShareConfigElement();
      anonymousShare.setUserId(anonymous.getId());
      anonymousShare.setOperation("read");
      ShareConfigElement existingAnonymousShareConfig = shareConfig.getValues()[0];
      anonymousShare.setDisplayContactDetails(
          existingAnonymousShareConfig.isDisplayContactDetails());
      anonymousShare.setPublicationSummary(existingAnonymousShareConfig.getPublicationSummary());
      anonymousShare.setPublishOnInternet(existingAnonymousShareConfig.isPublishOnInternet());
      shareConfig.setValues(new ShareConfigElement[] {anonymousShare});
    }
    SharingResult sharingResult = recordShareHandler.shareRecordsWithResult(shareConfig, sharer);
    return new AjaxReturnObject<>(sharingResult, error);
  }

  @GetMapping("trash/list")
  public ModelAndView listDeletedDocuments(
      Principal principal,
      @RequestParam(value = "name", required = false) String name,
      PaginationCriteria<AuditedRecord> pgCrit,
      Model model) {
    if (pgCrit.setOrderByIfNull("deletedDate")) {
      pgCrit.setSortOrder(SortOrder.DESC);
    }
    ModelAndView mav = doDeletedList(principal, name, pgCrit);
    mav.setViewName(WORKSPACE_DELETED_HISTORY_VIEW);
    setPublicationAllowed(model, principal.getName());
    return mav;
  }

  private void setPublicationAllowed(Model model, String uName) {
    User user = userManager.getUserByUsername(uName, true);
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(user, "public_sharing"));
    boolean seoAllowedByAdmin =
        systemPropertyPermissionManager.isPropertyAllowed(user, "publicdocs_allow_seo");
    boolean effectivelyAllowSeo =
        seoAllowedByAdmin && forbidSeoForNonPiIfAnyGroupDoesNotAllow(user);
    model.addAttribute("publicdocs_allow_seo", effectivelyAllowSeo);
  }

  private boolean forbidSeoForNonPiIfAnyGroupDoesNotAllow(User user) {
    if (!user.isPI()) {
      for (Group g : user.getGroups()) {
        if (!g.isSeoAllowed() && (!g.isProjectGroup() && !g.isCollaborationGroup())) {
          return false;
        }
      }
    }
    return true;
  }

  @GetMapping("trash/ajax/list")
  public ModelAndView listAjaxDeletedDocuments(
      Principal principal,
      PaginationCriteria<AuditedRecord> pgCrit,
      @RequestParam(value = "name", required = false) String name,
      Model model) {
    ModelAndView mav = doDeletedList(principal, name, pgCrit);
    mav.setViewName(WORKSPACE_DELETED_HISTORY_VIEW_AJAX);
    setPublicationAllowed(model, principal.getName());
    return mav;
  }

  private ModelAndView doDeletedList(
      Principal principal, String name, PaginationCriteria<AuditedRecord> pgCrit) {
    DefaultURLPaginator urlGn = new DefaultURLPaginator("/workspace/trash/ajax/list", pgCrit);
    User user = getUserByUsername(principal.getName());
    updateResultsPerPageProperty(user, pgCrit, Preference.DELETED_RECORDS_RESULTS_PER_PAGE);
    ISearchResults<AuditedRecord> rc = auditManager.getDeletedDocuments(user, name, pgCrit);
    List<PaginationObject> listings =
        PaginationUtil.generatePagination(rc.getTotalPages(), rc.getPageNumber(), urlGn);
    ModelAndView mav = new ModelAndView();
    mav.addObject("paginationList", listings);
    mav.addObject(NUMBER_RECORDS, rc.getHitsPerPage());
    mav.addObject("deleted", rc);
    return mav;
  }

  /**
   * Controller method to handle the search results (By Full Text, Name, Date, Form, Attachment,
   * Media files). This method should be generic to generate different aspect of the model.
   */
  @GetMapping("/ajax/search")
  public ModelAndView searchAjax(
      @RequestParam("options[]") String[] options,
      @RequestParam("terms[]") String[] terms,
      WorkspaceSettings settings,
      Model model,
      HttpServletRequest request,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {

    User user = userManager.getAuthenticatedUserInSession();
    log.info("options[] is {}", request.getParameter("options[]"));
    log.info("terms[] is {}", request.getParameter("terms[]"));
    log.info("options is {}", request.getParameter("options"));
    log.info("terms is {}", request.getParameter("terms"));

    WorkspaceListingConfig searchConfig = new WorkspaceListingConfig(settings, options, terms);
    doSearch(searchConfig, model, request, response, user);
    addWorkspaceConfigToSessionAndKeyModel(searchConfig, model, session);
    setPublicationAllowed(model, user.getUsername());
    return new ModelAndView(WORKSPACE_AJAX);
  }

  /**
   * Simplified document search, requires just a search query.
   *
   * @return json with a list of found documents
   */
  @GetMapping("/ajax/simpleSearch")
  @ResponseBody
  public AjaxReturnObject<List<RecordInformation>> simpleSearchAjax(
      @RequestParam String searchQuery, Principal principal) throws IOException {

    User user = userManager.getUserByUsername(principal.getName());
    List<BaseRecord> foundRecords =
        searchManager.searchUserRecordsWithSimpleQuery(user, searchQuery, 50).getResults();
    List<RecordInformation> recordInfos = new ArrayList<>();
    for (BaseRecord rec : foundRecords) {
      recordInfos.add(new RecordInformation(rec));
    }
    return new AjaxReturnObject<>(recordInfos, null);
  }

  /** Loads child records from Ajax request, performs search if this is set. */
  @GetMapping("/ajax/view/{recordId}")
  public ModelAndView view(
      @PathVariable("recordId") Long parentFolderId,
      WorkspaceSettings settings,
      Model model,
      Principal principal,
      HttpSession session) {
    User user = getUserByUsername(principal.getName());
    Folder parentFolder = folderManager.getFolder(parentFolderId, user);
    listFilesInFolder(settings, model, session, user, parentFolder);
    setPublicationAllowed(model, principal.getName());
    return new ModelAndView(WORKSPACE_AJAX);
  }

  /** Widely used function to simply list files in a folder */
  private void listFilesInFolder(
      WorkspaceSettings settings,
      Model model,
      HttpSession session,
      User user,
      Folder parentFolder) {

    if (parentFolder != null) {
      listFolderAndSetWorkspaceAttributes(
          parentFolder,
          model,
          settings.createWorkspaceFilter(),
          settings.createPaginationCriteria(),
          settings.getCurrentViewMode(),
          user,
          settings.getParentFolderId());

      Long grandparentFolderId = settings.getParentFolderId();
      settings.setParentFolderId(parentFolder.getId()); // update with new parent id
      WorkspaceListingConfig config = new WorkspaceListingConfig(settings);
      config.setGrandparentFolderId(grandparentFolderId);
      addWorkspaceConfigToSessionAndKeyModel(config, model, session);

    } else {
      throw new IllegalArgumentException("Either of search terms or parent folder must be set");
    }
  }

  @ResponseBody
  @GetMapping("/ajax/getViewablePublicUserInfoList")
  public AjaxReturnObject<List<UserPublicInfo>> getViewablePublicUserInfoList() {
    User userInSession = userManager.getAuthenticatedUserInSession();
    List<UserPublicInfo> userInfos = new ArrayList<>();
    List<User> users = userManager.getViewableUserList(userInSession);
    for (User user : users) {
      userInfos.add(user.toPublicInfo());
    }
    return new AjaxReturnObject<>(userInfos, null);
  }

  private WorkspaceListingConfig doSearch(
      WorkspaceListingConfig config,
      Model model,
      HttpServletRequest request,
      HttpServletResponse response,
      User user)
      throws IOException {
    validateSearchParams(request, response, config, user);

    model.addAttribute("user", user);
    Folder rootRecord;
    if (config.isAttachmentSearch()) {
      rootRecord = folderManager.getGalleryRootFolderForUser(user);
    } else {
      rootRecord = folderManager.getRootFolderForUser(user);
    }
    config.setParentFolderId(rootRecord.getId());

    updateResultsPerPageProperty(user, config.getPgCrit(), Preference.WORKSPACE_RESULTS_PER_PAGE);
    ISearchResults<BaseRecord> records = searchManager.searchWorkspaceRecords(config, user);

    List<PaginationObject> linkPages = null;
    if (records.getHits() == 0) {
      log.info("No search results from config [{}]", config);
      model.addAttribute("empty", "empty");
      addCoreSearchModelAttributes(rootRecord, model, records, null, user);
    } else {
      URLGenerator urlGn = new SearchPaginator(config, user);
      linkPages =
          generateNewPaginationLinks(config.getPgCrit(), model, records.getTotalPages(), urlGn);
    }

    addCoreSearchModelAttributes(rootRecord, model, records, linkPages, user);
    auditService.notify(new AuditSearchEvent(user, config, config.getPgCrit()));
    return config;
  }

  private void validateSearchParams(
      HttpServletRequest request,
      HttpServletResponse response,
      WorkspaceListingConfig input,
      User subject) {
    ErrorList errors =
        inputValidator.validateAndGetErrorList(input, new WorkspaceSearchInputValidator(subject));
    if (errors != null && errors.hasErrorMessages()) {
      request.setAttribute("ajax.errors", errors);
      response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
      throw new IllegalArgumentException("Invalid search input");
    }
  }

  private List<PaginationObject> generateNewPaginationLinks(
      PaginationCriteria<BaseRecord> pg, Model model, int totalPages, URLGenerator urlGn) {
    if (pg == null) {
      pg = PaginationCriteria.createDefaultForClass(BaseRecord.class);
    }
    List<PaginationObject> linkPages =
        PaginationUtil.generatePagination(totalPages, pg.getPageNumber().intValue(), urlGn);
    List<PaginationObject> numRecordsPerPage =
        PaginationUtil.generateRecordsPerPageListing(urlGn, null);
    PaginationObject nameLink =
        PaginationUtil.generateOrderByLink(SearchUtils.ORDER_BY_NAME, urlGn, null);
    PaginationObject dateLink =
        PaginationUtil.generateOrderByLink(
            SearchUtils.BASE_RECORD_ORDER_BY_LAST_MODIFIED, urlGn, null);
    PaginationObject creationDateLink =
        PaginationUtil.generateOrderByLink(SearchUtils.BASE_RECORD_ORDER_BY_CREATED, urlGn, null);

    model.addAttribute("numRecordsPerPage", numRecordsPerPage);
    model.addAttribute("orderByName", nameLink);
    model.addAttribute("orderByDate", dateLink);
    model.addAttribute("orderByCreationDate", creationDateLink);
    return linkPages;
  }

  /** Retrieves folders to show in list view and persists some settings to DB */
  private WorkspaceSettings.WorkspaceViewMode listFolderAndSetWorkspaceAttributes(
      Folder parentFolder,
      Model model,
      WorkspaceFilters filters,
      PaginationCriteria<BaseRecord> pgCrit,
      WorkspaceSettings.WorkspaceViewMode viewMode,
      User user,
      Long previousFolderId) {
    // persist / update settings with database
    updateResultsPerPageProperty(user, pgCrit, Preference.WORKSPACE_RESULTS_PER_PAGE);
    viewMode = workspaceViewModePreferences.setOrGetWorkspaceViewMode(viewMode, user);
    ISearchResults<BaseRecord> records = null;
    if (!filters.isSomeFilterActive()) {
      records =
          recordManager.listFolderRecords(
              parentFolder.getId(), pgCrit, RecordTypeFilter.WORKSPACE_FILTER);
    } else {
      records = recordManager.getFilteredRecords(filters, pgCrit, user);
    }

    if (filters.isSomeFilterActive()) {
      model.addAttribute(BCRUMB, getBreadcrumbWithHomeElementOnly(user));
    } else if (!model.containsAttribute(BCRUMB)) {
      Breadcrumb breadcrumbToHome = getBreadcrumbToHome(parentFolder, user, previousFolderId);
      if (!breadcrumbToHome.isContainLinks()) {
        /* maybe the folder is not in a hierarchy of previous folder (i.e. RSPAC-1105).
         * let's try displaying any path to Home. */
        breadcrumbToHome = getBreadcrumbToHome(parentFolder, user, null);
      }
      model.addAttribute(BCRUMB, breadcrumbToHome);
    }

    // Note that the workspace only uses the pagination to know how many pages of search results
    // there are. WorkspaceSettings + search options and terms are stored (and modified) on client
    // side.
    URLGenerator urlGn =
        new DefaultURLPaginator("workspace/ajax/view/" + parentFolder.getId(), pgCrit);

    if (records != null) {
      generateNewPaginationLinks(pgCrit, model, records.getTotalPages(), urlGn);
      List<PaginationObject> linkPages =
          PaginationUtil.generatePagination(
              records.getTotalPages(), pgCrit.getPageNumber().intValue(), urlGn);

      addCoreModelAttributes(parentFolder, model, records, linkPages, user, previousFolderId);
    } else {
      model.addAttribute(NUMBER_RECORDS, 0);
      model.addAttribute(SEARCH_RESULTS, SearchResultsImpl.emptyResult(pgCrit));
    }
    return viewMode;
  }

  private Breadcrumb getBreadcrumbToHome(BaseRecord fromRecord, User usr, Long viaFolderId) {
    Folder rootRecord = folderManager.getRootFolderForUser(usr);
    Folder via = viaFolderId == null ? null : folderManager.getFolder(viaFolderId, usr);
    return breadcrumbGenerator.generateBreadcrumbToHome(fromRecord, rootRecord, via);
  }

  private Breadcrumb getBreadcrumbWithHomeElementOnly(User usr) {
    Folder rootRecord = folderManager.getRootFolderForUser(usr);
    return breadcrumbGenerator.generateBreadcrumbToHome(rootRecord, rootRecord, null);
  }

  private Model addCoreStaticModelAttributes(
      Folder parentFolder, Model model, List<PaginationObject> linkPages, User subject) {
    if (linkPages != null) {
      model.addAttribute("paginationList", linkPages);
    }
    if (parentFolder != null) {
      model.addAttribute("recordId", parentFolder.getId());
      if (parentFolder.isNotebook()) {
        model.addAttribute("parentType", RecordType.NOTEBOOK);
      } else {
        model.addAttribute("parentType", RecordType.FOLDER);
      }
    }

    model.addAttribute("extMessaging", getExternalMessagingIntegrationInfos(subject));
    model.addAttribute("pioEnabled", isProtocolsIOEnabled(subject));
    model.addAttribute("evernoteEnabled", isEvernoteEnabled(subject));
    model.addAttribute("asposeEnabled", isAsposeEnabled());
    model.addAttribute("user", subject);
    Long labGroupFolderId = folderManager.getLabGroupFolderIdForUser(subject);
    if (labGroupFolderId != null) {
      model.addAttribute("labgroupsFolderId", labGroupFolderId);
    }

    addGroupAttributes(model, subject);
    // If not aut o-wired can't add this in constructor
    permDTObuilder.setFolderMger(folderManager);
    permDTObuilder.setRecMgr(recordManager);

    return model;
  }

  /**
   * Sets attributes for the workspace models. There is a similar method for usage when searching.
   * Should be called by ALL controller methods that return workspace or workspace_ajax page.
   *
   * @param parentFolder folder in which we are viewing results / root folder if searching
   * @param records can be <code>null</code>
   * @param linkPages pages to navigate to other search results. Can be null.
   * @param subject user
   * @param previousFolderId
   */
  private void addCoreModelAttributes(
      Folder parentFolder,
      Model model,
      ISearchResults<BaseRecord> records,
      List<PaginationObject> linkPages,
      User subject,
      Long previousFolderId) {

    addCoreStaticModelAttributes(parentFolder, model, linkPages, subject);
    if (records != null) {
      model.addAttribute(NUMBER_RECORDS, records.getHitsPerPage());
      model.addAttribute(SEARCH_RESULTS, records);

      preWorkspaceViewRecordStatusManager.setStatuses(records.getResults(), subject);

      permDTObuilder.addCreateAndOptionsMenuPermissions(
          parentFolder, subject, model, records.getResults(), previousFolderId, false);
    }
  }

  /**
   * Should be called by ALL controller methods that return workspace or workspace_ajax page with
   * search results
   *
   * @param parentFolder
   * @param records can be <code>null</code>
   * @param linkPages links to other pages of the search results
   * @param subject user
   */
  private void addCoreSearchModelAttributes(
      Folder parentFolder,
      Model model,
      ISearchResults<BaseRecord> records,
      List<PaginationObject> linkPages,
      User subject) {

    addCoreStaticModelAttributes(parentFolder, model, linkPages, subject);
    if (records != null) {

      List<BaseRecord> resultEntries = new ArrayList<>(records.getResults());
      model.addAttribute(NUMBER_RECORDS, records.getHitsPerPage());
      model.addAttribute(SEARCH_RESULTS, records);

      preWorkspaceViewRecordStatusManager.setStatuses(resultEntries, subject);

      permDTObuilder.addCreateAndOptionsMenuPermissions(
          parentFolder, subject, model, resultEntries, null, true);
    }
  }

  /** Only needed for full page reload */
  private void addGroupAttributes(Model model, User usr) {
    Set<Group> groups = groupManager.listGroupsForUser();
    model.addAttribute("groups", groups);
    List<User> users = Group.getUniqueUsersInGroups(groups, User.LAST_NAME_COMPARATOR, usr);
    model.addAttribute("uniqueUsers", users);
  }

  @ResponseBody
  @GetMapping("/ajax/getMyTemplates")
  public AjaxReturnObject<List<RecordInformation>> getUsersOwnTemplates() {

    User user = userManager.getAuthenticatedUserInSession();
    List<BaseRecord> viewableTemplates =
        new ArrayList<>(recordManager.getViewableTemplates(Set.of(user.getId())));
    // rspac-2073
    List<RecordInformation> templates = processResults(viewableTemplates);
    return new AjaxReturnObject<>(templates, null);
  }

  @ResponseBody
  @GetMapping("/ajax/getTemplatesSharedWithMe")
  public AjaxReturnObject<List<RecordInformation>> getTemplatesSharedWithUser() {
    User user = userManager.getAuthenticatedUserInSession();
    List<BaseRecord> sharedTemplates = sharingManager.getTemplatesSharedWithUser(user);
    List<RecordInformation> templates = processResults(sharedTemplates);
    return new AjaxReturnObject<>(templates, null);
  }

  private List<RecordInformation> processResults(List<BaseRecord> templates) {
    templates.sort(Collections.reverseOrder(BaseRecord.MODIFICATION_DATE_COMPARATOR));
    return templates.stream().map(BaseRecord::toRecordInfo).collect(Collectors.toList());
  }

  /**
   * Gets record information for getInfo
   *
   * @param revision (optional) requested db revision, currently only supported for media files
   * @param userVersion (optional) requested document version
   */
  @ResponseBody
  @GetMapping("/getRecordInformation")
  public AjaxReturnObject<DetailedRecordInformation> getRecordInformation(
      @RequestParam("recordId") long recordId,
      @RequestParam(value = "revision", required = false) Long revision,
      @RequestParam(value = "version", required = false) Long userVersion) {

    User user = userManager.getAuthenticatedUserInSession();

    DetailedRecordInformation detailedInfo =
        infoProvider.getDetailedRecordInformation(
            recordId, getCurrentActiveUsers(), user, revision, userVersion);
    return new AjaxReturnObject<>(detailedInfo, null);
  }

  /**
   * Gets 'linked by' record information for getInfo. Checks whether current user have permissions
   * to see the linking record, if not then only owner's info is populated into the returned list
   * element.
   *
   * @return information about the linked record
   */
  @ResponseBody
  @GetMapping("/getLinkedByRecords")
  public AjaxReturnObject<List<RecordInformation>> getLinkedByRecords(
      @RequestParam("targetRecordId") Long targetRecordId, Principal principal) {
    User user = userManager.getUserByUsername(principal.getName());
    List<RecordInformation> linkedByRecords = infoProvider.getLinkedByRecords(targetRecordId, user);
    return new AjaxReturnObject<>(linkedByRecords, null);
  }

  /**
   * Similar to `relist()` but only persists workspace settings. Requires settings key.
   *
   * @return an empty string always.
   */
  @ResponseBody
  @PostMapping("/ajax/saveWorkspaceSettings")
  public ResponseEntity<Object> saveWorkspaceSettings(
      @RequestParam(value = "settingsKey") String settingsKey,
      WorkspaceSettings settings,
      Principal principal,
      HttpSession session) {
    // basic validation
    if (!isValidSettingsKey(settingsKey)) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid settings key");
    }
    if (session.getAttribute(settingsKey) == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              "Session did not contain a workspace listing associated with the "
                  + "given settings key");
    } else {
      WorkspaceListingConfig cfg = new WorkspaceListingConfig(settings);
      session.setAttribute(settingsKey, cfg);
    }

    User user = getUserByUsername(principal.getName());
    workspaceViewModePreferences.setOrGetWorkspaceViewMode(settings.getCurrentViewMode(), user);
    PaginationCriteria<BaseRecord> pgCrit = settings.createPaginationCriteria();
    updateResultsPerPageProperty(user, pgCrit, Preference.WORKSPACE_RESULTS_PER_PAGE);
    return ResponseEntity.status(HttpStatus.OK).build();
  }

  /**
   * JSON data listing popular / recent forms that should show up in the Workspace 'Create' menu
   *
   * @param principal for authentication
   * @return list of form entries containing only the data needed to display them
   */
  @ResponseBody
  @GetMapping("/ajax/createMenuEntries")
  public AjaxReturnObject<List<CreateMenuFormEntry>> createMenuEntries(Principal principal) {
    User user = userManager.getUserByUsername(principal.getName());
    List<RSForm> forms = formManager.getDynamicMenuFormItems(user);
    List<CreateMenuFormEntry> entries =
        forms.stream().map(CreateMenuFormEntry::fromRSForm).collect(Collectors.toList());
    return new AjaxReturnObject<>(entries, null);
  }

  /** Get tags for the list of records */
  @GetMapping("/getTagsForRecords")
  @ResponseBody
  public List<RecordTagData> getTagsForRecords(
      @RequestParam("recordIds") List<Long> recordIds, Principal principal) {
    User user = getUserByUsername(principal.getName());
    List<RecordTagData> result = documentTagManager.getRecordTagsForRecordIds(recordIds, user);
    return result;
  }

  /** Save tags for records */
  @PostMapping("/saveTagsForRecords")
  @ResponseBody
  public ResponseEntity<Object> saveTagsForRecords(
      @RequestBody List<RecordTagData> recordTags, Principal principal) {

    User user = getUserByUsername(principal.getName());
    ErrorList errorList;
    for (RecordTagData recordTag : recordTags) {
      if (recordTag.getTagMetaData() != null) {
        String tagMetaData = recordTag.getTagMetaData().trim();
        String joinedTagValues =
            String.join(
                ",", DocumentTagManagerImpl.getAllTagValuesFromAllTagsPlusMeta(tagMetaData));
        errorList =
            inputValidator.validateAndGetErrorList(
                new RSpaceTag(joinedTagValues), new TagValidator());
        if (errorList != null) {
          return ResponseEntity.badRequest().body(errorList);
        }
      }
    }
    documentTagManager.saveTagsForRecords(recordTags, user);
    return ResponseEntity.status(HttpStatus.OK).build();
  }
}

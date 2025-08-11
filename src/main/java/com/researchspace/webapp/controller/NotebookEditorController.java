package com.researchspace.webapp.controller;

import static com.researchspace.service.impl.DocumentTagManagerImpl.allGroupsAllowBioOntologies;
import static com.researchspace.service.impl.DocumentTagManagerImpl.anyGroupEnforcesOntologies;

import com.researchspace.model.EditStatus;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.dtos.FormMenu;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.Breadcrumb;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.FormManager;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.session.UserSessionTracker;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpSession;
import org.apache.http.client.utils.URIBuilder;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/notebookEditor")
public class NotebookEditorController extends BaseController {

  /** View url */
  public static final String ROOT_URL = "/notebookEditor";

  private @Autowired FormManager formManager;
  private @Autowired RecordDeletionManager deletionManager;

  static Logger logger = LoggerFactory.getLogger(NotebookEditorController.class);
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  /**
   * Shows the notebook page and passes the notebook id for the journal plugin to use.
   *
   * @param notebookId
   * @param entryId optional id when we want to load up a specific entry
   * @return A model and view to the notebookEDitor JSP
   * @throws AuthorizationException
   */
  @GetMapping("/{notebookId}")
  public ModelAndView openNotebook(
      @PathVariable("notebookId") Long notebookId,
      @RequestParam(value = "initialRecordToDisplay", required = false) Long entryId,
      @RequestParam(value = "settingsKey", required = false) String settingsKey,
      Model model,
      HttpSession session,
      Principal principal)
      throws AuthorizationException {

    User user = userManager.getUserByUsername(principal.getName());
    Notebook notebook = folderManager.getNotebook(notebookId);

    boolean showWholeNotebook = true;
    StructuredDocument entryToShow = null;

    if (!permissionUtils.isPermitted(notebook, PermissionType.READ, user)) {
      if (entryId != null) {
        StructuredDocument entry = (StructuredDocument) recordManager.get(entryId);
        if (entry.isNotebookEntry()
            && entry.getParentNotebook().getId().equals(notebookId)
            && permissionUtils.isPermitted(entry, PermissionType.READ, user)) {
          showWholeNotebook = false;
          entryToShow = entry;
        }
      }

      if (showWholeNotebook) {
        String msg =
            authGenerator.getFailedMessage(user.getUsername(), "notebook " + notebook.getId());
        throw new AuthorizationException(msg);
      }
    }

    ActionPermissionsDTO permDTO = new ActionPermissionsDTO();
    permDTO.setCreateRecord(
        permissionUtils.isPermitted(notebook, PermissionType.CREATE, user)
            || recordManager.isSharedFolderOrSharedNotebookWithoutCreatePermission(user, notebook));
    // this is a quick fix, we need to hook into permissions system so that
    // shared notebook entries can't be deleted by PI/admin in the way that other shared content
    // can be deleted from shared folders
    permDTO.setDeleteRecord(notebook.getOwner().equals(user));

    model.addAttribute("permDTO", permDTO);
    model.addAttribute("selectedNotebookId", notebookId);
    model.addAttribute("selectedNotebookName", notebook.getName());
    model.addAttribute("notebook", notebook);
    model.addAttribute("entryCount", notebook.getEntryCount());
    model.addAttribute("initialRecordToDisplay", entryId); // can be null
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(user, "public_sharing"));
    if (isValidSettingsKey(settingsKey)) {
      model.addAttribute("settingsKey", settingsKey);
    }

    model.addAttribute("user", user);
    addGroupAttributes(model, user);

    FormMenu formMenu = formManager.generateFormMenu(user);
    model.addAttribute("formsForCreateMenuPagination", formMenu.getFormsForCreateMenuPagination());
    model.addAttribute("forms", formMenu.getForms());
    model.addAttribute("menuToAdd", formMenu.getMenuToAdd());

    model.addAttribute("canSeeNotebook", showWholeNotebook);
    model.addAttribute(
        "canEdit",
        permissionUtils.isPermitted(
            (showWholeNotebook ? notebook : entryToShow), PermissionType.WRITE, user));

    Folder rootRecord = folderManager.getRootFolderForUser(user);
    WorkspaceListingConfig cfg = null;
    Folder notebookparent = null;
    if (isValidSettingsKey(settingsKey)
        && (cfg = (WorkspaceListingConfig) session.getAttribute(settingsKey)) != null) {
      Long grandparentId = cfg.getGrandparentFolderId();
      if (grandparentId != null) {
        notebookparent = folderManager.getFolder(grandparentId, user);
      }
    }
    Breadcrumb bcrumb =
        breadcrumbGenerator.generateBreadcrumbToHome(
            (showWholeNotebook ? notebook : entryToShow), rootRecord, notebookparent);
    model.addAttribute("bcrumb", bcrumb);
    model.addAttribute("extMessaging", getExternalMessagingIntegrationInfos(user));
    model.addAttribute(
        "clientUISettingsPref", getUserPreferenceValue(user, Preference.UI_CLIENT_SETTINGS));
    model.addAttribute("workspaceFolderId", bcrumb.getParentFolderId());
    model.addAttribute("pioEnabled", isProtocolsIOEnabled(user));
    model.addAttribute("evernoteEnabled", isEvernoteEnabled(user));
    model.addAttribute("asposeEnabled", isAsposeEnabled());
    model.addAttribute("isPublished", notebook.isPublished());
    model.addAttribute("enforce_ontologies", anyGroupEnforcesOntologies(user));
    model.addAttribute("allow_bioOntologies", allGroupsAllowBioOntologies(user));

    return new ModelAndView("notebookEditor/notebookEditor", model.asMap());
  }

  private void addGroupAttributes(Model model, User usr) {
    Set<Group> groups = groupManager.listGroupsForUser();
    model.addAttribute("groups", groups);
    List<User> users = Group.getUniqueUsersInGroups(groups, User.LAST_NAME_COMPARATOR, usr);
    model.addAttribute("uniqueUsers", users);
  }

  @ResponseBody
  @PostMapping("/ajax/delete/{recordid}/{parentid}")
  public Long deleteEntry(
      @PathVariable("recordid") Long recordid,
      @PathVariable("parentid") Long parentid,
      Principal principal)
      throws RecordAccessDeniedException, IllegalAddChildOperation, DocumentAlreadyEditedException {

    UserSessionTracker users = getCurrentActiveUsers();

    User user = userManager.getUserByUsername(principal.getName());
    EditStatus es = recordManager.requestRecordEdit(recordid, user, users);
    if (EditStatus.ACCESS_DENIED.equals(es) || EditStatus.CANNOT_EDIT_OTHER_EDITING.equals(es)) {
      throw new RecordAccessDeniedException(
          getText("error.authorization.failure.polite", new Object[] {" delete this document."}));
    }
    try {
      CompositeRecordOperationResult result =
          deletionManager.deleteRecord(parentid, recordid, user);

      auditService.notify(new GenericEvent(user, result, AuditAction.DELETE));
    } finally {
      recordManager.unlockRecord(recordid, user.getUsername());
    }
    return recordid;
  }

  /**
   * Get URL pointing to notebook page for given notebook and entry
   *
   * @param notebookId
   * @param entryId (optional) entry to open, or null
   * @param settingsKey key to workspace settings stored in session
   * @throws URISyntaxException
   * @returns URL to notebook view opened on given entry
   */
  protected static String getNotebookViewUrl(Long notebookId, Long entryId, String settingsKey) {
    try {
      URIBuilder builder = new URIBuilder(ROOT_URL + "/" + notebookId);
      if (entryId != null) {
        builder.addParameter("initialRecordToDisplay", entryId + "");
      }
      if (isValidSettingsKey(settingsKey)) {
        builder.addParameter("settingsKey", settingsKey);
      }
      return builder.build().toString();
    } catch (URISyntaxException e) {
      logger.error(
          "Unable to make valid notebookview URL from entry {} and settingsKey {}",
          entryId,
          settingsKey);
      return ROOT_URL + "/" + notebookId;
    }
  }
}

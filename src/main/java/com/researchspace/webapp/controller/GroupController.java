package com.researchspace.webapp.controller;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.auth.UserPermissionUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.Organisation;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.ProductType;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.dtos.GroupSearchCriteria;
import com.researchspace.model.dtos.GroupValidator;
import com.researchspace.model.dtos.SwapPiCommand;
import com.researchspace.model.dtos.SwapPiValidator;
import com.researchspace.model.dtos.UserRoleView;
import com.researchspace.model.events.GroupMembershipEvent;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.views.GroupInvitation;
import com.researchspace.model.views.GroupListResult;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.model.views.UserView;
import com.researchspace.service.*;
import com.researchspace.session.SessionAttributeUtils;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.session.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Front end for Group CRUD operations */
@Controller
@RequestMapping("/groups")
public class GroupController extends BaseController {
  protected static final String ERROR_ATTRIBUTE_NAME = "error";
  protected static final String EDIT_GROUP_VIEW_NAME = "groups/editGroup";
  protected static final String GROUPS_VIEW_NAME = "groups/viewGroup";
  public static final String GROUPS_VIEW_URL = "/groups/view";

  private final Map<Long, Boolean> groupsWithAutoshareInProgress = new ConcurrentHashMap<>();

  private @Autowired IContentInitializer init;
  private @Autowired MessageOrRequestCreatorManager requestCreateMgr;
  private @Autowired IGroupPermissionUtils groupPermUtils;
  private @Autowired CommunityServiceManager communityMgr;
  private @Autowired SystemPropertyPermissionManager systemPropertyPermissionUtils;
  private @Autowired UserPermissionUtils userPermUtils;
  private @Autowired AutoshareManager autoshareManager;
  private @Autowired CommunicationManager communicationManager;
  private Validator groupValidator;
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  @Autowired private IGroupCreationStrategy groupCreationStrategy;

  @Autowired
  @Qualifier(value = "grpValidator")
  public void setGroupValidator(Validator groupvalidator) {
    this.groupValidator = groupvalidator;
  }

  private void loadUsers(Model model) {
    List<User> users = userManager.getUsers();
    model.addAttribute("users", users);
  }

  private PermissionFactory factory;

  public GroupController() {
    factory = new DefaultPermissionFactory();
  }

  /**
   * Creates a new Group for editing
   *
   * @param model
   * @return edit group view name
   * @throws AuthorizationException if user don't have group create permission
   */
  @GetMapping(value = "admin/", params = "new")
  public String createNew(Model model) throws AuthorizationException {
    permissionUtils.assertIsPermitted("GROUP:CREATE:", "create group");
    Group group = new Group();
    model.addAttribute("group", group);

    List<Group> groups = groupManager.list();
    model.addAttribute("groups", groups);
    loadUsers(model);
    return EDIT_GROUP_VIEW_NAME;
  }

  /**
   * Gets basic information about all users. Requires Create Group permission
   *
   * @return
   */
  @GetMapping(value = "/admin/ajax/allUsers")
  public @ResponseBody List<UserRoleView> getAllUsers() {
    permissionUtils.assertIsPermitted("GROUP:CREATE:", "create group");
    return userManager.getAllUsersViewWithRoles();
  }

  /**
   * Saves a group with users
   *
   * @param group
   * @param errors
   * @return
   * @throws AuthorizationException if user can't edit given group
   */
  @PostMapping("admin/")
  public String createGroup(@ModelAttribute("group") Group group, BindingResult errors, Model model)
      throws AuthorizationException {

    ValidationUtils.invokeValidator(groupValidator, group, errors);
    if (errors.hasErrors()) {
      loadUsers(model);
      return EDIT_GROUP_VIEW_NAME;
    }

    Session session = SecurityUtils.getSubject().getSession();
    User groupCreator = (User) session.getAttribute(SessionAttributeUtils.USER);
    permissionUtils.assertIsPermitted(group, PermissionType.CREATE, groupCreator, "edit group");

    List<User> users = new ArrayList<>();

    for (String user : group.getMemberString()) {
      users.add(userManager.getUserByUsername(user));
      updateUserSessionObject(session, groupCreator, user);
    }
    for (User u : users) {
      if (!u.isContentInitialized()) {
        init.init(u.getId());
      }
    }

    String pis = group.getPis();

    if (!group.isProjectGroup()) {
      String[] piArry = pis.split(",");
      User piowner = userManager.getUserByUsername(piArry[0]);
      if (!group.isProjectGroup() && !piowner.hasRole(Role.PI_ROLE)) {
        errors.addError(new FieldError("group", "pis", "PI does not have a PI role"));
        return EDIT_GROUP_VIEW_NAME;
      }
      group.setOwner(piowner);
    } else {
      group.setOwner(groupCreator);
    }

    group = groupCreationStrategy.createAndSaveGroup(group, groupCreator, users);
    return "redirect:/groups/view/" + group.getId();
  }

  @GetMapping("/viewPIGroup")
  public String viewPIGroup() {
    User subject = userManager.getAuthenticatedUserInSession();
    if (!subject.hasRole(Role.PI_ROLE)) {
      String msg = authGenerator.getFailedMessage(subject.getUsername(), " view PI's group");
      throw new AuthorizationException(msg);
    }
    Group piGroup = subject.getPrimaryLabGroupWithPIRole();
    if (piGroup != null) {
      return "redirect:/groups/view/" + piGroup.getId();
    } else {
      return "redirect:/userform";
    }
  }

  /**
   * Views an individual group
   *
   * @param model
   * @param groupId
   * @return
   * @throws RecordAccessDeniedException
   */
  @GetMapping("/view/{groupId}")
  public String viewGroup(Model model, @PathVariable("groupId") Long groupId, Principal principal)
      throws RecordAccessDeniedException {

    Group group = groupManager.getGroupWithCommunities(groupId);
    if (group == null) {
      throw new IllegalStateException(getResourceNotFoundMessage("Group", groupId));
    }
    User subject = userManager.getUserByUsername(principal.getName());
    subject = getUserWithRefreshedPermissions(subject);

    if (properties.isProfileHidingEnabled()) {
      if (group.isPrivateProfile()) {
        userManager.populateConnectedGroupList(subject);
        if (!subject.isConnectedToGroup(group)) {
          throw new RecordAccessDeniedException(getResourceNotFoundMessage("Group", groupId));
        }
      }
      userManager.populateConnectedUserList(subject);
    }

    // canEdit would allow lab admins without view all to manage the autoshare status
    boolean canManageAutoshare =
        group.getPiusers().contains(subject)
            || group.getLabAdminsWithViewAllPermission().contains(subject);
    boolean canManageOntologies = group.getPiusers().contains(subject);
    boolean canManagePublish = group.getPiusers().contains(subject);

    model.addAttribute("group", group);
    model.addAttribute(
        "roleEditable", groupPermUtils.subjectCanAlterGroupRole(group, subject, null));
    model.addAttribute("roleGroupOwner", subject.hasRoleInGroup(group, RoleInGroup.GROUP_OWNER));
    model.addAttribute("userGroups", subject.getGroups());
    model.addAttribute(
        "canEdit", permissionUtils.isPermitted(group, PermissionType.WRITE, subject));
    model.addAttribute("canManageAutoshare", canManageAutoshare);
    model.addAttribute("canManagePublish", canManagePublish);
    model.addAttribute("canManageOntologies", canManageOntologies);
    model.addAttribute(
        "isGroupAutoshareAllowed",
        systemPropertyPermissionUtils.isPropertyAllowed(group, "group_autosharing.available"));
    setPublicationAllowed(model, subject);
    model.addAttribute("showExportFunctionality", canUserSeeHomeFoldersInGroup(subject, group));
    model.addAttribute("subject", subject);
    if (group.isProjectGroup()) {
      model.addAttribute("groupOwners", group.getGroupOwnerUsers());
    } else {
      model.addAttribute("pi", group.getPiusers().iterator().next());
    }
    List<GroupInvitation> requests = groupManager.getPendingGroupInvitationsByGroupId(groupId);
    model.addAttribute("requests", requests);

    model.addAttribute("canEditPIEditAllChoice", canPIEditAllChoiceBeChanged(subject, group));
    model.addAttribute("canHideGroupProfile", canSubjectHideGroupProfile(subject, group));

    UserGroup userGroup = group.getUserGroupForUser(subject);
    model.addAttribute("PIEditAllChoiceValue", userGroup != null && userGroup.isPiCanEditWork());

    Long folderId = group.getCommunalGroupFolderId();
    Folder folder = null;
    if (folderId != null) folder = folderManager.getFolder(folderId, subject);
    model.addAttribute("folder", folder);
    return GROUPS_VIEW_NAME;
  }

  private void setPublicationAllowed(Model model, User user) {
    model.addAttribute(
        "isGroupPublicationAllowed",
        systemPropertyPermissionManager.isPropertyAllowed(user, "public_sharing"));
    model.addAttribute(
        "isGroupSeoAllowed",
        systemPropertyPermissionManager.isPropertyAllowed(user, "publicdocs_allow_seo"));
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(user, "public_sharing"));
  }

  private boolean canPIEditAllChoiceBeChanged(User subject, Group group) {
    return group.getPiusers().contains(subject)
        && !group.isCollaborationGroup()
        && systemPropertyPermissionUtils.isPropertyAllowed(
            group, Preference.PI_CAN_EDIT_ALL_WORK_IN_LABGROUP.name());
  }

  private Object canSubjectHideGroupProfile(User subject, Group group) {
    if (properties.isProfileHidingEnabled()) {
      if (subject.isPiOrLabAdminOfGroup(group)) {
        return true;
      }
    }
    return false;
  }

  private boolean canUserSeeHomeFoldersInGroup(User user, Group group) {
    if (user.hasRoleInGroup(group, RoleInGroup.PI)) {
      return true;
    }
    if (user.hasRoleInGroup(group, RoleInGroup.RS_LAB_ADMIN)) {
      UserGroup ug = group.getUserGroupForUser(user);
      return ug.isAdminViewDocsEnabled();
    }
    return false;
  }

  /**
   * Renames an individual group
   *
   * @param model
   * @param groupId
   * @return
   */
  @ResponseBody
  @PostMapping("/rename/{groupId}")
  public AjaxReturnObject<String> renameGroup(
      Model model, @PathVariable("groupId") Long groupId, @RequestParam("newname") String newName) {
    User subject = userManager.getAuthenticatedUserInSession();
    Group group = groupManager.getGroup(groupId);
    permissionUtils.assertIsPermitted(group, PermissionType.WRITE, subject, "rename group");
    if (StringUtils.isBlank(newName)) {
      ErrorList el = ErrorList.of("Missing display name!");
      return new AjaxReturnObject<String>(null, el);
    }
    if (newName.length() > BaseRecord.DEFAULT_VARCHAR_LENGTH) {
      ErrorList el =
          ErrorList.of(
              getText(
                  "errors.maxlength", new String[] {Organisation.MAX_INDEXABLE_UTF_LENGTH + ""}));
      return new AjaxReturnObject<String>(null, el);
    }
    group.setDisplayName(newName);
    groupManager.saveGroup(group, false, subject);
    publisher.publishEvent(new GenericEvent(subject, group, AuditAction.WRITE, "renamed group"));
    model.addAttribute("group", group);
    return new AjaxReturnObject<String>("Rename ok", null);
  }

  /**
   * @param model
   * @param groupId
   * @param newProfile
   * @return
   */
  @ResponseBody
  @PostMapping("/editProfile/{groupId}")
  public AjaxReturnObject<String> editGroupProfile(
      Model model,
      @PathVariable("groupId") Long groupId,
      @RequestParam("newProfile") String newProfile) {
    Group grp = groupManager.getGroup(groupId);
    User subject = userManager.getAuthenticatedUserInSession();
    permissionUtils.assertIsPermitted(grp, PermissionType.WRITE, subject, "edit group profile");

    if (StringUtils.isBlank(newProfile)) {
      ErrorList el = ErrorList.of("Please enter some profile information.");
      return new AjaxReturnObject<>(null, el);
    }
    grp.setProfileText(newProfile);
    groupManager.saveGroup(grp, false, subject);
    model.addAttribute("group", grp);
    publisher.publishEvent(
        new GenericEvent(subject, grp, AuditAction.WRITE, "Group profile edited"));
    return new AjaxReturnObject<>("Edit profile ok", null);
  }

  /**
   * Removes a user from a group and refreshes the group view.
   *
   * @param model
   * @param groupId
   * @param userIdToRemove
   * @return
   */
  @PostMapping("/admin/removeUser/{groupId}/{id}")
  public String removeUser(
      Model model, @PathVariable("groupId") Long groupId, @PathVariable("id") Long userIdToRemove) {

    Group group = groupManager.getGroup(groupId);
    User toRemove = userManager.get(userIdToRemove);
    User subject = userManager.getAuthenticatedUserInSession();
    if (group.isOnlyGroupPi(toRemove.getUsername())) {
      model.addAttribute(ERROR_ATTRIBUTE_NAME, getText("group.edit.mustbe1.admin.error.msg"));
      model.addAttribute("group", group);
      return GROUPS_VIEW_NAME;
    }
    if (group.isOnlyGroupOwner(toRemove.getUsername())) {
      model.addAttribute(ERROR_ATTRIBUTE_NAME, getText("group.edit.mustbe1.groupowner.error.msg"));
      model.addAttribute("group", group);
      return GROUPS_VIEW_NAME;
    }

    group = groupManager.removeUserFromGroup(toRemove.getUsername(), groupId, subject);
    publisher.publishEvent(
        new GenericEvent(
            subject, group, AuditAction.WRITE, "Removed [" + toRemove.getUsername() + "]."));
    return "redirect:/groups/view/" + groupId;
  }

  /**
   * Removes a user from a group and refreshes the group view. <em>Only applicable to Community
   * RSPAC1662
   *
   * @param model
   * @param groupId
   * @return true if can reload group, false otherwise (e.g. if group is private)
   */
  @PostMapping("/admin/removeSelf/{groupId}")
  public @ResponseBody AjaxReturnObject<Boolean> removeSelfFromGroup(
      Model model, @PathVariable("groupId") Long groupId) {

    Group group = groupManager.getGroup(groupId);

    User subjectToRemove = userManager.getAuthenticatedUserInSession();
    if (group.isOnlyGroupPi(subjectToRemove.getUsername())) {
      String msg = getText("group.edit.mustbe1.admin.error.msg");
      return new AjaxReturnObject<Boolean>(null, ErrorList.of(msg));
    }

    if (group.isProjectGroup() && group.isOnlyGroupOwner(subjectToRemove.getUsername())) {
      String msg = getText("group.edit.mustbe1.groupowner.error.msg");
      return new AjaxReturnObject<Boolean>(null, ErrorList.of(msg));
    }

    if (!group.getMembers().contains(subjectToRemove)) {
      return new AjaxReturnObject<Boolean>(
          null, ErrorList.of("You are not a member of the group."));
    }

    group =
        groupManager.removeUserFromGroup(subjectToRemove.getUsername(), groupId, subjectToRemove);
    publisher.publishEvent(
        new GenericEvent(
            subjectToRemove,
            group,
            AuditAction.WRITE,
            "Removed [" + subjectToRemove.getUsername() + "]."));
    return new AjaxReturnObject<Boolean>(group.isPrivateProfile() ? FALSE : TRUE, null);
  }

  /**
   * Gets list of all groups in JSON syntax
   *
   * @param pgCrit
   * @return
   */
  @ResponseBody
  @GetMapping("/ajax/admin/listAll")
  public AjaxReturnObject<List<GroupListResult>> listAllGroups(PaginationCriteria<Group> pgCrit) {
    pgCrit.setClazz(Group.class);
    pgCrit.setGetAllResults();
    User subject = userManager.getAuthenticatedUserInSession();

    ISearchResults<Group> allGrps = doGroupList(subject, pgCrit, pgCrit.getPageNumber().intValue());
    List<GroupListResult> groupDisplay = new ArrayList<GroupListResult>();
    for (Group group : allGrps.getResults()) {
      GroupListResult r = new GroupListResult(group.getId(), group.getDisplayName());
      groupDisplay.add(r);
    }
    return new AjaxReturnObject<>(groupDisplay, null);
  }

  private ISearchResults<Group> doGroupList(
      User subject, PaginationCriteria<Group> pgCrit, int pageNum) {
    if (pgCrit.getSearchCriteria() == null) {
      pgCrit.setSearchCriteria(new GroupSearchCriteria());
    }

    GroupSearchCriteria glf = (GroupSearchCriteria) pgCrit.getSearchCriteria();
    glf.setLoadCommunity(true);
    glf.setFilterByCommunity(true);
    pgCrit.setSearchCriteria(glf);

    ISearchResults<Group> grps = groupManager.list(subject, pgCrit);
    return grps;
  }

  /**
   * Remove group.
   *
   * @param groupId
   * @return
   */
  @PostMapping("/admin/removeGroup/{grpid}")
  public String removeGroup(@PathVariable("grpid") Long groupId) {

    Session session = SecurityUtils.getSubject().getSession();
    User subject = userManager.getAuthenticatedUserInSession();
    // TODO does it mean community admin can remove any group?
    if (!subject.hasRole(Role.ADMIN_ROLE, Role.SYSTEM_ROLE)) {
      throw new AuthorizationException(
          "Unauthorized attempt to delete group by [" + subject.getFullName() + "]");
    }
    Set<User> users = groupManager.getGroup(groupId).getMembers();
    Group grp = groupManager.removeGroup(groupId, subject);
    for (User u : users) {
      updateUserSessionObject(session, subject, u.getUniqueName());
    }
    publisher.publishEvent(new GenericEvent(subject, grp, AuditAction.DELETE));
    return "redirect:/system/groups/list";
  }

  /**
   * Remove lab group from collaboration group.
   *
   * @param groupId
   * @return
   */
  @PostMapping("/admin/removeLabGrpFromCollabGroup/{grpid}")
  public String removeLabGroupFromCollabGroup(@PathVariable("grpid") Long groupId) {
    User u = userManager.getAuthenticatedUserInSession();
    groupManager.removeLabGroupMembersFromCollabGroup(groupId, u);
    return "redirect:/userform";
  }

  /**
   * Invites a user to a group and returns operation status
   *
   * @param principal
   * @param group
   * @param errors
   * @return msg
   */
  @ResponseBody
  @PostMapping("/admin/addUser")
  public String addUser(
      Principal principal, @ModelAttribute("group") Group group, BindingResult errors) {
    if (group.getId() == null) {
      log.error("Group ID  was null for group {} ", group.toString());
      return "Could not issue invitation as request was lacking a group ID."
          + "This is a server error, please report it quoting RSPAC-1127";
    }
    if (group.getMemberString() == null || group.getMemberString().isEmpty()) {
      errors.rejectValue("memberString", GroupValidator.GROUP_MEMBERS_NONESELECTED);
      return "The request couldn't be processed (members string empty)";
    }

    User subject = userManager.getAuthenticatedUserInSession();
    List<User> usersToAddOrInvite = new ArrayList<>();
    for (String user : group.getMemberString()) {
      User u = userManager.getUserByUsername(user);
      if (!u.isContentInitialized()) {
        init.init(u.getId());
      }
      usersToAddOrInvite.add(u);
    }

    if (subject.hasAdminRole() && !GroupType.COLLABORATION_GROUP.equals(group.getGroupType())) {
      /* if current user is admin/sysadmin, add users directly */
      List<String> failedAutoshares = new ArrayList<>();

      for (User userToAdd : usersToAddOrInvite) {
        group =
            groupManager.addUserToGroup(
                userToAdd.getUsername(), group.getId(), RoleInGroup.DEFAULT);

        try {
          if (!userToAdd.isPI() && group.isAutoshareEnabled()) {
            userToAdd = userManager.getUserByUsername(userToAdd.getUsername(), true);
            group = groupManager.enableAutoshareForUser(userToAdd, group.getId());
            Folder autoshareFolder = groupManager.createAutoshareFolder(userToAdd, group);
            User updatedUserToAdd = userManager.getUserByUsername(userToAdd.getUsername(), true);
            autoshareManager.asyncBulkShareAllRecords(updatedUserToAdd, group, autoshareFolder);
          }
        } catch (Exception e) {
          log.error("Failed to enable autoshare for user {}: ", userToAdd.getId(), e);
          failedAutoshares.add(userToAdd.getUsername());
        }
      }

      if (failedAutoshares.size() > 0) {
        return "Users added, however, failed to enable autosharing for users "
            + String.join(", ", failedAutoshares)
            + ".";
      } else {
        return "Users added to the group.";
      }
    }

    /* if current user is pi/lab admin, or it's a collaboration group,
     * then go through invitation flow. */
    MsgOrReqstCreationCfg cgf = new MsgOrReqstCreationCfg(subject, permissionUtils);
    cgf.setGroupId(group.getId());
    cgf.setMessageType(
        group.isProjectGroup()
            ? MessageType.REQUEST_JOIN_PROJECT_GROUP
            : MessageType.REQUEST_JOIN_LAB_GROUP);
    MessageOrRequest request =
        requestCreateMgr.createRequest(
            cgf, principal.getName(), new HashSet<String>(group.getMemberString()), null, null);
    String msg = "Invitation sent to " + StringUtils.join(group.getMemberString(), ",");
    publisher.publishEvent(new GenericEvent(subject, request, AuditAction.CREATE, msg));
    return msg;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RoleUpdateInfo {
    private Boolean isAuthorized;
    private String roleText;
  }

  /**
   * Changes the role of a member of the group, if allowed
   *
   * @param groupId
   * @param userid
   * @param role
   * @param isAuthorized default=false
   * @return
   * @throws AuthorizationException if user can't alter group role
   */
  @PostMapping("/ajax/admin/changeRole/{groupid}/{userid}")
  public @ResponseBody AjaxReturnObject<RoleUpdateInfo> changeRole(
      @PathVariable("groupid") long groupId,
      @PathVariable("userid") Long userid,
      @RequestParam("role") String role,
      @RequestParam(value = "isAuthorized", required = false, defaultValue = "false")
          Boolean isAuthorized) {

    Group grp = groupManager.getGroup(groupId);
    RoleInGroup roleInGrp = RoleInGroup.getRoleFromString(role);
    if (roleInGrp == null || roleInGrp.equals(RoleInGroup.PI)) {
      ErrorList el = ErrorList.of(getText("group.edit.invalidrolename.error.msg"));
      return new AjaxReturnObject<>(null, el);
    }

    User subject = userManager.getAuthenticatedUserInSession();
    User toChange = userManager.get(userid);
    // if user doesn't have edit permission on this group and doesn't have correct role
    if (!groupPermUtils.subjectCanAlterGroupRole(grp, subject, toChange)) {
      throw new AuthorizationException(
          authGenerator.getFailedMessage(
              subject,
              String.format(
                  " change %s 's role in group %s. ",
                  toChange.getUsername(), grp.getDisplayName())));
    }

    // can't remove last PI
    if (grp.isOnlyGroupPi(toChange.getUsername())) {
      ErrorList el = ErrorList.of(getText("group.edit.mustbe1.admin.error.msg"));
      return new AjaxReturnObject<>(null, el);
    }

    // can't remove last group owner
    if (grp.isOnlyGroupOwner(toChange.getUsername())) {
      ErrorList el = ErrorList.of(getText("group.edit.mustbe1.groupowner.error.msg"));
      return new AjaxReturnObject<>(null, el);
    }

    groupManager.setRoleForUser(groupId, toChange.getId(), role, subject);

    // this only affects people with lab admin role
    if (roleInGrp.equals(RoleInGroup.RS_LAB_ADMIN)) {
      groupManager.authorizeLabAdminToViewAll(toChange.getId(), subject, groupId, isAuthorized);
    }

    publisher.publishEvent(
        new GenericEvent(
            subject,
            grp,
            AuditAction.WRITE,
            toChange.getUsername() + "'s role changed to " + role + "."));
    grp = groupManager.getGroup(groupId);
    UserGroup ug = grp.getUserGroupForUser(toChange);
    return new AjaxReturnObject<>(
        new RoleUpdateInfo(ug.isAdminViewDocsEnabled(), ug.getRoleInGroup().getLabel()), null);
  }

  @PostMapping("/ajax/admin/changePiCanEditAll/{groupid}")
  public @ResponseBody AjaxReturnObject<String> changePICanEditAll(
      @PathVariable("groupid") long groupId, @RequestParam("canPIEditAll") Boolean canPIEditAll) {
    Group group = groupManager.getGroupWithCommunities(groupId);
    User subject = userManager.getAuthenticatedUserInSession();

    if (!canPIEditAllChoiceBeChanged(subject, group)) {
      throw new AuthorizationException(
          authGenerator.getFailedMessage(
              subject,
              String.format(
                  " change PI can edit all work in lab group setting in group %s. ",
                  group.getDisplayName())));
    }

    groupManager.authorizePIToEditAll(groupId, subject, canPIEditAll);

    publisher.publishEvent(
        new GenericEvent(
            subject,
            group,
            AuditAction.WRITE,
            group.getDisplayName() + "'s PIs can edit all work was set to " + canPIEditAll + "."));
    group = groupManager.getGroup(groupId);
    return new AjaxReturnObject<>(
        String.valueOf(group.getUserGroupForUser(subject).isPiCanEditWork()), null);
  }

  @PostMapping("/ajax/admin/changeHideProfileSetting/{groupid}")
  public @ResponseBody AjaxReturnObject<String> changeHideProfileSetting(
      @PathVariable("groupid") long groupId, @RequestParam("hideProfile") Boolean hideProfile) {

    Group group = groupManager.getGroupWithCommunities(groupId);
    User subject = userManager.getAuthenticatedUserInSession();

    group = groupManager.hideGroupProfile(hideProfile, group, subject);
    publisher.publishEvent(
        new GenericEvent(
            subject,
            group,
            AuditAction.WRITE,
            group.getDisplayName()
                + "'s profile visibility was changed, hideProfile is now "
                + group.isPrivateProfile()
                + "."));
    return new AjaxReturnObject<>(String.valueOf(group.isPrivateProfile()), null);
  }

  private void updateUserSessionObject(Session session, User sessionUser, String user) {
    // updated session version with new version
    if (sessionUser != null && sessionUser.getUsername().equals(user)) {
      session.setAttribute(SessionAttributeUtils.USER, userManager.get(sessionUser.getId()));
    }
  }

  @PostMapping("/admin/ajax/authorizeAdmin")
  public @ResponseBody AjaxReturnObject<Boolean> authoriseLabAdminToViewAll(
      @RequestParam Long groupId,
      @RequestParam Long labAdminId,
      @RequestParam boolean isAuthorized) {

    User subject = userManager.getAuthenticatedUserInSession();
    groupManager.authorizeLabAdminToViewAll(labAdminId, subject, groupId, isAuthorized);
    return new AjaxReturnObject<Boolean>(true, null);
  }

  @PostMapping("/ajax/admin/swapPi/{groupid}")
  public @ResponseBody AjaxReturnObject<Boolean> swapPi(
      @PathVariable("groupid") long groupId, @RequestParam("newPiId") Long newPiId) {
    User admin = userManager.getAuthenticatedUserInSession();

    Group grp = groupManager.getGroup(groupId);
    User newPI = userManager.get(newPiId);
    userPermUtils.assertHasPermissionsOnTargetUser(admin, newPI, "Swap group Pis");
    BindingResult result =
        inputValidator.validate(
            new SwapPiCommand(grp.getPiusers().iterator().next(), newPI, grp),
            new SwapPiValidator());
    if (result.hasErrors()) {
      ErrorList el = new ErrorList();
      inputValidator.populateErrorList(result, el);
      return new AjaxReturnObject<Boolean>(null, el);
    }

    groupManager.setNewPi(groupId, newPI.getId(), admin);
    //	commsMgr.systemNotify(notificationType, msg, recipientName, broadcast)
    return new AjaxReturnObject<Boolean>(true, null);
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @EqualsAndHashCode(of = "username")
  public static class UserInviteView {
    UserInviteView(UserView uv) {
      this.displayName = uv.getFullName();
      this.username = uv.getUniqueName();
    }

    UserInviteView(User user) {
      this.displayName = user.getFullName();
      this.username = user.getUniqueName();
    }

    private String displayName;
    private String username;
  }

  @GetMapping("/ajax/invitableUsers/{groupId}")
  @Product(value = {ProductType.SSO, ProductType.STANDALONE})
  public ResponseEntity<AjaxReturnObject<List<UserInviteView>>> getInvitableUsers(
      @PathVariable Long groupId) {
    User subject = userManager.getAuthenticatedUserInSession();
    Group group = groupManager.getGroup(groupId);
    boolean canEdit = permissionUtils.isPermitted(group, PermissionType.WRITE, subject);
    if (!canEdit) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(createEmptyResult(subject));
    } else {
      if (GroupType.COLLABORATION_GROUP.equals(group.getGroupType())) {
        List<UserInviteView> members =
            groupManager.getCandidateMembersOfCollabGroup(group.getId()).stream()
                .map(UserInviteView::new)
                .sorted(this::compareByDisplayNameAsCaseInsensitive)
                .collect(toCollection(ArrayList::new));
        return ResponseEntity.ok(new AjaxReturnObject<List<UserInviteView>>(members, null));

      } else {
        Set<UserInviteView> existingGroupMembers =
            group.getMembers().stream().map(UserInviteView::new).collect(Collectors.toSet());
        List<UserInviteView> all =
            userManager.getAllUsersView().stream()
                .map(UserInviteView::new)
                .sorted(this::compareByDisplayNameAsCaseInsensitive)
                .collect(toCollection(ArrayList::new));
        all.removeAll(existingGroupMembers);
        return ResponseEntity.ok(new AjaxReturnObject<List<UserInviteView>>(all, null));
      }
    }
  }

  // view object for group membership event views
  @Data
  @NoArgsConstructor
  public static class GroupEvent {
    private String userFullName, userEmail;
    private String groupName, eventType;
    private Long userId, groupId, eventId;

    @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
    @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
    private Long timestamp;

    GroupEvent(GroupMembershipEvent event) {
      this.userEmail = event.getUser().getEmail();
      this.userFullName = event.getUser().getFullName();
      this.userId = event.getUser().getId();
      this.groupName = event.getGroup().getDisplayName();
      this.eventId = event.getId();
      this.groupId = event.getGroup().getId();
      this.eventType = event.getGroupEventType().toString();
      this.timestamp = event.getTimestamp().getTime();
    }
  }

  /**
   * Ajax call to get join/leave group events for all members and ex-members of a group
   *
   * @param groupId
   * @return AjaxReturnObject<List<GroupEvent>>, can be empty list if no events
   */
  @GetMapping("/ajax/membershipEventsByGroup/{groupId}")
  @ResponseBody
  public AjaxReturnObject<List<GroupEvent>> getMembershipEventsForGroup(
      @PathVariable("groupId") Long groupId) {
    Group group = groupManager.getGroup(groupId);
    User subject = userManager.getAuthenticatedUserInSession();
    List<GroupMembershipEvent> events = groupManager.getGroupEventsForGroup(subject, group);
    List<GroupEvent> rc = events.stream().map(GroupEvent::new).collect(toList());
    return new AjaxReturnObject<>(rc, null);
  }

  /**
   * Ajax call to get join/leave group events for a user
   *
   * @param userId
   * @return AjaxReturnObject<List<GroupEvent>>, can be empty list if no events
   */
  @GetMapping("/ajax/membershipEventsByUser/{userId}")
  @ResponseBody
  public AjaxReturnObject<List<GroupEvent>> getMembershipEventsForUser(
      @PathVariable("userId") Long userId) {
    User profileUser = userManager.get(userId);
    User subject = userManager.getAuthenticatedUserInSession();
    List<GroupMembershipEvent> events = groupManager.getGroupEventsForUser(subject, profileUser);
    List<GroupEvent> rc = events.stream().map(GroupEvent::new).collect(toList());
    return new AjaxReturnObject<>(rc, null);
  }

  private int compareByDisplayNameAsCaseInsensitive(UserInviteView ui1, UserInviteView ui2) {
    return ui1.getDisplayName().toLowerCase().compareTo(ui2.getDisplayName().toLowerCase());
  }

  private AjaxReturnObject<List<UserInviteView>> createEmptyResult(User subject) {
    return new AjaxReturnObject<>(
        null, ErrorList.of(authGenerator.getFailedMessage(subject, "list all Users")));
  }

  /** Is autoshare enabled at the group level */
  @GetMapping("/ajax/autoshareStatus/{groupId}")
  @ResponseBody
  public AjaxReturnObject<Boolean> autoshareStatus(@PathVariable Long groupId) {
    return new AjaxReturnObject<>(groupManager.getGroup(groupId).isAutoshareEnabled(), null);
  }

  /** Is publication of own docs enabled at the group level */
  @GetMapping("/ajax/publishAllowedStatus/{groupId}")
  @ResponseBody
  public AjaxReturnObject<Boolean> publishAllowedStatus(@PathVariable Long groupId) {
    return new AjaxReturnObject<>(groupManager.getGroup(groupId).isPublicationAllowed(), null);
  }

  /** Is public SEO enabled at the group level */
  @GetMapping("/ajax/seoAllowedStatus/{groupId}")
  @ResponseBody
  public AjaxReturnObject<Boolean> seoAllowedStatus(@PathVariable Long groupId) {
    return new AjaxReturnObject<>(groupManager.getGroup(groupId).isSeoAllowed(), null);
  }

  /** Are ontologies enforced at the group level */
  @GetMapping("/ajax/ontologiesEnforcedStatus/{groupId}")
  @ResponseBody
  public AjaxReturnObject<Boolean> ontologiesEnforcedStatus(@PathVariable Long groupId) {
    return new AjaxReturnObject<>(groupManager.getGroup(groupId).isEnforceOntologies(), null);
  }

  /** Are bio-ontologies allowed at the group level */
  @GetMapping("/ajax/bioOntologiesAllowedStatus/{groupId}")
  @ResponseBody
  public AjaxReturnObject<Boolean> bioOntologiesAllowedStatus(@PathVariable Long groupId) {
    return new AjaxReturnObject<>(groupManager.getGroup(groupId).isAllowBioOntologies(), null);
  }

  /** Disable enforced ontologies at the group level */
  @PostMapping("/ajax/unenforceGroupOntologies/{groupId}")
  @ResponseBody
  public AjaxReturnObject<String> disableGroupOntologiesEnforced(@PathVariable Long groupId) {
    return setGroupOntologiesEnforced(groupId, false);
  }

  /** Enable enforced ontologies at the group level */
  @PostMapping("/ajax/enforceGroupOntologies/{groupId}")
  @ResponseBody
  public AjaxReturnObject<String> enableGroupOntologiesEnforced(@PathVariable Long groupId) {
    return setGroupOntologiesEnforced(groupId, true);
  }

  /** Disallow BioOntologies at the group level */
  @PostMapping("/ajax/disallowBioOntologies/{groupId}")
  @ResponseBody
  public AjaxReturnObject<String> disallowGroupBioOntologies(@PathVariable Long groupId) {
    return setGroupBioOntologiesAllowed(groupId, false);
  }

  /** Allow BioOntologies at the group level */
  @PostMapping("/ajax/allowBioOntologies/{groupId}")
  @ResponseBody
  public AjaxReturnObject<String> allowGroupBioOntologies(@PathVariable Long groupId) {
    return setGroupBioOntologiesAllowed(groupId, true);
  }

  @Data
  public static class AutoshareMemberStatus {
    private final Long userId;
    private final String username;
    private final Boolean isPI;
    private final Boolean autoshareEnabled;
    private final Boolean isAutoshareInProgress;

    public AutoshareMemberStatus(UserGroup ug, AutoshareManager autoshareManager) {
      this.userId = ug.getUser().getId();
      this.username = ug.getUser().getUsername();
      this.isPI = ug.getUser().isPI();
      this.autoshareEnabled = ug.isAutoshareEnabled();
      this.isAutoshareInProgress = autoshareManager.isBulkShareInProgress(ug.getUser());
    }
  }

  /** Get autoshare status for all members of the group */
  @GetMapping("/ajax/autoshareMemberStatus/{groupId}")
  @ResponseBody
  public AjaxReturnObject<List<AutoshareMemberStatus>> autoshareMemberStatus(
      @PathVariable Long groupId) {

    Group group = groupManager.getGroup(groupId);

    List<AutoshareMemberStatus> statuses = new ArrayList<>();
    for (UserGroup ug : group.getUserGroups()) {
      statuses.add(new AutoshareMemberStatus(ug, autoshareManager));
    }

    return new AjaxReturnObject<>(statuses, null);
  }

  /** Get autoshare status for a member of the group */
  @GetMapping("/ajax/autoshareMemberStatus/{groupId}/{userId}")
  @ResponseBody
  public AjaxReturnObject<AutoshareMemberStatus> autoshareMemberStatus(
      @PathVariable Long groupId, @PathVariable Long userId) {

    Group group = groupManager.getGroup(groupId);
    User subject = userManager.get(userId);
    UserGroup ug = group.getUserGroupForUser(subject);

    return new AjaxReturnObject<>(new AutoshareMemberStatus(ug, autoshareManager), null);
  }

  /** Enable autoshare at the group level */
  @PostMapping("/ajax/enableAutoshare/{groupId}")
  @ResponseBody
  public AjaxReturnObject<String> enableAutoshare(@PathVariable Long groupId) {
    return setAutoshare(groupId, true);
  }

  /** Disable autoshare at the group level */
  @PostMapping("/ajax/disableAutoshare/{groupId}")
  @ResponseBody
  public AjaxReturnObject<String> disableAutoshare(@PathVariable Long groupId) {
    return setAutoshare(groupId, false);
  }

  private AjaxReturnObject<String> setAutoshare(Long groupId, boolean targetAutoshareStatus) {
    User subject = userManager.getAuthenticatedUserInSession();
    Group group = groupManager.getGroupWithCommunities(groupId);

    String errorMessage = canManageAutoshareForGroup(group, subject, targetAutoshareStatus);
    if (errorMessage != null) {
      return new AjaxReturnObject<>(null, ErrorList.of(errorMessage));
    }

    // This line shouldn't be in the runEnableAutoshareTask to prevent
    // queuing up multiple tasks that modify the same group
    groupsWithAutoshareInProgress.put(group.getId(), targetAutoshareStatus);

    group.setAutoshareEnabled(targetAutoshareStatus);
    group = groupManager.saveGroup(group, false, subject);

    autoshareManager.logGroupAutoshareStatusChange(group, subject, targetAutoshareStatus);
    runEnableAutoshareTask(group, subject, targetAutoshareStatus);

    return new AjaxReturnObject<>("", null);
  }

  /** Enable publication SEO at the group level */
  @PostMapping("/ajax/allowGroupSeo/{groupId}")
  @ResponseBody
  public AjaxReturnObject<String> allowGroupSeo(@PathVariable Long groupId) {
    return setGroupSeo(groupId, true);
  }

  /** Disable publication SEO at the group level */
  @PostMapping("/ajax/disableGroupSeo/{groupId}")
  @ResponseBody
  public AjaxReturnObject<String> disableGroupSeo(@PathVariable Long groupId) {
    return setGroupSeo(groupId, false);
  }

  /** Enable publication at the group level */
  @PostMapping("/ajax/allowGroupPublications/{groupId}")
  @ResponseBody
  public AjaxReturnObject<String> allowGroupPublications(@PathVariable Long groupId) {
    return setGroupPublication(groupId, true);
  }

  /** Disable publication at the group level */
  @PostMapping("/ajax/disableGroupPublications/{groupId}")
  @ResponseBody
  public AjaxReturnObject<String> disableGroupPublications(@PathVariable Long groupId) {
    return setGroupPublication(groupId, false);
  }

  private AjaxReturnObject<String> setGroupPublication(Long groupId, boolean publicationAllowed) {
    User subject = userManager.getAuthenticatedUserInSession();
    Group group = groupManager.getGroupWithCommunities(groupId);
    if (!subject.hasRoleInGroup(group, RoleInGroup.PI)) {
      return new AjaxReturnObject<>(null, ErrorList.of("Only PI can allow group publication"));
    }
    group.setPublicationAllowed(publicationAllowed);
    groupManager.saveGroup(group, false, subject);
    return new AjaxReturnObject<>("", null);
  }

  private AjaxReturnObject<String> setGroupOntologiesEnforced(
      Long groupId, boolean enforceOntologies) {
    User subject = userManager.getAuthenticatedUserInSession();
    Group group = groupManager.getGroupWithCommunities(groupId);
    if (!subject.hasRoleInGroup(group, RoleInGroup.PI)) {
      return new AjaxReturnObject<>(null, ErrorList.of("Only PI can enforce group ontologies"));
    }
    group.setEnforceOntologies(enforceOntologies);
    groupManager.saveGroup(group, false, subject);
    return new AjaxReturnObject<>("", null);
  }

  private AjaxReturnObject<String> setGroupBioOntologiesAllowed(Long groupId, boolean allow) {
    User subject = userManager.getAuthenticatedUserInSession();
    Group group = groupManager.getGroupWithCommunities(groupId);
    if (!subject.hasRoleInGroup(group, RoleInGroup.PI)) {
      return new AjaxReturnObject<>(
          null, ErrorList.of("Only PI can allow group to use Bio Ontologies"));
    }
    group.setAllowBioOntologies(allow);
    groupManager.saveGroup(group, false, subject);
    return new AjaxReturnObject<>("", null);
  }

  private AjaxReturnObject<String> setGroupSeo(Long groupId, boolean seoAllowed) {
    User subject = userManager.getAuthenticatedUserInSession();
    Group group = groupManager.getGroupWithCommunities(groupId);
    if (!subject.hasRoleInGroup(group, RoleInGroup.PI)) {
      return new AjaxReturnObject<>(null, ErrorList.of("Only PI can allow group publication"));
    }
    group.setSeoAllowed(seoAllowed);
    groupManager.saveGroup(group, false, subject);
    return new AjaxReturnObject<>("", null);
  }

  /**
   * @return null if can manage, error message otherwise
   */
  private String canManageAutoshareForGroup(Group group, User user, boolean targetAutoshareStatus) {
    if (!systemPropertyPermissionUtils.isPropertyAllowed(group, "group_autosharing.available")) {
      return "Please contact your system administrator to enable this feature";
    } else if (!group.isLabGroup()) {
      return "Can only manage autosharing for lab groups";
    } else if (properties.isCloud()) {
      return "Group autosharing is only available on the Enterprise version of RSpace";
    } else if (group.isAutoshareEnabled() == targetAutoshareStatus) {
      return "Autoshare is already set to the desired state";
    } else if (groupsWithAutoshareInProgress.containsKey(group.getId())) {
      return "Cannot launch new bulk share operation; already running";
    }

    boolean isLabAdmin = group.getLabAdminsWithViewAllPermission().contains(user);
    boolean isPi = group.getPiusers().contains(user);
    if (isPi || isLabAdmin) {
      return null;
    }

    Community community = communityMgr.getWithAdmins(group.getCommunityId());
    if (user.hasSysadminRole() || community.getAdmins().contains(user)) {
      return "Sysadmins cannot manage group-wide autoshare status";
    }

    return "Forbidden";
  }

  /**
   * The functionality in this method is located here instead of AutoshareManager due to
   * asyncBulkShare depending on enableAutoshareForUser transaction completion
   */
  @Async(value = "archiveTaskExecutor")
  void runEnableAutoshareTask(Group group, User subject, Boolean targetAutoshareStatus) {
    try {
      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result;
      // Enable/Disable autoshare for each user,
      // skip users that already have the desired autoshare status
      for (User member : group.getMembers()) {

        if (group.getUserGroupForUser(member).isAutoshareEnabled() == targetAutoshareStatus) {
          continue;
        }

        if (!member.isPI()) {
          if (targetAutoshareStatus) {
            group = groupManager.enableAutoshareForUser(member, group.getId());
            Folder autoshareFolder = groupManager.createAutoshareFolder(member, group);
            User updatedMember = userManager.getUserByUsername(member.getUsername(), true);
            result = autoshareManager.bulkShareAllRecords(updatedMember, group, autoshareFolder);
          } else {
            group = groupManager.disableAutoshareForUser(member, group.getId());
            User updatedMember = userManager.getUserByUsername(member.getUsername(), true);
            result = autoshareManager.bulkUnshareAllRecords(updatedMember, group);
          }

          group = groupManager.getGroup(group.getId());

          String notificationMessage =
              autoshareManager.createNotificationMessage(targetAutoshareStatus, result);
          communicationManager.systemNotify(
              NotificationType.PROCESS_COMPLETED, notificationMessage, member.getUsername(), true);
        }
      }

      communicationManager.systemNotify(
          NotificationType.PROCESS_COMPLETED,
          "Group-wide autoshare status change has been completed.",
          subject.getUsername(),
          true);
    } catch (Exception e) {
      communicationManager.systemNotify(
          NotificationType.PROCESS_FAILED,
          "Group-wide autoshare status has been changed. However, changing "
              + "the autoshare status for some of the users has failed. Please "
              + "contact RSpace for support.",
          subject.getUsername(),
          true);
      throw (e);
    } finally {
      groupsWithAutoshareInProgress.remove(group.getId());
    }
  }
}

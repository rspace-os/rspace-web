package com.researchspace.webapp.controller;

import com.researchspace.core.util.DefaultURLPaginator;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.core.util.PaginationUtil;
import com.researchspace.model.Community;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for admin of communities by sysadmin/admins. Requires these roles to access these
 * URLs, as specified in security.xml
 */
@Controller
@RequestMapping("/community/admin")
public class CommunityAdminController extends BaseController {

  static final String REDIRECT_SYSTEM_COMMUNITY = "redirect:/system/community/";

  private CommunityServiceManager communityService;
  private @Autowired SystemPropertyManager systemPropertyManager;
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  @Autowired
  void setCommunityServiceManager(CommunityServiceManager communityService) {
    this.communityService = communityService;
  }

  /**
   * Removes a group from its current community and moves it to the default community
   *
   * @param groupIds
   * @param communityId
   * @return An {@link AjaxReturnObject} with the id of the community from which the group was
   *     removed.
   */
  @PostMapping("/ajax/remove")
  @ResponseBody
  public AjaxReturnObject<Long> removeGroup(
      @RequestParam("ids[]") Long[] groupIds, @RequestParam("communityId") Long communityId) {
    User subject = userManager.getAuthenticatedUserInSession();
    if (!permissionUtils.isPermitted("COMMUNITY:WRITE:id=" + communityId)) {
      throwAuthorisationException(communityId, subject, PermissionType.WRITE);
    }
    if (Community.DEFAULT_COMMUNITY_ID.equals(communityId)) {
      ErrorList errors = ErrorList.of(getText("community.removeFromDefaultProhibited.msg"));
      return new AjaxReturnObject<Long>(null, errors);
    }
    for (Long id : groupIds) {
      communityService.addGroupToCommunity(id, Community.DEFAULT_COMMUNITY_ID, subject);
    }
    return new AjaxReturnObject<Long>(communityId, null);
  }

  /**
   * Moves a group from its current community and moves it to the target community
   *
   * @param groupIds
   * @param cmmunityIdFrom
   * @param cmmunityIdTo
   * @return An {@link AjaxReturnObject} with the id of the target.
   */
  @PostMapping("/ajax/move")
  @ResponseBody
  public AjaxReturnObject<Long> moveGroup(
      @RequestParam("ids") Long[] groupIds,
      @RequestParam("from") Long cmmunityIdFrom,
      @RequestParam("to") Long cmmunityIdTo) {
    if (cmmunityIdFrom.equals(cmmunityIdTo)) {
      ErrorList errors = ErrorList.of(getText("community.moveSrcTargetSame.msg"));
      return new AjaxReturnObject<Long>(null, errors);
    }
    User user = userManager.getAuthenticatedUserInSession();
    for (Long groupId : groupIds) {
      communityService.addGroupToCommunity(groupId, cmmunityIdTo, user);
    }
    return new AjaxReturnObject<>(cmmunityIdTo, null);
  }

  @GetMapping("/ajax/listMoveTargets")
  public String listMoveTargets(
      Model model,
      @RequestParam("from") Long cmmunityIdFrom,
      PaginationCriteria<Community> pgCrit) {

    User subject = userManager.getAuthenticatedUserInSession();
    pgCrit.setResultsPerPage(Integer.MAX_VALUE); // assume there won't be lots of communities
    doListing(subject, model, pgCrit);
    return "system/communityList_ajax";
  }

  @GetMapping("/list")
  public ModelAndView listCommunities(Model model, PaginationCriteria<Community> pgCrit) {
    User userInSession = userManager.getAuthenticatedUserInSession();
    doListing(userInSession, model, pgCrit);
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(userInSession, "public_sharing"));
    return new ModelAndView("system/communityList");
  }

  @GetMapping("/ajax/list")
  public String listCommunitiesAjax(
      Model model,
      PaginationCriteria<Community> pgCrit,
      @RequestParam(value = "viewType", defaultValue = "full", required = false) String viewType) {
    User subject = userManager.getAuthenticatedUserInSession();

    if ("moveCommunity".equals(viewType)) {
      pgCrit.setResultsPerPage(Integer.MAX_VALUE); // get all communities
      doListing(subject, model, pgCrit);
      return "system/moveCommunityTo_ajax";
    } else {
      doListing(subject, model, pgCrit);
      return "system/communityList_ajax";
    }
  }

  private ISearchResults<Community> doListing(
      User subject, Model model, PaginationCriteria<Community> pgCrit) {
    ISearchResults<Community> res = communityService.listCommunities(subject, pgCrit);
    model.addAttribute("communities", res);
    List<PaginationObject> po =
        PaginationUtil.generatePagination(
            res.getTotalPages(),
            res.getPageNumber(),
            new DefaultURLPaginator("/community/admin/ajax/list", pgCrit));
    model.addAttribute("paginationList", po);
    return res;
  }

  @PostMapping("/edit")
  public String editCommunity(Model model, @ModelAttribute Community community, BindingResult br) {

    User authUser = userManager.getAuthenticatedUserInSession();
    ValidationUtils.rejectIfEmptyOrWhitespace(
        br, "displayName", "errors.required", new Object[] {"Display name"});
    if (br.hasErrors()) {
      model.addAttribute("view", false);
      return "system/community";
    }
    Community comm2 = communityService.get(community.getId());
    if (comm2 != null && !permissionUtils.isPermitted(comm2, PermissionType.WRITE, authUser)) {
      throwAuthorisationException(comm2.getId(), authUser, PermissionType.WRITE);
    }
    if (comm2 != null) {
      comm2.setDisplayName(community.getDisplayName());
      comm2.setProfileText(community.getProfileText());
    }
    communityService.save(comm2);
    return REDIRECT_SYSTEM_COMMUNITY + community.getId();
  }

  private void throwAuthorisationException(Long id, User subject, PermissionType permType) {
    throw new AuthorizationException(
        getText(
            "authorisation.failed",
            new Object[] {"Community", id, subject.getFullName(), permType}));
  }

  @PostMapping("/addAdmin")
  public String addAdmin(@ModelAttribute Community community) {
    User subject = userManager.getAuthenticatedUserInSession();
    assertWritePermission(community, subject);

    if (community.getAdminIds() == null || community.getAdminIds().isEmpty()) {
      return REDIRECT_SYSTEM_COMMUNITY + community.getId();
    }
    Long[] adminids = new Long[community.getAdminIds().size()];
    communityService.addAdminsToCommunity(
        community.getAdminIds().toArray(adminids), community.getId());
    publisher.publishEvent(
        new GenericEvent(
            subject,
            community,
            AuditAction.WRITE,
            "added admins (" + StringUtils.join(adminids, ",") + ")"));
    return REDIRECT_SYSTEM_COMMUNITY + community.getId();
  }

  private void assertWritePermission(Community community, User subject) {
    if (!permissionUtils.isPermitted(community, PermissionType.WRITE, subject)) {
      throwAuthorisationException(community.getId(), subject, PermissionType.WRITE);
    }
  }

  @PostMapping("/ajax/removeAdmin")
  @ResponseBody
  public AjaxReturnObject<Boolean> removeAdmin(
      @RequestParam("adminToRemove") Long adminId, @RequestParam("commId") Long communityId) {
    User subject = userManager.getAuthenticatedUserInSession();
    if (!permissionUtils.isPermitted("COMMUNITY:WRITE:id=" + communityId)) {
      throwAuthorisationException(communityId, subject, PermissionType.WRITE);
    }

    ServiceOperationResult<Community> result =
        communityService.removeAdminFromCommunity(adminId, communityId);
    if (result.isSucceeded()) {
      publisher.publishEvent(
          new GenericEvent(
              subject, result.getEntity(), AuditAction.WRITE, "removed admin [" + adminId + "]"));
      return new AjaxReturnObject<Boolean>(true, null);
    } else {
      ErrorList el = ErrorList.of("Could not remove admin from this community!");
      return new AjaxReturnObject<Boolean>(null, el);
    }
  }

  @PostMapping("/ajax/removeCommunity")
  @ResponseBody
  public AjaxReturnObject<Boolean> removeCommunity(@RequestParam("ids[]") Long[] communityIds) {
    User subject = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(subject);
    for (Long commId : communityIds) {
      ServiceOperationResult<Community> result = communityService.removeCommunity(commId);
      if (result.isSucceeded()) {
        publisher.publishEvent(new GenericEvent(subject, result.getEntity(), AuditAction.DELETE));
      }
    }
    return new AjaxReturnObject<Boolean>(true, null);
  }

  @GetMapping("/ajax/availableAdmins")
  public String getAvailableAdmins(Model model, @RequestParam("id") Long communityId) {
    List<User> availableAdmins = userManager.getAvailableAdminUsers();
    Community community = communityService.getWithAdmins(communityId);
    for (User admin : community.getAdmins()) {
      availableAdmins.remove(admin);
    }
    community.setAvailableAdmins(availableAdmins);
    model.addAttribute(community);
    return "system/communityAddAdmin";
  }

  /*
   * ==================================================
   *   below methods used by 'Apps Settings' dialog on Community Page
   * ==================================================
   */

  /**
   * Returns system settings page fragment from JSP. Doesn't set any model properties.
   *
   * @return system settings page view
   */
  @GetMapping("/ajax/systemSettingsView")
  public ModelAndView getFileSystemsView() {
    return new ModelAndView("system/community_settings_ajax");
  }

  @GetMapping("/ajax/editableProperties")
  @IgnoreInLoggingInterceptor(ignoreAll = true)
  @ResponseBody
  public Map<String, String> getEditableProperties(
      @RequestParam(value = "communityId", required = true) Long communityId) {
    assertUserCanEditCommunity(
        userManager.getAuthenticatedUserInSession(), communityService.get(communityId));

    Map<String, String> properties = new HashMap<String, String>();
    List<SystemProperty> allProperties = systemPropertyManager.listSystemPropertyDefinitions();
    for (SystemProperty sp : allProperties) {
      properties.put(sp.getName(), "NOT_SET");
    }
    List<SystemPropertyValue> communityProperties =
        systemPropertyManager.getAllByCommunity(communityId);
    for (SystemPropertyValue spv : communityProperties) {
      properties.put(spv.getProperty().getName(), spv.getValue());
    }

    for (SystemPropertyValue systemPropertyValue :
        systemPropertyManager.getAllSysadminProperties()) {
      String value =
          properties
              .get(systemPropertyValue.getProperty().getName())
              .concat("," + systemPropertyValue.getValue());
      properties.put(systemPropertyValue.getProperty().getName(), value);
    }

    return properties; // map of "system property name" -> "community setting value, system admin
    // setting value"
  }

  /**
   * Method for updating system property by name
   *
   * @return updated property value
   */
  @PostMapping("/ajax/updateProperty")
  @ResponseBody
  public AjaxReturnObject<String> updateProperty(
      @RequestParam(value = "propertyName", required = true) String propertyName,
      @RequestParam(value = "newValue", required = true) String newValue,
      @RequestParam(value = "communityId", required = true) Long communityId) {
    User subject = userManager.getAuthenticatedUserInSession();
    assertUserCanEditCommunity(subject, communityService.get(communityId));

    SystemPropertyValue systemPropertyValueBySysAdmin =
        systemPropertyManager.findByName(propertyName);
    // If system admin denies something, the setting cannot be overridden
    if (systemPropertyValueBySysAdmin
            .getValue()
            .equalsIgnoreCase(HierarchicalPermission.DENIED.name())
        && !newValue.equalsIgnoreCase(HierarchicalPermission.DENIED.name())) {
      return new AjaxReturnObject<>(
          null,
          ErrorList.of(
              "System setting was set to DENIED by system admin and cannot be overridden."));
    }

    SystemPropertyValue systemPropertyValue =
        systemPropertyManager.findByNameAndCommunity(propertyName, communityId);
    if (systemPropertyValue == null) {
      SystemProperty systemProperty =
          systemPropertyManager.listSystemPropertyDefinitions().stream()
              .filter(sp -> sp.getName().equals(propertyName))
              .findFirst()
              .get();
      Community community = communityService.get(communityId);
      systemPropertyValue = new SystemPropertyValue(systemProperty, newValue, community);
    }

    systemPropertyValue.setValue(newValue);
    systemPropertyValue = systemPropertyManager.save(systemPropertyValue, subject);

    return new AjaxReturnObject<>(systemPropertyValue.getValue(), null);
  }

  private void assertUserCanEditCommunity(User user, Community community) {
    assertWritePermission(community, user);
  }
}

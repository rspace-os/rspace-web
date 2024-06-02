package com.researchspace.webapp.controller;

import static java.util.stream.Collectors.toList;

import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.dto.UserBasicInfo;
import com.researchspace.model.dtos.CreateCloudGroup;
import com.researchspace.model.dtos.CreateCloudGroupValidator;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.cloud.CloudGroupManager;
import com.researchspace.service.cloud.CloudNotificationManager;
import com.researchspace.service.cloud.CommunityUserManager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller("projectGroupController")
@RequestMapping("/projectGroup")
public class ProjectGroupController extends BaseController {

  private @Autowired CommunityUserManager cloudUserManager;
  private @Autowired CloudGroupManager cloudGroupManager;
  private @Autowired CloudNotificationManager cloudNotificationManager;
  private @Autowired CreateCloudGroupValidator cloudGroupValidator;

  @RequestMapping(method = RequestMethod.GET, value = "/newGroupForm")
  public String projectGroupForm(Model model) {

    CreateCloudGroup createCloudGroupConfig = new CreateCloudGroup();
    model.addAttribute("createCloudGroupConfig", createCloudGroupConfig);
    model.addAttribute("isProjectGroup", true);
    return "admin/cloud/createCloudGroupForm";
  }

  @PostMapping("/createProjectGroup")
  public @ResponseBody AjaxReturnObject<Map<String, String>> createProjectGroup(
      @RequestBody CreateCloudGroup createCloudGroup,
      BindingResult errors,
      HttpServletRequest request) {

    User creator = userManager.getAuthenticatedUserInSession();
    createCloudGroup.setSessionUser(creator);
    cloudGroupValidator.validate(createCloudGroup, errors);
    List<Group> groups = groupManager.listGroupsForOwner(creator);
    Map<String, String> response = new HashMap<>();
    for (Group aGroup : groups) {
      if (aGroup
          .getDisplayName()
          .toLowerCase()
          .equals(createCloudGroup.getGroupName().toLowerCase())) {
        errors.rejectValue(
            "groupName",
            "duplicate.groupname",
            "You have already created a Group with the name: " + createCloudGroup.getGroupName());
        break;
      }
    }
    if (errors.hasErrors()) {
      ErrorList el = inputValidator.populateErrorList(errors, new ErrorList());
      return new AjaxReturnObject<Map<String, String>>(null, el);
    }
    List<String> listEmails = Arrays.asList(createCloudGroup.getEmails());

    Group newLabGroup =
        cloudGroupManager.createAndSaveProjectGroup(
            createCloudGroup.getGroupName(), creator, creator);

    sendInvites(request, creator, listEmails, newLabGroup);
    publisher.publishEvent(new GenericEvent(creator, newLabGroup, AuditAction.CREATE));
    response.put("newGroup", "" + newLabGroup.getId());
    return new AjaxReturnObject<>(response, null);
  }

  private void sendInvites(
      HttpServletRequest request, User creator, List<String> listEmails, Group newGroup) {
    if (!listEmails.isEmpty() && !listEmails.get(0).isEmpty()) {
      List<User> usersList = cloudUserManager.createInvitedUserList(listEmails);
      newGroup.setMemberString(usersList.stream().map(User::getUsername).collect(toList()));
      permissionUtils.refreshCache();
      cloudNotificationManager.sendJoinGroupRequest(creator, newGroup);

      for (User invitedUser : usersList) {
        cloudNotificationManager.sendJoinGroupInvitationEmail(
            creator, invitedUser, newGroup, request);
      }
    }
  }

  /**
   * Remove group.
   *
   * @param groupId
   * @return redirect url - but see comment in code, the actual redirect is handled by JS code
   */
  @PostMapping("/deleteGroup/{grpid}")
  public String removeGroup(@PathVariable("grpid") Long groupId) {

    User subject = userManager.getAuthenticatedUserInSession();
    Group targetGroup = groupManager.getGroup(groupId);
    if (!(targetGroup.isProjectGroup() && targetGroup.getOwner().equals(subject))) {
      throw new AuthorizationException(
          "Unauthorized attempt to delete a project group by [" + subject.getFullName() + "]");
    }
    Group grp = groupManager.removeGroup(groupId, subject);
    userManager.updateSessionUser(subject.getId());
    publisher.publishEvent(new GenericEvent(subject, grp, AuditAction.DELETE));
    // Note the redirect to /userform is actually handled by JS code in viewGroupEditing -
    // the value returned here is arbitrary but must match a view mapped in the application
    return "redirect:/userform";
  }

  @ResponseBody
  @GetMapping("/searchPublicUserInfoList")
  public AjaxReturnObject<List<UserBasicInfo>> searchPublicUserInfoList(
      @RequestParam(value = "term", required = true) String term) {

    term = SecureStringUtils.removeWildCards(term);
    List<UserBasicInfo> userInfos = null;
    try {
      userInfos = userManager.searchPublicUserInfoList(term);
    } catch (IllegalArgumentException e) {
      if (e.getMessage().contains("must be at least 3 characters")) {
        ErrorList error =
            ErrorList.of(getText("errors.minlength", new String[] {"Search term", "3"}));
        return new AjaxReturnObject<>(null, error);
      } else {
        throw e;
      }
    }
    return new AjaxReturnObject<>(userInfos, null);
  }
}

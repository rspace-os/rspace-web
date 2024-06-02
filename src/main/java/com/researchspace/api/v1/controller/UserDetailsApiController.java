package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.UserDetailsApi;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.ContainerApiManager;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestAttribute;

/** API for retrieving details of RSpace users. */
@ApiController
public class UserDetailsApiController extends BaseApiController implements UserDetailsApi {

  @Value("${api.userDetails.alwaysReturnFullDetails:false}")
  private boolean isAlwaysReturnFullDetailsEnabled;

  private @Autowired UserManager userMgr;
  private @Autowired ContainerApiManager containerMgr;

  @Override
  public ApiUser getCurrentUserDetails(@RequestAttribute(name = "user") User user) {
    return getPopulatedApiUser(user);
  }

  private ApiUser getPopulatedApiUser(User user) {
    ApiUser apiUser = new ApiUser(user);
    apiUser.setWorkbenchId(containerMgr.getWorkbenchIdForUser(user));
    return apiUser;
  }

  @Override
  public boolean isOperatedAs(@RequestAttribute(name = "user") User user) {
    return SecurityUtils.getSubject().isRunAs();
  }

  @Override
  public List<ApiUser> getGroupMembersForCurrentUser(@RequestAttribute(name = "user") User user) {
    List<ApiUser> groupMembers =
        getMembersOfUserGroups(user).stream()
            .map(u -> getPopulatedApiUser(u))
            .collect(Collectors.toList());
    return groupMembers;
  }

  private List<User> getMembersOfUserGroups(User user) {
    return user.getGroups().stream()
        .flatMap(g -> g.getMembers().stream())
        .distinct()
        .collect(Collectors.toList());
  }

  @Override
  public List<ApiUser> searchUserDetails(
      String query, @RequestAttribute(name = "user") User subject) {
    List<User> connectedUsers = getMembersOfUserGroups(subject);
    connectedUsers.add(subject);

    return userMgr.searchUsers(query).stream()
        .map(foundUser -> getApiUserWithFullOrPublicData(connectedUsers, foundUser, subject))
        .collect(Collectors.toList());
  }

  private ApiUser getApiUserWithFullOrPublicData(
      List<User> connectedUsers, User foundUser, User subject) {
    if (subject.hasSysadminRole()
        || connectedUsers.contains(foundUser)
        || isAlwaysReturnFullDetailsEnabled) {
      return getPopulatedApiUser(foundUser);
    }
    return getPublicDataApiUser(foundUser);
  }

  private ApiUser getPublicDataApiUser(User user) {
    return new ApiUser(user, true);
  }
}

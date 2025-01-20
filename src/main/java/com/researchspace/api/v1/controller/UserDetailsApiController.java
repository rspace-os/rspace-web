package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.UserDetailsApi;
import com.researchspace.api.v1.model.ApiUiNavigationData;
import com.researchspace.api.v1.model.ApiUiNavigationData.ApiUiNavigationUserDetails;
import com.researchspace.api.v1.model.ApiUiNavigationData.ApiUiNavigationVisibleTabs;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.User;
import com.researchspace.model.UserProfile;
import com.researchspace.repository.spi.ExternalId;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.service.UserManager;
import com.researchspace.service.UserProfileManager;
import com.researchspace.service.inventory.ContainerApiManager;
import java.util.Date;
import java.util.List;
import java.util.Optional;
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

  private @Autowired UserExternalIdResolver extIdResolver;
  private @Autowired UserProfileManager userProfileManager;
  private @Autowired SystemPropertyPermissionManager systemPropertyManager;
  private @Autowired SystemPropertyPermissionManager systemPropertyPermissionManager;
  private @Autowired MaintenanceManager maintenanceManager;

  @Override
  public ApiUiNavigationData getDataForNavigationUI(@RequestAttribute(name = "user") User user) {

    ApiUiNavigationData navigationData = new ApiUiNavigationData();

    navigationData.setBannerImgSrc(
        properties.isCloud() ? "/images/mainLogoCloudN2.png" : "/public/banner");

    ApiUiNavigationVisibleTabs visibleTabs = new ApiUiNavigationVisibleTabs();
    visibleTabs.setInventory(systemPropertyManager.isPropertyAllowed(user, "inventory.available"));
    visibleTabs.setMyLabGroups(user.hasAnyPiOrLabGroupViewAllRole());
    visibleTabs.setPublished(
        systemPropertyPermissionManager.isPropertyAllowed(user, "public_sharing"));
    visibleTabs.setSystem(user.hasAdminRole());
    navigationData.setVisibleTabs(visibleTabs);

    ApiUiNavigationUserDetails userDetails = new ApiUiNavigationUserDetails();
    userDetails.setUsername(user.getUsername());
    userDetails.setFullName(user.getFullName());
    userDetails.setEmail(user.getEmail());
    Optional<ExternalId> extId = extIdResolver.getExternalIdForUser(user, IdentifierScheme.ORCID);
    if (extId.isPresent()) {
      userDetails.setOrcidId(extId.get().getIdentifier());
    }
    UserProfile userProfile = userProfileManager.getUserProfile(user);
    Long profileImageId =
        Optional.ofNullable(userProfile.getProfilePicture()).map(ImageBlob::getId).orElse(null);
    if (profileImageId != null) {
      userDetails.setProfileImgSrc(
          "/userform/profileImage/" + userProfile.getId() + "/" + profileImageId);
    }
    Date lastLogin = user.getLastLogin();
    userDetails.setLastSession(lastLogin == null ? null : lastLogin.getTime());
    navigationData.setUserDetails(userDetails);

    navigationData.setIncomingMaintenance(
        !ScheduledMaintenance.NULL.equals(maintenanceManager.getNextScheduledMaintenance()));
    navigationData.setOperatedAs(SecurityUtils.getSubject().isRunAs());

    return navigationData;
  }
}

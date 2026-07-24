package com.researchspace.api.v2.controller;

import com.researchspace.api.v1.controller.ApiController;
import com.researchspace.api.v2.model.ApiV2CurrentUser;
import com.researchspace.api.v2.model.ApiV2CurrentUser.Capabilities;
import com.researchspace.api.v2.model.ApiV2CurrentUser.Orcid;
import com.researchspace.api.v2.model.ApiV2CurrentUser.Session;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.User;
import com.researchspace.model.UserProfile;
import com.researchspace.repository.spi.ExternalId;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.service.UserProfileManager;
import com.researchspace.service.inventory.ContainerApiManager;
import java.util.Date;
import java.util.Optional;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@ApiController
@RequestMapping("/api/v2/users")
public class UsersV2Controller {

  @Autowired private ContainerApiManager containerApiManager;
  @Autowired private UserExternalIdResolver externalIdResolver;
  @Autowired private UserProfileManager userProfileManager;
  @Autowired private SystemPropertyPermissionManager propertyPermissionManager;

  @GetMapping("/me")
  public ApiV2CurrentUser getCurrentUser(@RequestAttribute(name = "user") User user) {
    UserProfile profile = userProfileManager.getUserProfile(user);
    Long imageId =
        Optional.ofNullable(profile.getProfilePicture()).map(ImageBlob::getId).orElse(null);
    return new ApiV2CurrentUser(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getRootFolder() == null ? null : user.getRootFolder().getId(),
        containerApiManager.getWorkbenchIdForUser(user),
        user.isPI(),
        user.hasSysadminRole(),
        imageId == null ? null : "/userform/profileImage/" + profile.getId() + "/" + imageId,
        imageId == null ? null : "/api/v2/users/me/profile-image",
        orcid(user),
        capabilities(user),
        session(user));
  }

  @GetMapping(value = "/me/profile-image", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> getCurrentUserProfileImage(
      @RequestAttribute(name = "user") User user) {
    ImageBlob image = userProfileManager.getUserProfile(user).getProfilePicture();
    if (image == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.IMAGE_PNG)
        .body(image.getData());
  }

  private Orcid orcid(User user) {
    boolean available =
        externalIdResolver.isIdentifierSchemeAvailable(user, IdentifierScheme.ORCID);
    String id =
        available
            ? externalIdResolver
                .getExternalIdForUser(user, IdentifierScheme.ORCID)
                .map(ExternalId::getIdentifier)
                .orElse(null)
            : null;
    return new Orcid(available, id);
  }

  private Capabilities capabilities(User user) {
    return new Capabilities(
        propertyPermissionManager.isPropertyAllowed(user, SystemPropertyName.INVENTORY_AVAILABLE),
        propertyPermissionManager.isPropertyAllowed(user, SystemPropertyName.PUBLIC_SHARING),
        user.hasAdminRole());
  }

  private Session session(User user) {
    return new Session(SecurityUtils.getSubject().isRunAs(), lastSession(user));
  }

  private String lastSession(User user) {
    Date lastLogin = user.getLastLogin();
    return lastLogin == null ? null : lastLogin.toInstant().toString();
  }
}

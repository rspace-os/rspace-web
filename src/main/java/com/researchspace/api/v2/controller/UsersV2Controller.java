package com.researchspace.api.v2.controller;

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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/users")
public class UsersV2Controller {

  private final ContainerApiManager containerApiManager;
  private final UserExternalIdResolver externalIdResolver;
  private final UserProfileManager userProfileManager;
  private final SystemPropertyPermissionManager propertyPermissionManager;

  @GetMapping("/me")
  @Operation(
      operationId = "getCurrentUser",
      summary = "Get the current user",
      description = "Returns identity, capabilities, external identifiers, and session state.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Current user details."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Access is forbidden."),
        @ApiResponse(responseCode = "429", description = "The request was throttled.")
      })
  public ApiV2CurrentUser getCurrentUser(@RequestAttribute(name = "user") User user) {
    UserProfile profile = userProfileManager.getUserProfile(user);
    ImageBlob picture = profile.getProfilePicture();
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
        picture == null
            ? null
            : "/userform/profileImage/" + profile.getId() + "/" + picture.getId(),
        picture == null ? null : "/api/v2/users/me/profile-image",
        orcid(user),
        capabilities(user),
        session(user));
  }

  @GetMapping(value = "/me/profile-image", produces = MediaType.IMAGE_PNG_VALUE)
  @Operation(
      operationId = "getCurrentUserProfileImage",
      summary = "Get the current user's profile image",
      description = "Returns a PNG image with Cache-Control: no-store.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Current profile image.",
            headers = @Header(name = "Cache-Control", description = "Always contains no-store.")),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Access is forbidden."),
        @ApiResponse(responseCode = "404", description = "The user has no profile image."),
        @ApiResponse(responseCode = "429", description = "The request was throttled.")
      })
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
    Date lastLogin = user.getLastLogin();
    return new Session(
        SecurityUtils.getSubject().isRunAs(),
        lastLogin == null ? null : lastLogin.toInstant().toString());
  }
}

package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.auth.ApiAuthenticationException;
import com.researchspace.api.v1.auth.ApiAuthenticator;
import com.researchspace.api.v1.controller.ApiAuthenticationInterceptor;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.User;
import com.researchspace.model.UserProfile;
import com.researchspace.model.record.Folder;
import com.researchspace.repository.spi.ExternalId;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.service.UserProfileManager;
import com.researchspace.service.inventory.ContainerApiManager;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.util.NestedServletException;

class UsersV2ControllerTest {

  private final ContainerApiManager containerApiManager = mock(ContainerApiManager.class);
  private final FeatureFlagManager featureFlagManager = mock(FeatureFlagManager.class);
  private final UserExternalIdResolver externalIdResolver = mock(UserExternalIdResolver.class);
  private final UserProfileManager userProfileManager = mock(UserProfileManager.class);
  private final SystemPropertyPermissionManager propertyPermissionManager =
      mock(SystemPropertyPermissionManager.class);
  private final Subject subject = mock(Subject.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    UsersV2Controller controller = new UsersV2Controller();
    ReflectionTestUtils.setField(controller, "containerApiManager", containerApiManager);
    ReflectionTestUtils.setField(controller, "featureFlagManager", featureFlagManager);
    ReflectionTestUtils.setField(controller, "externalIdResolver", externalIdResolver);
    ReflectionTestUtils.setField(controller, "userProfileManager", userProfileManager);
    ReflectionTestUtils.setField(
        controller, "propertyPermissionManager", propertyPermissionManager);
    ThreadContext.bind(subject);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(problemAdvice()).build();
  }

  @AfterEach
  void tearDown() {
    ThreadContext.unbindSubject();
  }

  @Test
  void returnsCurrentUserProfileCapabilitiesAndSession() throws Exception {
    User user = mock(User.class);
    UserProfile profile = mock(UserProfile.class);
    ImageBlob profileImage = mock(ImageBlob.class);
    ExternalId orcid = mock(ExternalId.class);
    Folder homeFolder = mock(Folder.class);

    when(user.getId()).thenReturn(123L);
    when(user.getUsername()).thenReturn("ada");
    when(user.getEmail()).thenReturn("ada@example.com");
    when(user.getFirstName()).thenReturn("Ada");
    when(user.getLastName()).thenReturn("Lovelace");
    when(user.getRootFolder()).thenReturn(homeFolder);
    when(homeFolder.getId()).thenReturn(456L);
    when(user.isPI()).thenReturn(true);
    when(user.hasSysadminRole()).thenReturn(false);
    when(user.hasAdminRole()).thenReturn(true);
    when(user.getLastLogin()).thenReturn(Date.from(Instant.parse("2026-07-15T08:30:00Z")));
    when(containerApiManager.getWorkbenchIdForUser(user)).thenReturn(789L);
    when(userProfileManager.getUserProfile(user)).thenReturn(profile);
    when(profile.getId()).thenReturn(12L);
    when(profile.getProfilePicture()).thenReturn(profileImage);
    when(profileImage.getId()).thenReturn(34L);
    when(externalIdResolver.isIdentifierSchemeAvailable(user, IdentifierScheme.ORCID))
        .thenReturn(true);
    when(externalIdResolver.getExternalIdForUser(user, IdentifierScheme.ORCID))
        .thenReturn(Optional.of(orcid));
    when(orcid.getIdentifier()).thenReturn("0000-0001-2345-6789");
    when(propertyPermissionManager.isPropertyAllowed(user, SystemPropertyName.INVENTORY_AVAILABLE))
        .thenReturn(true);
    when(propertyPermissionManager.isPropertyAllowed(user, SystemPropertyName.PUBLIC_SHARING))
        .thenReturn(false);
    when(subject.isRunAs()).thenReturn(true);
    when(featureFlagManager.canUseDevtools(user)).thenReturn(true);
    when(featureFlagManager.canOverrideFeatureFlags(user)).thenReturn(false);
    when(featureFlagManager.canChangeFeatureFlagBaselines(user)).thenReturn(false);

    mockMvc
        .perform(get("/api/v2/users/me").requestAttr("user", user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(123))
        .andExpect(jsonPath("$.username").value("ada"))
        .andExpect(jsonPath("$.email").value("ada@example.com"))
        .andExpect(jsonPath("$.firstName").value("Ada"))
        .andExpect(jsonPath("$.lastName").value("Lovelace"))
        .andExpect(jsonPath("$.homeFolderId").value(456))
        .andExpect(jsonPath("$.workbenchId").value(789))
        .andExpect(jsonPath("$.hasPiRole").value(true))
        .andExpect(jsonPath("$.hasSysAdminRole").value(false))
        .andExpect(jsonPath("$.profileImageUrl").value("/userform/profileImage/12/34"))
        .andExpect(jsonPath("$.profileImageApiUrl").value("/api/v2/users/me/profile-image"))
        .andExpect(jsonPath("$.orcid.available").value(true))
        .andExpect(jsonPath("$.orcid.id").value("0000-0001-2345-6789"))
        .andExpect(jsonPath("$.capabilities.canUseInventory").value(true))
        .andExpect(jsonPath("$.capabilities.canPublish").value(false))
        .andExpect(jsonPath("$.capabilities.canViewSystem").value(true))
        .andExpect(jsonPath("$.session.operatedAs").value(true))
        .andExpect(jsonPath("$.session.lastSession").value("2026-07-15T08:30:00Z"))
        .andExpect(jsonPath("$.session.canUseDevtools").value(true))
        .andExpect(jsonPath("$.session.canOverrideFeatureFlags").value(false))
        .andExpect(jsonPath("$.session.canChangeFeatureFlagBaselines").value(false));
  }

  @Test
  void omitsUnavailableOptionalProfileData() throws Exception {
    User user = mock(User.class);
    UserProfile profile = mock(UserProfile.class);
    when(userProfileManager.getUserProfile(user)).thenReturn(profile);
    when(externalIdResolver.isIdentifierSchemeAvailable(user, IdentifierScheme.ORCID))
        .thenReturn(false);

    mockMvc
        .perform(get("/api/v2/users/me").requestAttr("user", user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileImageUrl").isEmpty())
        .andExpect(jsonPath("$.profileImageApiUrl").isEmpty())
        .andExpect(jsonPath("$.orcid.available").value(false))
        .andExpect(jsonPath("$.orcid.id").isEmpty())
        .andExpect(jsonPath("$.session.lastSession").isEmpty());
  }

  @Test
  void servesProfileImageThroughApiAuthenticationSurface() throws Exception {
    User user = mock(User.class);
    UserProfile profile = mock(UserProfile.class);
    ImageBlob image = mock(ImageBlob.class);
    byte[] imageBytes = {1, 2, 3};
    when(userProfileManager.getUserProfile(user)).thenReturn(profile);
    when(profile.getProfilePicture()).thenReturn(image);
    when(image.getId()).thenReturn(34L);
    when(image.getData()).thenReturn(imageBytes);

    mockMvc
        .perform(get("/api/v2/users/me/profile-image").requestAttr("user", user))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().contentType("image/png"))
        .andExpect(content().bytes(imageBytes));
  }

  @Test
  void returnsNotFoundWhenCurrentUserHasNoProfileImage() throws Exception {
    User user = mock(User.class);
    UserProfile profile = mock(UserProfile.class);
    when(userProfileManager.getUserProfile(user)).thenReturn(profile);

    mockMvc
        .perform(get("/api/v2/users/me/profile-image").requestAttr("user", user))
        .andExpect(status().isNotFound());
  }

  @Test
  void returnsExactOrdinaryUserShape() throws Exception {
    User user = mock(User.class);
    when(user.getId()).thenReturn(321L);
    when(user.getUsername()).thenReturn("ordinary");
    when(user.getEmail()).thenReturn("ordinary@example.com");
    when(user.getFirstName()).thenReturn("");
    when(user.getLastName()).thenReturn("");
    when(containerApiManager.getWorkbenchIdForUser(user)).thenReturn(null);
    when(userProfileManager.getUserProfile(user)).thenReturn(mock(UserProfile.class));

    mockMvc
        .perform(get("/api/v2/users/me").requestAttr("user", user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(14))
        .andExpect(jsonPath("$.orcid.length()").value(2))
        .andExpect(jsonPath("$.capabilities.length()").value(3))
        .andExpect(jsonPath("$.session.length()").value(5))
        .andExpect(jsonPath("$.id").value(321))
        .andExpect(jsonPath("$.username").value("ordinary"))
        .andExpect(jsonPath("$.email").value("ordinary@example.com"))
        .andExpect(jsonPath("$.firstName").value(""))
        .andExpect(jsonPath("$.lastName").value(""))
        .andExpect(jsonPath("$.homeFolderId").isEmpty())
        .andExpect(jsonPath("$.workbenchId").isEmpty())
        .andExpect(jsonPath("$.hasPiRole").value(false))
        .andExpect(jsonPath("$.hasSysAdminRole").value(false))
        .andExpect(jsonPath("$.profileImageUrl").isEmpty())
        .andExpect(jsonPath("$.profileImageApiUrl").isEmpty())
        .andExpect(jsonPath("$.orcid.available").value(false))
        .andExpect(jsonPath("$.orcid.id").isEmpty())
        .andExpect(jsonPath("$.capabilities.canUseInventory").value(false))
        .andExpect(jsonPath("$.capabilities.canPublish").value(false))
        .andExpect(jsonPath("$.capabilities.canViewSystem").value(false))
        .andExpect(jsonPath("$.session.operatedAs").value(false))
        .andExpect(jsonPath("$.session.lastSession").isEmpty())
        .andExpect(jsonPath("$.session.canUseDevtools").value(false))
        .andExpect(jsonPath("$.session.canOverrideFeatureFlags").value(false))
        .andExpect(jsonPath("$.session.canChangeFeatureFlagBaselines").value(false));
  }

  @Test
  void reportsPiRoleWithoutGrantingSystemCapability() throws Exception {
    User user = mock(User.class);
    when(user.isPI()).thenReturn(true);
    when(user.hasAdminRole()).thenReturn(false);
    when(userProfileManager.getUserProfile(user)).thenReturn(mock(UserProfile.class));

    mockMvc
        .perform(get("/api/v2/users/me").requestAttr("user", user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasPiRole").value(true))
        .andExpect(jsonPath("$.hasSysAdminRole").value(false))
        .andExpect(jsonPath("$.capabilities.canViewSystem").value(false));
  }

  @Test
  void reportsSysadminRole() throws Exception {
    User user = mock(User.class);
    when(user.hasSysadminRole()).thenReturn(true);
    when(user.hasAdminRole()).thenReturn(true);
    when(userProfileManager.getUserProfile(user)).thenReturn(mock(UserProfile.class));

    mockMvc
        .perform(get("/api/v2/users/me").requestAttr("user", user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasSysAdminRole").value(true))
        .andExpect(jsonPath("$.capabilities.canViewSystem").value(true));
  }

  @Test
  void reportsAvailableOrcidWithoutIdAndIndependentPermissions() throws Exception {
    User user = mock(User.class);
    when(userProfileManager.getUserProfile(user)).thenReturn(mock(UserProfile.class));
    when(externalIdResolver.isIdentifierSchemeAvailable(user, IdentifierScheme.ORCID))
        .thenReturn(true);
    when(externalIdResolver.getExternalIdForUser(user, IdentifierScheme.ORCID))
        .thenReturn(Optional.empty());
    when(propertyPermissionManager.isPropertyAllowed(user, SystemPropertyName.INVENTORY_AVAILABLE))
        .thenReturn(false);
    when(propertyPermissionManager.isPropertyAllowed(user, SystemPropertyName.PUBLIC_SHARING))
        .thenReturn(true);

    mockMvc
        .perform(get("/api/v2/users/me").requestAttr("user", user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orcid.available").value(true))
        .andExpect(jsonPath("$.orcid.id").isEmpty())
        .andExpect(jsonPath("$.capabilities.canUseInventory").value(false))
        .andExpect(jsonPath("$.capabilities.canPublish").value(true))
        .andExpect(jsonPath("$.capabilities.canViewSystem").value(false));
  }

  @Test
  void requiresAuthenticatedUserRequestAttribute() throws Exception {
    ApiAuthenticator authenticator = mock(ApiAuthenticator.class);
    when(authenticator.authenticate(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new ApiAuthenticationException("Authentication required"));
    ApiAuthenticationInterceptor interceptor = new ApiAuthenticationInterceptor();
    ReflectionTestUtils.setField(interceptor, "combinedApiAuthenticator", authenticator);
    MockMvc authenticatedMockMvc =
        MockMvcBuilders.standaloneSetup(new UsersV2Controller())
            .addInterceptors(interceptor)
            .setControllerAdvice(problemAdvice())
            .build();

    assertThrows(
        NestedServletException.class, () -> authenticatedMockMvc.perform(get("/api/v2/users/me")));
  }

  private static ApiV2ControllerAdvice problemAdvice() {
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage(
        "errors.api.v2.authenticationRequired", Locale.getDefault(), "Authentication is required.");
    source.addMessage(
        "errors.api.v2.notFound", Locale.getDefault(), "The requested resource was not found.");
    return new ApiV2ControllerAdvice(new MessageSourceUtils(source));
  }
}

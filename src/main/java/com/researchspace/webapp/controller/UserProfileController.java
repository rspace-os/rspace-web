package com.researchspace.webapp.controller;

import static com.researchspace.core.util.MediaUtils.getExtension;
import static com.researchspace.model.preference.Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL;
import static com.researchspace.model.preference.Preference.BROADCAST_REQUEST_BY_EMAIL;
import static com.researchspace.model.preference.Preference.NOTIFICATION_DOCUMENT_DELETED_PREF;
import static com.researchspace.model.preference.Preference.NOTIFICATION_DOCUMENT_EDITED_PREF;
import static com.researchspace.model.preference.Preference.NOTIFICATION_DOCUMENT_SHARED_PREF;
import static com.researchspace.model.preference.Preference.NOTIFICATION_DOCUMENT_UNSHARED_PREF;
import static com.researchspace.model.preference.Preference.NOTIFICATION_REQUEST_STATUS_CHANGE_PREF;
import static com.researchspace.model.preference.Preference.PROCESS_COMPLETED_PREF;
import static com.researchspace.model.preference.Preference.PROCESS_FAILED_PREF;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.abbreviate;
import static org.apache.commons.lang.StringUtils.isEmpty;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.Constants;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.api.v1.model.NewOAuthTokenResponse;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.core.util.ResponseUtil;
import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.DeploymentPropertyType;
import com.researchspace.model.Group;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.Organisation;
import com.researchspace.model.ProductType;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.UserApiKey;
import com.researchspace.model.UserGroup;
import com.researchspace.model.UserPreference;
import com.researchspace.model.UserProfile;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dto.GroupInfo;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.dto.MiniProfile;
import com.researchspace.model.dto.UserPublicInfo;
import com.researchspace.model.dtos.PreferencesCommand;
import com.researchspace.model.events.AccountEventType;
import com.researchspace.model.events.UserAccountEvent;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.frontend.OAuthAppInfo;
import com.researchspace.model.frontend.PublicOAuthAppInfo;
import com.researchspace.model.frontend.PublicOAuthApps;
import com.researchspace.model.frontend.PublicOAuthConnAppInfo;
import com.researchspace.model.frontend.PublicOAuthConnApps;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.preference.PreferenceCategory;
import com.researchspace.model.preference.SettingsType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.views.RecordTypeFilter;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.AutoshareManager;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.IVerificationPasswordValidator;
import com.researchspace.service.OAuthAppManager;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserApiKeyManager;
import com.researchspace.service.UserProfileManager;
import com.researchspace.service.UserRoleHandler;
import com.researchspace.service.cloud.CommunityUserManager;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.vavr.control.Try;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

/** Controller for UserProfile page */
@Controller
@RequestMapping({"/userform*", "/public/publicView/userform*"})
public class UserProfileController extends BaseController {

  private static final String IMAGES_DEFAULT_PROFILE_IMAGE_PNG = "/images/defaultProfileImage.png";
  private static final long MAX_PROFILE_SIZE = 1_000_000; // 1Mb
  private static final String FIRST_NAME = "firstName";
  private static final String ERRORS_REQUIRED = "errors.required";
  private static final String ERRORS_MAXLENGTH = "errors.maxlength";
  private static final String AFFILIATION = "affiliation";
  public static final String API_KEY_IS_ACTIVE = "apiKey is ACTIVE";

  private @Autowired IReauthenticator reauthenticator;
  private @Autowired SystemPropertyPermissionManager systemPropertyPermissionUtils;
  private @Autowired CommunityUserManager cloudUserManager;
  private @Autowired UserApiKeyManager apiKeyMgr;
  private @Autowired ApiAvailabilityHandler availabilityHandler;
  private @Autowired IVerificationPasswordValidator verificationPasswordValidator;
  private @Autowired AnalyticsManager analyticsManager;
  private @Autowired UserProfileManager userProfileManager;
  private @Autowired OAuthAppManager oAuthAppManager;
  private @Autowired OAuthTokenManager oAuthTokenManager;
  private @Autowired AutoshareManager autoshareManager;
  private @Autowired UserRoleHandler userRoleHandler;

  @Value("${sysadmin.apikey.generation}")
  private boolean sysadminApiKeyGeneration;

  @Autowired
  @Qualifier("loginPasswordResetHandler")
  private PasswordChangeHandlerBase passwordResetHandler;

  // perf testing suggests <= items to share  will take less than 5s
  static final int MAX_TO_AUTO_SHARE_SYNC = 100;

  static final List<Preference> desiredMessageDisplayOrder =
      Arrays.asList(
          NOTIFICATION_DOCUMENT_SHARED_PREF,
          NOTIFICATION_DOCUMENT_UNSHARED_PREF,
          NOTIFICATION_DOCUMENT_DELETED_PREF,
          NOTIFICATION_DOCUMENT_EDITED_PREF,
          NOTIFICATION_REQUEST_STATUS_CHANGE_PREF,
          PROCESS_FAILED_PREF,
          PROCESS_COMPLETED_PREF,
          BROADCAST_REQUEST_BY_EMAIL,
          BROADCAST_NOTIFICATIONS_BY_EMAIL);

  public UserProfileController() {
    setCancelView("redirect:/workspace");
    setSuccessView("redirect:/admin/users");
  }

  /**
   * Gets a user's profile
   *
   * @param userId An optional userId, if authenticated user wants to look at another user's
   *     profile.
   * @param principal
   * @param model
   * @return
   * @throws RecordAccessDeniedException
   */
  @GetMapping
  public ModelAndView showUserForm(
      @RequestParam(value = "userId", required = false) Long userId,
      Principal principal,
      Model model)
      throws RecordAccessDeniedException {

    User sessionUser = userManager.getUserByUsername(principal.getName(), true);
    User user;

    if (userId == null) {
      user = sessionUser; // own profile by default
    } else {
      user = userManager.getUser(userId + "");
    }

    if (properties.isProfileHidingEnabled()) {
      if (user.isPrivateProfile()) {
        userManager.populateConnectedUserList(sessionUser);
        if (!sessionUser.isConnectedToUser(user)) {
          throw new RecordAccessDeniedException(getResourceNotFoundMessage("User", userId));
        }
      }
      userManager.populateConnectedGroupList(sessionUser);
    }

    boolean canEdit = checkPermissions(sessionUser, user);
    model.addAttribute("canEdit", canEdit);
    model.addAttribute("canEditEmail", canEdit && canEditEmail(user));
    model.addAttribute("canEditPassword", canEdit && canEditPassword(user));
    model.addAttribute("canEditName", canEdit && canEditName());
    model.addAttribute("canEditVerificationPassword", canEdit && canEditVerificationPassword(user));
    model.addAttribute("isVerificationPasswordSet", isVerificationPasswordSet(user));
    model.addAttribute("showLastLoginDate", getLastLoginTime(user, sessionUser));
    model.addAttribute("subject", sessionUser);
    model.addAttribute(
        "publish_allowed", systemPropertyPermissionUtils.isPropertyAllowed(user, "public_sharing"));
    user.setRole("User");
    for (Role role : user.getRoles()) {
      if (role.getName().equals(Constants.ADMIN_ROLE)) {
        user.setRole("Admin");
        break;
      } else if (role.getName().equals(Constants.SYSADMIN_ROLE)) {
        user.setRole("SysAdmin");
        break;
      }
    }

    user.setConfirmPassword(user.getPassword());
    model.addAttribute(user);
    addProfileAndImageDetails(model, user);
    if (user.equals(sessionUser)) {
      loadMessageSettings(principal, model);
    }
    addIntegrationDetailsToModel(model, user);
    addAccountEvents(user, model);

    return new ModelAndView("userform");
  }

  private void addAccountEvents(User user, Model model) {
    List<UserAccountEvent> accountEvents = userManager.getAccountEventsForUser(user);
    model.addAttribute("accountEvents", accountEvents);
  }

  private void addIntegrationDetailsToModel(Model model, User user) {
    IntegrationInfo orcidIntegration = getIntegrationForUser("ORCID", user);
    if (orcidIntegration != null) {
      model.addAttribute("orcidAvailable", orcidIntegration.isAvailable());
      model.addAttribute("orcidOptionsId", orcidIntegration.retrieveFirstOptionsId());
      model.addAttribute("orcidId", orcidIntegration.retrieveFirstOptionValue());
    }
  }

  private IntegrationInfo getIntegrationForUser(String integrationName, User user) {
    return integrationsHandler != null
        ? integrationsHandler.getIntegration(user, integrationName)
        : null;
  }

  private boolean canEditEmail(User user) {
    return (!SignupSource.GOOGLE.equals(user.getSignupSource()))
        && properties.isProfileEmailEditable();
  }

  private boolean canEditName() {
    return properties.isProfileNameEditable();
  }

  private boolean canEditPassword(User user) {
    return isStandaloneAndUserSignedUpInternally(user) || isSSOBackdoorUser(user);
  }

  private boolean isStandaloneAndUserSignedUpInternally(User user) {
    SignupSource signupSource = user.getSignupSource();
    boolean signedUpInternally =
        SignupSource.MANUAL.equals(signupSource) || SignupSource.INTERNAL.equals(signupSource);
    return properties.isStandalone() && signedUpInternally;
  }

  private boolean isSSOBackdoorUser(User user) {
    return SignupSource.SSO_BACKDOOR.equals(user.getSignupSource());
  }

  private boolean canEditVerificationPassword(User user) {
    return verificationPasswordValidator.isVerificationPasswordRequired(user);
  }

  private boolean isVerificationPasswordSet(User user) {
    return verificationPasswordValidator.isVerificationPasswordSet(user);
  }

  private void loadMessageSettings(Principal principal, Model model) {

    Set<UserPreference> allPrefs = userManager.getUserAndPreferencesForUser(principal.getName());
    for (UserPreference up : allPrefs) {
      up.setUser(null); // don't pass sensitive info back to UI
    }

    List<UserPreference> allPrefsList = new ArrayList<>(allPrefs);
    List<UserPreference> displayedPrefsList = sortAndFilterDisplayedPrefs(allPrefsList);

    PreferencesCommand command = new PreferencesCommand();
    command.setPrefs(displayedPrefsList);
    model.addAttribute("preferences", command);
    model.addAttribute("categories", PreferenceCategory.values());
  }

  // rspac 953
  private List<UserPreference> sortAndFilterDisplayedPrefs(List<UserPreference> anyOrder) {
    List<UserPreference> actual = new ArrayList<>();
    desiredMessageDisplayOrder.stream()
        .forEach(
            pref -> {
              Optional<UserPreference> opt =
                  anyOrder.stream().filter(p2 -> pref.equals(p2.getPreference())).findFirst();
              opt.ifPresent(actual::add);
            });
    return actual;
  }

  private void addProfileAndImageDetails(Model model, User user) {
    UserProfile profile = userProfileManager.getUserProfile(user);
    model.addAttribute("profile", profile);

    Long profileImageId = getProfileImageId(profile);
    model.addAttribute("profileImageId", profileImageId);
  }

  private Long getProfileImageId(UserProfile profile) {
    // -1 is default blob id
    return Optional.ofNullable(profile.getProfilePicture()).map(ImageBlob::getId).orElse(-1L);
  }

  @IgnoreInLoggingInterceptor(ignoreAllRequestParams = true)
  @PostMapping("/ajax/changePassword")
  @ResponseBody
  public AjaxReturnObject<String> changePassword(
      @RequestParam("currentPassword") String currentPassword,
      @RequestParam("newPassword") String newPassword,
      @RequestParam("confirmPassword") String confirmPassword,
      HttpServletRequest request) {
    User user = userManager.getAuthenticatedUserInSession();
    String msg =
        passwordResetHandler.changePassword(
            currentPassword, newPassword, confirmPassword, request, user);
    return new AjaxReturnObject<>(msg, null);
  }

  private void logAuthenticationFailure(HttpServletRequest request, User user) {
    SECURITY_LOG.warn(
        "{}  [{}] unsuccessfully attempted to  reset password but "
            + "did not authenticate from remote address [{}]",
        user.getFullName(),
        user.getId(),
        RequestUtil.remoteAddr(request));
  }

  private class UserDataValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
      return clazz.isAssignableFrom(User.class);
    }

    @Override
    public void validate(Object target, Errors errors) {
      User u = (User) target;
      ValidationUtils.rejectIfEmpty(
          errors, FIRST_NAME, ERRORS_REQUIRED, new Object[] {"First name"});
      ValidationUtils.rejectIfEmpty(
          errors, "lastName", ERRORS_REQUIRED, new Object[] {"Last name"});
      if (u.getFirstName().length() > UserProfile.MAX_FIELD_LENG) {
        errors.rejectValue(FIRST_NAME, ERRORS_MAXLENGTH, new String[] {FIRST_NAME, "255"}, "");
      }
      if (u.getLastName().length() > UserProfile.MAX_FIELD_LENG) {
        errors.rejectValue("lastName", ERRORS_MAXLENGTH, new String[] {FIRST_NAME, "255"}, "");
      }
      if (properties.isCloud()) {
        ValidationUtils.rejectIfEmpty(
            errors, AFFILIATION, ERRORS_REQUIRED, new Object[] {"Affiliation"});
        // rspac-932
        if (!StringUtils.isBlank(u.getAffiliation())
            && u.getAffiliation().length() > Organisation.MAX_INDEXABLE_UTF_LENGTH) {
          errors.rejectValue(
              AFFILIATION,
              ERRORS_MAXLENGTH,
              new String[] {AFFILIATION, "" + Organisation.MAX_INDEXABLE_UTF_LENGTH},
              "");
        }
      }
    }
  }

  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"emailPasswordInput"})
  @PostMapping("/ajax/changeEmail")
  @DeploymentProperty(DeploymentPropertyType.PROFILE_EMAIL_EDITABLE)
  @ResponseBody
  public AjaxReturnObject<String> changeEmail(
      @RequestParam("newEmailInput") String newEmailInput,
      @RequestParam("newEmailConfirm") String newEmailConfirm,
      @RequestParam("emailPasswordInput") String emailPasswordInput,
      HttpServletRequest request,
      HttpSession session,
      Principal principal) {

    User user = userManager.getUserByUsername(principal.getName(), true);

    String email = StringUtils.trim(newEmailInput);
    String emailConfirm = StringUtils.trim(newEmailConfirm);
    String password = StringUtils.trim(emailPasswordInput);

    String errorMsg = null;
    if (email.length() >= User.DEFAULT_MAXFIELD_LEN) {
      errorMsg =
          "Email address is too long - should be less than "
              + User.DEFAULT_MAXFIELD_LEN
              + " characters";
    } else if (checkInputString(email)
        || checkInputString(emailConfirm)
        || checkInputString(password)) {
      errorMsg = "Please enter data in all fields";
    } else if (!reauthenticator.reauthenticate(user, password)) {
      logAuthenticationFailure(request, user);
      errorMsg = "The current password is incorrect";
    } else if (!email.equalsIgnoreCase(emailConfirm)) {
      errorMsg = "New email field does not match the confirm email";
    } else if (email.equals(user.getEmail())) {
      errorMsg = "Provided email is the same as your current email";
    } else if (CollectionUtils.isNotEmpty(userManager.getUserByEmail(email))) {
      errorMsg = "There is already user registered with this email";
    }
    if (errorMsg != null) {
      return new AjaxReturnObject<>(null, ErrorList.of(errorMsg));
    }

    if (properties.isCloud()) {
      cloudUserManager.emailChangeRequested(user, email, request.getRemoteAddr());
      return new AjaxReturnObject<>("VERIFICATION", null);
    }

    user = userManager.changeEmail(user, email);
    updateSessionUser(user, session);
    return new AjaxReturnObject<>("SUCCESS", null);
  }

  @PostMapping("/ajax/editProfile")
  @ResponseBody
  public AjaxReturnObject<UserPublicInfo> editProfile(
      @RequestParam("firstNameInput") String firstNameInput,
      @RequestParam("surnameInput") String surnameInput,
      @RequestParam(value = "newAffiliation", required = false) String affiliation,
      @RequestParam("externalLinkInput") String externalLinkInput,
      @RequestParam("linkDescriptionInput") String linkDescriptionInput,
      @RequestParam("additionalInfoArea") String additionalInfoArea,
      Principal principal,
      HttpSession session) {

    User user = userManager.getUserByUsername(principal.getName(), true);
    UserProfile userProfile = userProfileManager.getUserProfile(user);
    firstNameInput = firstNameInput.trim();
    surnameInput = surnameInput.trim();
    if (!StringUtils.isEmpty(affiliation)) {
      affiliation = affiliation.trim();
    }

    ErrorList errors;
    errors = checkIfNameEditedWhenShouldntBe(user, firstNameInput, surnameInput);
    if (errors.hasErrorMessages()) {
      return new AjaxReturnObject<>(null, errors);
    }
    user.setFirstName(firstNameInput);
    user.setLastName(surnameInput);
    if (!StringUtils.isEmpty(affiliation) && properties.isCloud()) {

      if (affiliation.length() > User.DEFAULT_MAXFIELD_LEN) {
        errors = ErrorList.of(getText(ERRORS_MAXLENGTH, new String[] {AFFILIATION, "255"}));
        return new AjaxReturnObject<>(null, errors);
      } else {
        user.setAffiliation(affiliation, ProductType.COMMUNITY);
      }

    } else if (StringUtils.isEmpty(affiliation) && properties.isCloud()) {
      errors = ErrorList.of(getText(ERRORS_REQUIRED, new String[] {"Affiliation"}));
      return new AjaxReturnObject<>(null, errors);
    }

    errors = inputValidator.validateAndGetErrorList(user, new UserDataValidator());
    if (errors != null) {
      return new AjaxReturnObject<>(null, errors);
    }

    userProfile.setExternalLinkDisplay(linkDescriptionInput);
    if (userProfile.isTooLong(externalLinkInput)) {
      ErrorList el =
          ErrorList.of(
              getText(ERRORS_MAXLENGTH, new String[] {"Link", UserProfile.MAX_FIELD_LENG + ""}));
      return new AjaxReturnObject<>(null, el);
    }
    userProfile.setExternalLinkURL(externalLinkInput);
    userProfile.setProfileText(additionalInfoArea);

    user = userManager.save(user);
    userProfileManager.saveUserProfile(userProfile);

    if (properties.isCloud()) {
      organisationManager.checkAndSaveNonApprovedOrganisation(user);
    }

    UserPublicInfo pui = user.toPublicInfo();
    pui.setProfile(userProfile);
    updateSessionUser(user, session);
    return new AjaxReturnObject<>(pui, null);
  }

  // RSPAC-1208
  private ErrorList checkIfNameEditedWhenShouldntBe(
      User user, String firstNameInput, String surnameInput) {
    ErrorList el = new ErrorList();
    if ((!user.getFirstName().equals(firstNameInput)) && (!properties.isProfileNameEditable())) {
      el.addErrorMsg(authGenerator.getFailedMessage(user, " edit first name"));
    }
    if ((!user.getLastName().equals(surnameInput)) && (!properties.isProfileNameEditable())) {
      el.addErrorMsg(authGenerator.getFailedMessage(user, " edit last name"));
    }
    return el;
  }

  private boolean checkInputString(String str) {
    return str == null || str.isEmpty();
  }

  private boolean checkPermissions(User sessionUser, User user) {
    return permissionUtils.isPermitted(user, PermissionType.WRITE, sessionUser);
  }

  /**
   * File uploader for profile image
   *
   * @param imageFile the uplaoded file, must be < MAX_PROFILE_SIZE size
   * @return
   * @throws IOException
   */
  // this only modifies subject's profile and everyone is allowed to edit their own profile
  @PostMapping("/profileImage/upload")
  @ResponseBody
  public void postProfileImage(
      @RequestParam("imageFile") MultipartFile imageFile, Principal subject) throws IOException {

    final int maxWidth = 124;
    final int size = 1000;

    if (imageFile.getSize() > MAX_PROFILE_SIZE) {
      throw new IOException(
          "Profile image size is too big - must be smaller than " + MAX_PROFILE_SIZE);
    }
    String extension = MediaUtils.getExtension(imageFile.getOriginalFilename());
    if (!MediaUtils.isImageFile(extension)) {
      log.warn("Attempting to save a possible non-image file with extension[{}]", extension);
      throwUnacceptableImage(imageFile);
    }

    Optional<BufferedImage> scaledImage =
        ImageUtils.scale(
            imageFile.getInputStream(), maxWidth, getExtension(imageFile.getOriginalFilename()));
    if (!scaledImage.isPresent()) {
      log.warn("Couldn't scale image [{}]", imageFile.getOriginalFilename());
      throwUnacceptableImage(imageFile);
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream(size);

    ImageIO.write(scaledImage.get(), "png", baos);
    baos.flush();

    User user = userManager.getUserByUsername(subject.getName());
    UserProfile up = userProfileManager.getUserProfile(user);
    ImageBlob image = new ImageBlob(baos.toByteArray());
    up.setProfilePicture(image);
    userProfileManager.saveUserProfile(up);
  }

  private void throwUnacceptableImage(MultipartFile imageFile) {
    throw new IllegalArgumentException(
        String.format(
            "RSpace could not interpret  file %s as an image file",
            imageFile.getOriginalFilename()));
  }

  /** Get the profile image */
  @GetMapping("/profileImage/{profileId}/{pictureId}")
  public ResponseEntity<byte[]> getProfileThumbnail(
      @PathVariable("profileId") Long profileId, @PathVariable("pictureId") Long pictureId)
      throws IOException {

    UserProfile userProfile = userProfileManager.getUserProfile(profileId);
    ImageBlob profilePicture = userProfile.getProfilePicture();
    byte[] pictureBytes = null;
    // ensure that pictureId provided is a profile picture and not some other db image
    if (profilePicture != null && profilePicture.getId().equals(pictureId)) {
      pictureBytes = profilePicture.getData();
    } else {
      pictureBytes = getDefaultImageBytes();
    }

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);
    setCacheTimeInBrowser(ResponseUtil.YEAR, null, headers);

    return new ResponseEntity<>(pictureBytes, headers, HttpStatus.OK);
  }

  private byte[] getDefaultImageBytes() throws IOException {
    return IOUtils.toByteArray(
        getServletContext().getResourceAsStream(IMAGES_DEFAULT_PROFILE_IMAGE_PNG));
  }

  /**
   * Updates Preferences
   *
   * @param prefsToEnable notificationCheckboxes; these are checked checkboxes for notification
   *     choices, and the values are string values of the preference Enums..
   * @param principal
   * @return jsp view name
   * @throws IOException
   */
  @ResponseBody
  @PostMapping("/ajax/messageSettings")
  public AjaxReturnObject<String> updatePreferences(
      @RequestParam(value = "messageCheckboxes", required = false) List<String> prefsToEnable,
      Principal principal,
      HttpServletRequest req) {
    // handle case where message settings are empty and all should be switched off. rspac-1967
    if (CollectionUtils.isEmpty(prefsToEnable)) {
      prefsToEnable = Collections.emptyList();
    }
    User user = userManager.getUserByUsername(principal.getName());
    Set<UserPreference> allPrefs = userManager.getUserAndPreferencesForUser(principal.getName());
    if (!allPrefs.isEmpty()) {
      // assume at this stage that all are booleans; need to refactor when dealing with other
      // preferences
      for (Preference pref : Preference.values()) {
        if (pref.getPrefType().equals(SettingsType.BOOLEAN) && pref.isMessagingPreference()) {
          boolean preferenceValue = prefsToEnable.contains(pref.toString());
          userManager.setPreference(pref, "" + preferenceValue, user.getUsername());
        }
      }
      userManager.setAsPrivateProfile(prefsToEnable.contains("PRIVATE_PROFILE"), user);
    }
    analyticsManager.usersPreferencesChanged(user, req);
    return new AjaxReturnObject<>(getText("userProfile.messageSettingsChanged.msg"), null);
  }

  /**
   * @param preference
   * @param principal
   * @return
   */
  @ResponseBody
  @GetMapping("/ajax/preference")
  public String getPreferenceValue(
      @RequestParam(value = "preference") String preference, Principal principal) {

    Set<UserPreference> allPrefs = userManager.getUserAndPreferencesForUser(principal.getName());
    for (UserPreference pref : allPrefs) {
      if (pref.getPreference().toString().equals(preference)) {
        return pref.getValue();
      }
    }
    return null;
  }

  @ResponseBody
  @PostMapping("/ajax/preference")
  public AjaxReturnObject<String> updatePreferenceValue(
      @RequestParam(value = "preference") String preferenceName,
      @RequestParam(value = "value") String value,
      Principal principal,
      HttpServletRequest req) {

    Preference pref = Preference.valueOf(preferenceName);
    User user = userManager.getUserByUsername(principal.getName());
    UserPreference updatedPreference =
        userManager.setPreference(pref, "" + value, user.getUsername());
    analyticsManager.usersPreferencesChanged(user, req);
    return new AjaxReturnObject<>(updatedPreference.getValue(), null);
  }

  @PostMapping("/ajax/apiKey")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = "password")
  public @ResponseBody AjaxReturnObject<ApiKeyInfo> generateApiKey(
      @RequestParam("password") String pwd) {
    if (isEmpty(pwd)) {
      return new AjaxReturnObject<>(
          null, ErrorList.of(getText(ERRORS_REQUIRED, new Object[] {"Password"})));
    }

    if (SecurityUtils.getSubject().isRunAs() && !sysadminApiKeyGeneration) {
      return new AjaxReturnObject<>(
          null, ErrorList.of("API key value cannot be accessed when 'operating as' another user"));
    }

    User user = userManager.getAuthenticatedUserInSession();
    if (!reauthenticator.reauthenticate(user, pwd)) {
      SECURITY_LOG.warn(
          "User {} tried to create a new API key but authentication failed", user.getUsername());
      return new AjaxReturnObject<>(null, ErrorList.of("Invalid password"));
    }

    UserApiKey apiKey = apiKeyMgr.createKeyForUser(user);
    SECURITY_LOG.info("User {} created new API key", user.getUsername());
    return new AjaxReturnObject<>(
        new ApiKeyInfo(apiKey.getApiKey(), true, true, true, "", 0L), null);
  }

  @DeleteMapping("/ajax/apiKey")
  public @ResponseBody Integer revokeApiKey() {
    User user = userManager.getAuthenticatedUserInSession();
    int result = apiKeyMgr.revokeKeyForUser(user);
    SECURITY_LOG.info("User {} revoked API key", user.getUsername());
    return result;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ApiKeyInfo {
    /** The actual API key */
    private String key = null;

    /** Whether or not key can be revoked */
    private boolean revokable = false;

    /** Whether or not key can be regenerated */
    private boolean regenerable = true;

    /** Whether or not key is enabled */
    private boolean enabled = true;

    /** If key is not available, an explanatory message */
    private String message = "";

    /** Age in days of the API key */
    private long age;
  }

  @GetMapping("/ajax/apiKeyDisplayInfo")
  public @ResponseBody AjaxReturnObject<ApiKeyInfo> getApiKeyDisplayInfo() {
    User user = userManager.getAuthenticatedUserInSession();
    ApiKeyInfo rc = new ApiKeyInfo();
    if (apiKeyMgr.isKeyExistingForUser(user)) {
      rc.setRevokable(true);
      rc.setAge(apiKeyMgr.calculateApiKeyAgeForUser(user));
    }
    ServiceOperationResult<String> available = availabilityHandler.isAvailable(user, null);
    rc.setEnabled(available.isSucceeded());
    if (!available.isSucceeded()) {
      rc.setMessage(available.getEntity());
    }
    return new AjaxReturnObject<>(rc, null);
  }

  @GetMapping("/ajax/apiKeyValue")
  public @ResponseBody AjaxReturnObject<String> getApiKeyValue() {
    if (SecurityUtils.getSubject().isRunAs()) {
      return new AjaxReturnObject<>(
          null, ErrorList.of("API key value cannot be accessed when 'operating as' another user"));
    }
    User user = userManager.getAuthenticatedUserInSession();
    SECURITY_LOG.info("User [{}] asked to see their API key", user.getUsername());

    if (!apiKeyMgr.isKeyExistingForUser(user)) {
      return new AjaxReturnObject<>(null, ErrorList.of("API key is not set"));
    }
    return new AjaxReturnObject<>(API_KEY_IS_ACTIVE, null);
  }

  /** Shows a list of created OAuth apps on the user's profile page */
  @GetMapping("/ajax/oAuthApps")
  @ResponseBody
  public AjaxReturnObject<PublicOAuthApps> getOAuthApps() {
    User appDeveloper = userManager.getAuthenticatedUserInSession();
    return new AjaxReturnObject<>(new PublicOAuthApps(oAuthAppManager.getApps(appDeveloper)), null);
  }

  @PostMapping("/ajax/oAuthApps/{oAuthAppName}")
  @ResponseBody
  public AjaxReturnObject<OAuthAppInfo> addOAuthApp(@PathVariable String oAuthAppName) {
    User appDeveloper = userManager.getAuthenticatedUserInSession();
    ServiceOperationResult<OAuthAppInfo> result =
        oAuthAppManager.addApp(appDeveloper, oAuthAppName);
    return AjaxReturnObject.fromSOR(result);
  }

  @DeleteMapping("/ajax/oAuthApps/{clientId}")
  @ResponseBody
  public AjaxReturnObject<Void> removeOAuthApp(@PathVariable String clientId) {
    User appDeveloper = userManager.getAuthenticatedUserInSession();
    ServiceOperationResult<Void> result = oAuthTokenManager.removeAllTokens(appDeveloper, clientId);
    if (result.isSucceeded()) {
      result = oAuthAppManager.removeApp(userManager.getAuthenticatedUserInSession(), clientId);
    }
    return AjaxReturnObject.fromSOR(result);
  }

  /** Shows list of connected OAuth apps on the user's profile page */
  @GetMapping("/ajax/oAuthConnectedApps")
  @ResponseBody
  public AjaxReturnObject<PublicOAuthConnApps> getOAuthConnectedApps() {
    User user = userManager.getAuthenticatedUserInSession();
    List<PublicOAuthConnAppInfo> result = new ArrayList<>();
    for (OAuthToken token : oAuthTokenManager.getTokensForUser(user)) {
      Optional<PublicOAuthAppInfo> appInfo = oAuthAppManager.getApp(token.getClientId());
      if (!appInfo.isPresent()) {
        continue;
      }
      result.add(new PublicOAuthConnAppInfo(token, appInfo.get()));
    }
    return new AjaxReturnObject<>(new PublicOAuthConnApps(result), null);
  }

  @DeleteMapping("/ajax/oAuthConnectedApps/{clientId}")
  @ResponseBody
  public AjaxReturnObject<Boolean> disconnectOAuthConnectedApp(@PathVariable String clientId) {
    User user = userManager.getAuthenticatedUserInSession();
    ServiceOperationResult<OAuthToken> result = oAuthTokenManager.removeToken(user, clientId);
    return AjaxReturnObject.fromSOR(result, result2 -> Boolean.TRUE);
  }

  private static final String INVENTORY_CLIENT_ID = "rsInventoryWebClient";
  private static final String INVENTORY_CLIENT_SECRET = "rsInventoryPublicSecret";

  protected RetryConfig retryConfigForNewJwtTokenCreation;

  @PostConstruct
  public void init() {
    retryConfigForNewJwtTokenCreation = buildRetryConfigForNewJwtTokenCreation();
  }

  @ResponseBody
  @GetMapping("/ajax/inventoryOauthToken")
  public AjaxReturnObject<String> getInventoryOauthToken(Principal principal) {
    User user = userManager.getUserByUsername(principal.getName());

    Retry retry = Retry.of("jwtTokenGeneration", retryConfigForNewJwtTokenCreation);
    Callable<NewOAuthTokenResponse> updateWithRetry =
        Retry.decorateCallable(
            retry,
            () ->
                oAuthTokenManager
                    .createNewJwtToken(
                        INVENTORY_CLIENT_ID,
                        INVENTORY_CLIENT_SECRET,
                        user,
                        OAuthToken.DEFAULT_SCOPE)
                    .getEntity());
    NewOAuthTokenResponse createdToken = Try.ofCallable(updateWithRetry).get();

    return new AjaxReturnObject<>(createdToken.getAccessToken(), null);
  }

  private RetryConfig buildRetryConfigForNewJwtTokenCreation() {
    IntervalFunction intervalWithCustomExponentialBackoff =
        IntervalFunction.ofExponentialRandomBackoff(500, 2d);
    return RetryConfig.custom()
        .maxAttempts(3)
        .intervalFunction(intervalWithCustomExponentialBackoff)
        .retryExceptions(DataIntegrityViolationException.class)
        .build();
  }

  private static final String PROFILE_IMAGE_LINK_FMT = "/userform/profileImage/%d/%d";

  /**
   * Gets mini-profile information for a user RSPAC-683 without
   *
   * @param userId ID of user whose profile should be retrieved.
   * @return AjaxReturnObject
   */
  @GetMapping("/ajax/miniprofile/{userId}")
  @ResponseBody
  public AjaxReturnObject<MiniProfile> miniprofile(@PathVariable(name = "userId") Long userId) {
    User sessionUser = userManager.getAuthenticatedUserInSession();
    User profileUser = userManager.getUser(userId + "");
    if (properties.isProfileHidingEnabled() && profileUser.isPrivateProfile()) {
      userManager.populateConnectedUserList(sessionUser);
      if (!sessionUser.isConnectedToUser(profileUser)) {
        String errorMsg = getResourceNotFoundMessage("User", userId);
        return new AjaxReturnObject<>(null, ErrorList.of(errorMsg));
      }
    }

    List<GroupInfo> groups =
        profileUser.getGroups().stream().map(g -> new GroupInfo(g, profileUser)).collect(toList());
    UserProfile up = userProfileManager.getUserProfile(profileUser);
    Long profileImageId = getProfileImageId(up);
    String profileImageLink = String.format(PROFILE_IMAGE_LINK_FMT, up.getId(), profileImageId);
    Long timestampLong = getLastLoginTime(profileUser, sessionUser);
    MiniProfile mp =
        MiniProfile.builder()
            .email(profileUser.getEmail())
            .fullname(profileUser.getFullName())
            .username(profileUser.getUsername())
            .profileImageLink(profileImageLink)
            .lastLogin(timestampLong)
            .accountEnabled(profileUser.isEnabled())
            .groups(groups)
            .build();
    return new AjaxReturnObject<>(mp, null);
  }

  private Long getLastLoginTime(User profileUser, User sessionUser) {
    if (profileUser.equals(sessionUser)
        || systemPropertyPermissionUtils.isPropertyAllowed(
            profileUser, "publicLastLogin.available")) {
      return profileUser.getLastLogin() != null ? profileUser.getLastLogin().getTime() : null;
    } else {
      return null;
    }
  }

  @Data
  @NoArgsConstructor
  public static class UserAccountView {
    private String fullname;
    private String username;
    private AccountEventType eventType;
    private Long id;

    @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
    @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
    private Long timestamp;

    public UserAccountView(UserAccountEvent userAccountEvent) {
      this.fullname = userAccountEvent.getUser().getFullName();
      this.username = userAccountEvent.getUser().getUsername();
      this.eventType = userAccountEvent.getAccountEventType();
      this.id = userAccountEvent.getUser().getId();
      this.timestamp = userAccountEvent.getTimestamp().getTime();
    }
  }

  /**
   * UserAccount events are viewable by:
   *
   * <ul>
   *   <li>Sysadmins/community admins
   *   <li>Any member of a group that the queried user is in
   *       <ul>
   *
   * @param userId
   * @return A List of UserAccountViews; this will be empty in the case there are no events or the
   *     subject is not authorised to view.
   */
  @GetMapping("/ajax/accountEventsByUser/{userId}")
  @ResponseBody
  public AjaxReturnObject<List<UserAccountView>> getAccountEventsByUser(
      @PathVariable("userId") Long userId) {
    User sessionUser = userManager.getAuthenticatedUserInSession();
    User toQuery = userManager.get(userId);
    List<UserAccountView> rc = Collections.emptyList();
    if (isAccountEventsViewable(sessionUser, toQuery)) {
      rc =
          userManager.getAccountEventsForUser(toQuery).stream()
              .map(UserAccountView::new)
              .collect(toList());
    }

    return new AjaxReturnObject<>(rc, null);
  }

  private boolean isAccountEventsViewable(User sessionUser, User toQuery) {
    return sessionUser.equals(toQuery)
        || sessionUser.hasAdminRole()
        || sessionUser.getAllGroupMembers().contains(toQuery);
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class UserGroupInfo {
    private Long groupId;
    private Boolean autoshareEnabled = false;
    private Boolean privateGroup = false;
    private String groupDisplayName;
    private String roleInGroup;
    private Boolean labGroup = true;
    // whether or not autoshare folder is set.
    private Boolean autoshareFolderSet = false;

    public UserGroupInfo(UserGroup ug) {
      this.groupId = ug.getGroup().getId();
      this.autoshareEnabled = ug.isAutoshareEnabled();
      this.groupDisplayName = ug.getGroup().getDisplayName();
      this.autoshareFolderSet = ug.getAutoShareFolder() != null;
      if (RoleInGroup.DEFAULT.equals(ug.getRoleInGroup())) {
        this.roleInGroup = "USER";
      } else {
        this.roleInGroup = ug.getRoleInGroup().name();
      }
      this.labGroup = ug.getGroup().isLabGroup();
    }

    public UserGroupInfo(boolean isPrivate) {
      this.privateGroup = isPrivate;
    }
  }

  /**
   * Populates the 'LabGroups' section of User Profile
   *
   * @param userId
   * @return
   */
  @GetMapping("/ajax/userGroupInfo/{userId}")
  @ResponseBody
  public AjaxReturnObject<List<UserGroupInfo>> getUserGroupInfo(
      @PathVariable("userId") Long userId) {
    User profileUser = userManager.get(userId);
    User subject = userManager.getAuthenticatedUserInSession();
    List<UserGroupInfo> rc = new ArrayList<>();

    boolean hasAnyPrivateGroups =
        profileUser.getGroups().stream().anyMatch(this::isPrivateGroupProfile);
    if (hasAnyPrivateGroups) {
      userManager.populateConnectedGroupList(subject);
    }
    for (UserGroup ug : profileUser.getUserGroups()) {
      Group grp = ug.getGroup();
      // don't include info if is private profile
      if (isPrivateGroupProfile(grp) && !subject.isConnectedToGroup(grp)) {

        rc.add(new UserGroupInfo(true));
      } else {
        rc.add(new UserGroupInfo(ug));
      }
    }
    return new AjaxReturnObject<>(rc, null);
  }

  private boolean isPrivateGroupProfile(Group grp) {
    return grp.isPrivateProfile() && properties.isProfileHidingEnabled();
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class EnableAutoshareConfig {
    @Size(max = 255)
    private String autoshareFolderName;
  }

  @Data
  public static class AutoshareResponse {
    private boolean async = false;
  }

  /** Enables autoshare for user in group. */
  @PostMapping("/ajax/enableAutoshare/{groupId}/{userId}")
  @ResponseBody
  public AjaxReturnObject<AutoshareResponse> enableAutoshare(
      @Valid @RequestBody EnableAutoshareConfig config,
      BindingResult errors,
      @PathVariable Long groupId,
      @PathVariable Long userId) {

    if (errors.hasErrors()) {
      ErrorList el = new ErrorList();
      inputValidator.populateErrorList(errors, el);
      return new AjaxReturnObject<>(null, el);
    }

    User subject = userManager.getAuthenticatedUserInSession();
    Group group = groupManager.getGroupWithCommunities(groupId);
    User targetUser = userManager.get(userId);

    String errorMessage = canManageAutoshare(subject, targetUser, group, true);
    if (errorMessage != null) {
      log.info(errorMessage);
      return new AjaxReturnObject<>(null, ErrorList.of(errorMessage));
    }

    Group updatedGroup = groupManager.enableAutoshareForUser(targetUser, group.getId());

    String name = null;
    if (!StringUtils.isBlank(config.getAutoshareFolderName())) {
      name =
          config.getAutoshareFolderName().length() < 4
              ? config.getAutoshareFolderName()
              : abbreviate(config.getAutoshareFolderName(), BaseRecord.DEFAULT_VARCHAR_LENGTH);
    }
    Folder autoshareFolder = groupManager.createAutoshareFolder(targetUser, updatedGroup, name);
    targetUser = userManager.getUserByUsername(targetUser.getUsername(), true);

    Long docsAndNotebookCount = getDocsAndNotebookCount(targetUser);
    AutoshareResponse resp = new AutoshareResponse();

    if (docsAndNotebookCount > MAX_TO_AUTO_SHARE_SYNC) {
      log.info("autosharing {} items, async initiated...", docsAndNotebookCount);
      autoshareManager.asyncBulkShareAllRecords(targetUser, updatedGroup, autoshareFolder);
      resp.setAsync(true);
    } else {
      log.info("autosharing {} items synchronously", docsAndNotebookCount);
      autoshareManager.bulkShareAllRecords(targetUser, updatedGroup, autoshareFolder);
      resp.setAsync(false);
    }

    return new AjaxReturnObject<>(resp, null);
  }

  /**
   * Disable autoshare for user in group.
   *
   * @return true if unsharing completed without exception, false otherwise
   */
  @PostMapping("/ajax/disableAutoshare/{groupId}/{userId}")
  @ResponseBody
  public AjaxReturnObject<AutoshareResponse> disableAutoshare(
      @PathVariable Long groupId, @PathVariable Long userId) {

    User subject = userManager.getAuthenticatedUserInSession();
    User targetUser = userManager.get(userId);
    Group group = groupManager.getGroupWithCommunities(groupId);

    String errorMessage = canManageAutoshare(subject, targetUser, group, false);
    if (errorMessage != null) {
      log.info(errorMessage);
      return new AjaxReturnObject<>(null, ErrorList.of(errorMessage));
    }

    Group updatedGroup = groupManager.disableAutoshareForUser(targetUser, group.getId());
    Long docsAndNotebookCount = getDocsAndNotebookCount(targetUser);
    AutoshareResponse resp = new AutoshareResponse();

    if (docsAndNotebookCount > MAX_TO_AUTO_SHARE_SYNC) {
      log.info("disabling autosharing {} items, async initiated...", docsAndNotebookCount);
      autoshareManager.asyncBulkUnshareAllRecords(targetUser, updatedGroup);
      resp.setAsync(true);
    } else {
      log.info("disabling autosharing {} items synchronously", docsAndNotebookCount);
      autoshareManager.bulkUnshareAllRecords(targetUser, updatedGroup);
      resp.setAsync(false);
    }

    return new AjaxReturnObject<>(resp, null);
  }

  /**
   * @return null if can manage, error message otherwise
   */
  private String canManageAutoshare(
      User subject, User targetUser, Group group, boolean targetAutoshareStatus) {

    if (!subject.equals(targetUser)
        && !systemPropertyPermissionUtils.isPropertyAllowed(group, "group_autosharing.available")) {
      return "Please contact your system administrator to enable this feature";
    } else if (!group.isLabGroup()) {
      return "Can only manage autoshare status for lab groups";
    }

    boolean isAutoshareEnabled =
        groupManager.getGroup(group.getId()).getUserGroupForUser(targetUser).isAutoshareEnabled();
    if (isAutoshareEnabled == targetAutoshareStatus) {
      return String.format(
          "Autosharing for user %s in group %s has already been set to %s",
          subject.getUsername(), group.getDisplayName(), targetAutoshareStatus);
    }

    if (subject.equals(targetUser)
        || targetUser.getGroupMembersWithViewAll(group).contains(subject)) {
      return null;
    }

    return String.format(
        "User %s attempted to modify user's %s autoshare status without permission",
        subject.getUsername(), targetUser.getUsername());
  }

  /** Boolean query as to whether autoshare is currently in progress. */
  @GetMapping("/ajax/autoshareInProgress")
  @ResponseBody
  public AjaxReturnObject<Boolean> isAutoshareInProgress() {
    User subject = userManager.getAuthenticatedUserInSession();
    Boolean isInProgress = autoshareManager.isBulkShareInProgress(subject);
    return new AjaxReturnObject<>(isInProgress, null);
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class AutoshareStatus {
    private Long groupId;
    private Boolean autoshareEnabled = false;

    AutoshareStatus(UserGroup ug) {
      this.groupId = ug.getGroup().getId();
      this.autoshareEnabled = ug.isAutoshareEnabled();
    }
  }

  /** Gets list of autoshare statuses for user. */
  @GetMapping("/ajax/autoshareStatus")
  @ResponseBody
  public AjaxReturnObject<List<AutoshareStatus>> autoshareStatus(Principal subjectPrincipal) {
    User subject = userManager.getUserByUsername(subjectPrincipal.getName());
    List<AutoshareStatus> statuses =
        subject.getUserGroups().stream().map(AutoshareStatus::new).collect(Collectors.toList());
    return new AjaxReturnObject<>(statuses, null);
  }

  private Long getDocsAndNotebookCount(User subject) {
    return recordManager.getRecordCountForUser(
        new RecordTypeFilter(EnumSet.of(RecordType.NORMAL, RecordType.NOTEBOOK), true), subject);
  }

  @GetMapping("/ajax/enforcedOntologies")
  @ResponseBody
  public boolean isOntologiesEnforcementActiveForUser() {
    User subject = userManager.getAuthenticatedUserInSession();
    boolean result = subject.getGroups().stream().anyMatch(Group::isEnforceOntologies);
    return result;
  }

  /**
   * Endpoint allowing user to self-declare as a PI. See RSPAC-2588 for more details.
   *
   * @return true if successful, and user now has PI role
   */
  @PostMapping("/ajax/selfDeclareAsPi")
  @ResponseBody
  public AjaxReturnObject<Boolean> selfDeclareAsPi(Principal subject) {
    if (!properties.isSSOSelfDeclarePiEnabled()) {
      return new AjaxReturnObject<>(false, ErrorList.of("Self-declaring PI not enabled"));
    }

    User user = userManager.getUserByUsername(subject.getName(), true);
    if (user.isPI()) {
      return new AjaxReturnObject<>(false, ErrorList.of("User is already a PI."));
    } else if (!user.isAllowedPiRole()) {
      return new AjaxReturnObject<>(
          false, ErrorList.of("User cannot self-declare as a PI (isAllowedPiRole=false)."));
    }
    userRoleHandler.doGrantGlobalPiRoleToUser(user);
    permissionUtils.refreshCache();
    return new AjaxReturnObject<>(user.isPI(), null);
  }

  /**
   * Endpoint allowing user to self-declare as a regular user, rather than a PI. See RSPAC-2588 for
   * more details.
   *
   * @return true if successful, and user no longer has PI role
   */
  @PostMapping("/ajax/selfDeclareAsRegularUser")
  @ResponseBody
  public AjaxReturnObject<Boolean> selfDeclareAsRegularUser(Principal subject) {
    if (!properties.isSSOSelfDeclarePiEnabled()) {
      return new AjaxReturnObject<>(false, ErrorList.of("Self-declaring PI not enabled."));
    }
    User user = userManager.getUserByUsername(subject.getName(), true);
    if (!user.isPI()) {
      return new AjaxReturnObject<>(false, ErrorList.of("User is not a PI."));
    }
    try {
      userRoleHandler.doRevokeGlobalPiRoleFromUser(user);
    } catch (IllegalStateException ise) {
      log.warn("Couldn't revoke pi role", ise);
      return new AjaxReturnObject<>(
          false,
          ErrorList.of(
              "You must delete "
                  + "or transfer over all LabGroups before you can remove your PI status."));
    }
    permissionUtils.refreshCache();
    return new AjaxReturnObject<>(!user.isPI(), null);
  }
}

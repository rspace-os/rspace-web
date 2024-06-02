package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.session.SessionAttributeUtils.USER_INFO;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Group;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.UserPreference;
import com.researchspace.model.UserProfile;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.dto.MiniProfile;
import com.researchspace.model.dtos.PreferencesCommand;
import com.researchspace.model.events.AccountEventType;
import com.researchspace.model.events.UserAccountEvent;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.frontend.OAuthAppInfo;
import com.researchspace.model.frontend.PublicOAuthApps;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.preference.PreferenceCategory;
import com.researchspace.model.preference.SettingsType;
import com.researchspace.model.record.Folder;
import com.researchspace.service.GroupManager;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.UserProfileManager;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestGroup;
import com.researchspace.webapp.controller.UserProfileController.ApiInfo;
import java.io.IOException;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.commons.lang.RandomStringUtils;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.ui.ModelMap;

@WebAppConfiguration
public class UserProfileControllerMVCIT extends MVCTestBase {

  private @Autowired UserProfileManager userProfileManager;
  private @Autowired SystemPropertyManager systemPropertyManager;
  private @Autowired GroupManager groupManager;

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void getUserFormTest() throws Exception {
    allowPublicLastLogin(); // setup
    User user = createInitAndLoginAnyUser();
    user = forceSetLastLogin(user);
    MvcResult result =
        mockMvc
            .perform(get("/userform").principal(new MockPrincipal(user.getUsername())))
            .andExpect(status().isOk())
            // check user can edit his own profile
            .andExpect(model().attributeExists("user"))
            .andExpect(model().attribute("canEdit", true))
            .andExpect(model().attribute("canEditEmail", true))
            .andExpect(model().attribute("canEditPassword", true))
            .andExpect(model().attribute("canEditVerificationPassword", false))
            .andExpect(model().attributeExists("preferences"))
            .andExpect(model().attribute("showLastLoginDate", Matchers.notNullValue(Long.class)))
            .andReturn();
    ModelMap map = result.getModelAndView().getModelMap();
    PreferencesCommand comm = (PreferencesCommand) map.get("preferences");

    // check all prefs are available for display, see RSPAC-1339 for a pref only applicable to PIs.
    final int TOTAL_COUNT_OF_UNDISPLAYED_PREFS = 1;
    assertEquals(
        UserProfileController.desiredMessageDisplayOrder.size() - TOTAL_COUNT_OF_UNDISPLAYED_PREFS,
        comm.getPrefs().size());
    comm.getPrefs().stream().forEach(up -> assertNull(up.getUser()));

    // let's try seeing another user's profile
    User other = createAndSaveUser(CoreTestUtils.getRandomName(8));
    other = forceSetLastLogin(other);

    MvcResult result2 =
        mockMvc
            .perform(
                get("/userform").param("userId", other.getId() + "").principal(user::getUsername))
            .andExpect(status().isOk())
            // can't edit another user's profile, but can view
            .andExpect(model().attributeExists("user"))
            .andExpect(model().attribute("canEdit", false))
            .andExpect(model().attribute("canEditEmail", false))
            .andExpect(model().attribute("canEditPassword", false))
            .andExpect(model().attribute("showLastLoginDate", notNullValue(Long.class)))
            .andReturn();
    ModelMap map2 = result2.getModelAndView().getModelMap();
    // can't see preferences of another user.
    assertNull(map2.get("preferences"));

    // rspac-2234, deny public lastLogin:
    denyPublicLastLogin();
    mockMvc
        .perform(get("/userform").param("userId", other.getId() + "").principal(user::getUsername))
        .andExpect(model().attribute("showLastLoginDate", nullValue(Long.class)));
  }

  @Test
  public void getGoogleUserProfilePage() throws Exception {
    User user = createAndSaveUser(CoreTestUtils.getRandomName(8));
    user.setSignupSource(SignupSource.GOOGLE);
    userMgr.saveUser(user);

    logoutAndLoginAs(user);
    // for user who signed up with google, email and password shouldn't be editable
    mockMvc
        .perform(get("/userform").principal(new MockPrincipal(user.getUsername())))
        .andExpect(status().isOk())
        .andExpect(model().attribute("canEdit", true))
        .andExpect(model().attribute("canEditEmail", false))
        .andExpect(model().attribute("canEditPassword", false))
        .andExpect(model().attribute("canEditVerificationPassword", true))
        .andReturn();
  }

  @Test
  public void getProfileTest() throws Exception {

    User user = createAndSaveUser(CoreTestUtils.getRandomName(8));
    logoutAndLoginAs(user);
    // this retrieves user form and will generate default profile
    MvcResult res =
        mockMvc
            .perform(get("/userform").principal(new MockPrincipal(user.getUsername()))) //
            .andExpect(status().isOk())
            .andReturn();
    UserProfile profile = (UserProfile) res.getModelAndView().getModelMap().get("profile");

    // simulate profile pic upload
    simulateProfileImageUpload(profile);

    // now test we can stream it:
    mockMvc
        .perform(
            get(
                "/userform/profileImage/{profileid}/{pictureid}",
                profile.getId(),
                profile.getProfilePicture().getId()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andReturn();
    // now generate another user
    User user2 = createAndSaveUser(CoreTestUtils.getRandomName(8));
    logoutAndLoginAs(user);
    // and generate a basic profile with no profile image
    MvcResult res2 =
        mockMvc
            .perform(get("/userform").principal(new MockPrincipal(user2.getUsername()))) //
            .andExpect(status().isOk())
            .andReturn();
    UserProfile profile2 = (UserProfile) res2.getModelAndView().getModelMap().get("profile");

    // and will return default image if no image has been set
    mockMvc
        .perform(get("/userform/profileImage/{profileid}/-1", profile2.getId()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andReturn();

    // now, let's access another user's profile - their profile should load OK (RSPAC-208)
    mockMvc
        .perform(
            get("/userform/")
                .param("userId", user.getId() + "")
                .principal(new MockPrincipal(user2.getUniqueName())))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("profile"))
        .andReturn();
  }

  @Test
  public void testUploadFile() throws Exception {
    User user = createAndSaveUser(CoreTestUtils.getRandomName(8));
    logoutAndLoginAs(user);
    MockMultipartFile mf =
        new MockMultipartFile(
            "imageFile", "image.png", "png", getTestResourceFileStream("Picture1.png"));
    long size = mf.getSize();
    mockMvc
        .perform(
            fileUpload("/userform/profileImage/upload")
                .file(mf)
                .principal(new MockPrincipal(user.getUsername())))
        .andExpect(status().isOk())
        .andReturn();
    ImageBlob picture = userProfileManager.getUserProfile(user).getProfilePicture();
    assertNotNull(picture);
    assertTrue(picture.getData().length > 0);
  }

  private void simulateProfileImageUpload(UserProfile profile) throws IOException {
    byte[] img = RSpaceTestUtils.getResourceAsByteArray("tester.png");
    ImageBlob blob = new ImageBlob(img);
    profile.setProfilePicture(blob);
    userProfileManager.saveUserProfile(profile);
  }

  @Test
  public void changePasswordTest() throws Exception {
    User user = createAndSaveUser(CoreTestUtils.getRandomName(8));
    logoutAndLoginAs(user);

    String newPassword = RandomStringUtils.random(10);
    String newHint = RandomStringUtils.random(10);
    MvcResult result =
        mockMvc
            .perform(
                post("/userform/ajax/changePassword")
                    .param("currentPassword", TESTPASSWD)
                    .param("newPassword", newPassword)
                    .param("confirmPassword", newPassword)
                    .param("hintPassword", newHint)
                    .principal(user::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    assertTrue(result.getResponse().getContentAsString().contains("Password changed successfully"));

    String newPassword2 = RandomStringUtils.random(3);
    MvcResult result2 =
        mockMvc
            .perform(
                post("/userform/ajax/changePassword")
                    .param("currentPassword", newPassword)
                    .param("newPassword", newPassword2)
                    .param("confirmPassword", newPassword2)
                    .param("hintPassword", newHint)
                    .principal(user::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    assertTrue(
        result2
            .getResponse()
            .getContentAsString()
            .contains(getMsgFromResourceBundler("errors.invalidpwd").substring(0, 10)));

    String newPassword3 = RandomStringUtils.random(10);
    MvcResult result3 =
        mockMvc
            .perform(
                post("/userform/ajax/changePassword")
                    .param("currentPassword", newPassword)
                    .param("newPassword", newPassword3)
                    .param("confirmPassword", newPassword3 + "X")
                    .param("hintPassword", newHint)
                    .principal(user::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    String responseAsString = result3.getResponse().getContentAsString();
    assertTrue(
        "unexpected response: " + responseAsString,
        responseAsString.contains(getMsgFromResourceBundler("errors.password.conflict")));
  }

  @Test
  public void changeEmailTest() throws Exception {
    User user = createAndSaveUser(CoreTestUtils.getRandomName(8));
    logoutAndLoginAs(user);

    String newEmail = RandomStringUtils.random(10);
    MvcResult result =
        mockMvc
            .perform(
                post("/userform/ajax/changeEmail")
                    .param("newEmailInput", newEmail)
                    .param("newEmailConfirm", newEmail)
                    .param("emailPasswordInput", TESTPASSWD)
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(
                request()
                    .sessionAttribute(
                        USER_INFO, hasProperty("email", Matchers.equalTo(newEmail)))) // RSPAC-436
            .andExpect(status().isOk())
            .andReturn();

    Map json = parseJSONObjectFromResponseStream(result);
    assertEquals("SUCCESS", json.get("data"));

    String newEmail2 = RandomStringUtils.random(300);
    MvcResult result2 =
        mockMvc
            .perform(
                post("/userform/ajax/changeEmail")
                    .param("newEmailInput", newEmail2)
                    .param("newEmailConfirm", newEmail2)
                    .param("emailPasswordInput", TESTPASSWD)
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(status().isOk())
            .andReturn();

    ErrorList errorList = mvcUtils.getErrorListFromAjaxReturnObject(result2);
    assertEquals(1, errorList.getErrorMessages().size());
    assertEquals(
        "Email address is too long - should be less than 255 characters",
        errorList.getErrorMessages().get(0));
  }

  @Test
  // RSPAC-1208
  public void editProfileRejectsNAmeChangeIfNotAllowed() throws Exception {
    try {
      propertyHolder.setProfileNameEditable(false);
      User user = createAndSaveUser(CoreTestUtils.getRandomName(8));
      logoutAndLoginAs(user);
      String newfirstNAme = CoreTestUtils.getRandomName(8);
      String newLastNAme = CoreTestUtils.getRandomName(8);
      MvcResult result =
          postProfileUpdate(user, newfirstNAme, newLastNAme).andExpect(status().isOk()).andReturn();
      ErrorList rejected = mvcUtils.getErrorListFromAjaxReturnObject(result);
      assertEquals(2, rejected.getErrorMessages().size());
      user = userMgr.getUserByUsername(user.getUsername(), true);
      assertFalse(user.getFirstName().equals(newfirstNAme));
      assertFalse(user.getLastName().equals(newLastNAme));
    } finally {
      propertyHolder.setProfileNameEditable(true); // restore default
    }
  }

  @Test
  public void editProfileTest() throws Exception {
    User user = createAndSaveUser(CoreTestUtils.getRandomName(8));
    logoutAndLoginAs(user);
    String newfirstNAme = CoreTestUtils.getRandomName(8);
    String newLastNAme = CoreTestUtils.getRandomName(8);
    MvcResult result =
        postProfileUpdate(user, newfirstNAme, newLastNAme)
            .andExpect(status().isOk())
            .andExpect(
                request()
                    .sessionAttribute(
                        USER_INFO,
                        hasProperty("firstName", Matchers.equalTo(newfirstNAme)))) // RSPAC-436
            .andExpect(
                request()
                    .sessionAttribute(
                        USER_INFO,
                        hasProperty("lastName", Matchers.equalTo(newLastNAme)))) // RSPAC-436
            .andReturn();

    Map resp = parseJSONObjectFromResponseStream(result);
    assertNotNull(resp.get("data"));

    MvcResult result2 =
        mockMvc
            .perform(
                post("/userform/ajax/editProfile")
                    .param("firstNameInput", "")
                    .param("surnameInput", "")
                    .param("externalLinkInput", CoreTestUtils.getRandomName(8))
                    .param("linkDescriptionInput", CoreTestUtils.getRandomName(8))
                    .param("additionalInfoArea", CoreTestUtils.getRandomName(100))
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(status().isOk())
            .andReturn();
    Map resp2 = parseJSONObjectFromResponseStream(result2);
    assertNull(resp2.get("data"));

    MvcResult result3 =
        mockMvc
            .perform(
                post("/userform/ajax/editProfile")
                    .param("firstNameInput", CoreTestUtils.getRandomName(8))
                    .param("surnameInput", CoreTestUtils.getRandomName(8))
                    .param("externalLinkInput", CoreTestUtils.getRandomName(300))
                    .param("linkDescriptionInput", CoreTestUtils.getRandomName(8))
                    .param("additionalInfoArea", CoreTestUtils.getRandomName(100))
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(status().isOk())
            .andReturn();

    assertTrue(result3.getResponse().getContentAsString().contains("cannot be greater than"));
  }

  private ResultActions postProfileUpdate(User user, String newfirstNAme, String newLastNAme)
      throws Exception {
    return mockMvc.perform(
        post("/userform/ajax/editProfile")
            .param("firstNameInput", newfirstNAme)
            .param("surnameInput", newLastNAme)
            .param("externalLinkInput", CoreTestUtils.getRandomName(8))
            .param("linkDescriptionInput", CoreTestUtils.getRandomName(8))
            .param("additionalInfoArea", CoreTestUtils.getRandomName(100))
            .principal(new MockPrincipal(user.getUsername())));
  }

  @Test
  public void testGetPrefs() throws Exception {
    User user = createAndSaveUser(CoreTestUtils.getRandomName(8));
    logoutAndLoginAs(user);
    MvcResult result3 =
        mockMvc
            .perform(
                get("/userform/")
                    .param("userId", user.getId() + "")
                    .principal(new MockPrincipal(user.getUsername())))
            .andReturn();
    PreferencesCommand command =
        (PreferencesCommand) result3.getModelAndView().getModelMap().get("preferences");
    List<UserPreference> msgPrefs = command.getPrefs();
    msgPrefs.stream().forEach(up -> assertNotNull(up.getValue()));
    IntStream.range(0, msgPrefs.size())
        .forEach(
            i -> {
              Preference p = msgPrefs.get(i).getPreference();
              int indexInRefList = UserProfileController.desiredMessageDisplayOrder.indexOf(p);
              // there may be some user prefs not displayed for a particular user role, this tests
              // that
              // sort order is maintained
              assertTrue(indexInRefList != -1 && indexInRefList >= i);
            });
  }

  @Test
  public void setMessagePreferences() throws Exception {
    //	StructuredDocument sdoc=setUpLoginAndCreateADocument();
    // mimic cboxes to false except 1

    MvcResult result =
        mockMvc
            .perform(
                post("/userform/ajax/messageSettings")
                    .principal(mockPrincipal)
                    .param(
                        "messageCheckboxes",
                        Preference.NOTIFICATION_DOCUMENT_EDITED_PREF.toString()))
            .andReturn();

    assertThat(
        result.getResponse().getContentAsString(),
        containsString(getMsgFromResourceBundler("userProfile.messageSettingsChanged.msg")));

    Set<UserPreference> prefs = userMgr.getUserAndPreferencesForUser(piUser.getUsername());

    // this was passed in; should be true.
    assertBooleanPrefState(prefs, Preference.NOTIFICATION_DOCUMENT_EDITED_PREF, true);
    // this was not passed in from ui, therefore is not checked, should be false
    assertBooleanPrefState(prefs, Preference.NOTIFICATION_DOCUMENT_SHARED_PREF, false);

    // unset all prefs RSPAC-1967
    MvcResult result2 =
        mockMvc
            .perform(post("/userform/ajax/messageSettings").principal(mockPrincipal))
            .andReturn();

    Set<UserPreference> allPrefs = userMgr.getUserAndPreferencesForUser(piUser.getUsername());
    assertThat(
        allPrefs.stream().filter(this::isMessagePref).noneMatch(UserPreference::getValueAsBoolean),
        is(true));
  }

  boolean isMessagePref(UserPreference userPref) {
    return PreferenceCategory.MESSAGING.equals(userPref.getPreference().getCategory())
        || PreferenceCategory.MESSAGING_BROADCAST.equals(userPref.getPreference().getCategory());
  }

  /*
   * tests that a boolean preference is in the specified state
   */
  private void assertBooleanPrefState(Set<UserPreference> prefs, Preference pref, boolean state) {
    assert (pref.getPrefType().equals(SettingsType.BOOLEAN));
    boolean found = false;
    for (UserPreference up : prefs) {
      if (up.getPreference().equals(pref)) {
        if (state) {
          assertTrue(Boolean.valueOf(up.getValue()));
        } else {
          assertFalse(Boolean.valueOf(up.getValue()));
        }
        found = true;
        break;
      }
    }
    assertTrue(found);
  }

  @Test
  public void oAuthAppManagement() throws Exception {
    MvcResult result;
    PublicOAuthApps apps;

    User user = createAndSaveUser(CoreTestUtils.getRandomName(8));
    logoutAndLoginAs(user);
    mockPrincipal = new MockPrincipal(user.getUsername());

    // 1. List all apps, should be 0 apps
    result = mockMvc.perform(get("/userform/ajax/oAuthApps").principal(mockPrincipal)).andReturn();
    apps = getFromJsonAjaxReturnObject(result, PublicOAuthApps.class);
    assertEquals(0, apps.getOAuthApps().size());

    // 2. Add a new app
    result =
        mockMvc
            .perform(
                post("/userform/ajax/oAuthApps/{oAuthAppName}", "newApp1").principal(mockPrincipal))
            .andReturn();
    OAuthAppInfo app = getFromJsonAjaxReturnObject(result, OAuthAppInfo.class);
    assertEquals("newApp1", app.getAppName());

    String clientId = app.getClientId();

    // 3. List all apps, should be 1 app
    result = mockMvc.perform(get("/userform/ajax/oAuthApps").principal(mockPrincipal)).andReturn();
    apps = getFromJsonAjaxReturnObject(result, PublicOAuthApps.class);
    assertEquals(1, apps.getOAuthApps().size());

    // 4. Delete the app
    mockMvc.perform(
        delete("/userform/ajax/oAuthApps/{clientId}", clientId).principal(mockPrincipal));

    // 5. List all apps, should be 0 apps
    result = mockMvc.perform(get("/userform/ajax/oAuthApps").principal(mockPrincipal)).andReturn();
    apps = getFromJsonAjaxReturnObject(result, PublicOAuthApps.class);
    assertEquals(0, apps.getOAuthApps().size());
  }

  @Test
  public void oAuthAppManagementIsolationTest() throws Exception {
    MvcResult result;
    OAuthAppInfo app;
    PublicOAuthApps apps;

    User user1 = createAndSaveUser(CoreTestUtils.getRandomName(8));
    User user2 = createAndSaveUser(CoreTestUtils.getRandomName(8));
    Principal mockPrincipal1 = new MockPrincipal(user1.getUsername());
    Principal mockPrincipal2 = new MockPrincipal(user2.getUsername());

    // Create an app for each user
    logoutAndLoginAs(user1);

    result =
        mockMvc
            .perform(
                post("/userform/ajax/oAuthApps/{oAuthAppName}", "user1App")
                    .principal(mockPrincipal1))
            .andReturn();
    app = getFromJsonAjaxReturnObject(result, OAuthAppInfo.class);
    String clientIdUser1 = app.getClientId();

    logoutAndLoginAs(user2);

    result =
        mockMvc
            .perform(
                post("/userform/ajax/oAuthApps/{oAuthAppName}", "user2App")
                    .principal(mockPrincipal2))
            .andReturn();
    getFromJsonAjaxReturnObject(result, OAuthAppInfo.class);

    // Incorrect principal
    result = mockMvc.perform(get("/userform/ajax/oAuthApps").principal(mockPrincipal1)).andReturn();
    apps = getFromJsonAjaxReturnObject(result, PublicOAuthApps.class);
    assertEquals(1, apps.getOAuthApps().size());
    assertEquals("user2App", apps.getOAuthApps().get(0).getAppName());

    // Incorrect deletion request
    mockMvc.perform(
        delete("/userform/ajax/oAuthApps/{clientId}", clientIdUser1).principal(mockPrincipal2));

    logoutAndLoginAs(user1);

    result = mockMvc.perform(get("/userform/ajax/oAuthApps").principal(mockPrincipal1)).andReturn();
    apps = getFromJsonAjaxReturnObject(result, PublicOAuthApps.class);
    assertEquals(1, apps.getOAuthApps().size());
  }

  @Test
  public void apiKeyManagement() throws Exception {
    User user = createAndSaveUser(CoreTestUtils.getRandomName(8));
    logoutAndLoginAs(user);
    mockPrincipal = new MockPrincipal(user.getUsername());
    MvcResult result =
        mockMvc.perform(get("/userform/ajax/apiKeyInfo").principal(mockPrincipal)).andReturn();
    ApiInfo info = getFromJsonAjaxReturnObject(result, UserProfileController.ApiInfo.class);
    assertNull(info.getKey());
    assertTrue(info.isRegenerable());
    assertFalse(info.isRevokable());

    MvcResult createdKey =
        mockMvc
            .perform(
                post("/userform/ajax/apiKey")
                    .principal(mockPrincipal)
                    .param("password", TESTPASSWD))
            .andReturn();
    ApiInfo created = getFromJsonAjaxReturnObject(createdKey, UserProfileController.ApiInfo.class);
    assertNotNull(created.getKey());
    assertTrue(created.isRegenerable());
    assertTrue(created.isRevokable());

    MvcResult deletedKey =
        mockMvc.perform(delete("/userform/ajax/apiKey").principal(mockPrincipal)).andReturn();
    Long revoked = Long.parseLong(deletedKey.getResponse().getContentAsString());
    assertTrue(revoked >= 1);

    MvcResult noPwd =
        mockMvc
            .perform(post("/userform/ajax/apiKey").principal(mockPrincipal).param("password", ""))

            // .andExpect(status().is4xxClientError())
            .andReturn();
    ErrorList el = getErrorListFromAjaxReturnObject(noPwd);
    assertTrue(el.hasErrorMessages());

    MvcResult wrongPwd =
        mockMvc
            .perform(
                post("/userform/ajax/apiKey")
                    .principal(mockPrincipal)
                    .param("password", "wrongpwd"))
            .andReturn();
    el = getErrorListFromAjaxReturnObject(wrongPwd);
    assertTrue(el.hasErrorMessages());
  }

  @Test
  public void saveRetrieveUserPreference() throws Exception {

    User user = createAndSaveUser(CoreTestUtils.getRandomName(8));
    logoutAndLoginAs(user);
    mockPrincipal = new MockPrincipal(user.getUsername());
    String testValue = "dummy";

    MvcResult unknownPrefGetResult =
        mockMvc
            .perform(
                get("/userform/ajax/preference")
                    .param("preference", "unknown")
                    .principal(mockPrincipal))
            .andReturn();
    assertNull(unknownPrefGetResult.getResolvedException());
    String unknownPrefGetResponse = unknownPrefGetResult.getResponse().getContentAsString();
    assertEquals("", unknownPrefGetResponse);

    MvcResult unknownPrefPostResult =
        mockMvc
            .perform(
                post("/userform/ajax/preference")
                    .param("preference", "unknown")
                    .param("value", testValue)
                    .principal(mockPrincipal))
            .andReturn();
    assertNotNull(unknownPrefPostResult.getResolvedException());
    assertEquals(
        "No enum constant com.researchspace.model.preference.Preference.unknown",
        unknownPrefPostResult.getResolvedException().getMessage());

    MvcResult validPrefPostResult =
        mockMvc
            .perform(
                post("/userform/ajax/preference")
                    .param("preference", Preference.UI_CLIENT_SETTINGS.toString())
                    .param("value", testValue)
                    .principal(mockPrincipal))
            .andReturn();
    assertNull(validPrefPostResult.getResolvedException());
    String updatedPref = getFromJsonAjaxReturnObject(validPrefPostResult, String.class);
    assertEquals(testValue, updatedPref);

    MvcResult validPrefGetResult =
        mockMvc
            .perform(
                get("/userform/ajax/preference")
                    .param("preference", Preference.UI_CLIENT_SETTINGS.toString())
                    .principal(mockPrincipal))
            .andReturn();
    assertNull(validPrefGetResult.getResolvedException());
    String validPrefGetResponse = validPrefGetResult.getResponse().getContentAsString();
    assertEquals(testValue, validPrefGetResponse);
  }

  @Test
  public void miniProfile() throws Exception {
    TestGroup g1 = createTestGroup(1);
    // set lastLoginDate != null
    User piUser = userMgr.get(g1.getPi().getId());
    piUser.setLastLogin(new Date());
    userMgr.save(piUser);

    logoutAndLoginAs(g1.getPi());
    MvcResult validPrefGetResult =
        mockMvc
            .perform(get("/userform/ajax/miniprofile/{id}", g1.getPi().getId() + ""))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    // check ISO-8601 with timezone
    assertThat(
        getJsonPathValue(validPrefGetResult, "$.data.lastLogin").toString(), containsString("Z"));
    MiniProfile miniProfile = getFromJsonAjaxReturnObject(validPrefGetResult, MiniProfile.class);
    assertEquals(g1.getPi().getEmail(), miniProfile.getEmail());
    assertNotNull(miniProfile.getProfileImageLink());
    assertEquals(1, miniProfile.getGroups().size());

    //  check that miniprofile can be retrieved for user who has never logged in:
    MvcResult validPrefGetResult2 =
        mockMvc
            .perform(get("/userform/ajax/miniprofile/{id}", g1.u1().getId()))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertThat(getJsonPathValue(validPrefGetResult2, "$.data.lastLogin"), nullValue());
  }

  @Test
  public void userAccountEvents() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    // this event is not set by the above setup test method, so we create an event here
    userMgr.saveUserAccountEvent(new UserAccountEvent(anyUser, AccountEventType.FIRST_LOGIN));
    MvcResult userAccountEvents =
        mockMvc
            .perform(get("/userform/ajax/accountEventsByUser/{id}", anyUser.getId()))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals(1, getJsonPathValue(userAccountEvents, "$.data.length()"));
    assertEquals("FIRST_LOGIN", getJsonPathValue(userAccountEvents, "$.data.[0].eventType"));
  }

  @Test
  public void testAutoshareRoundTrip() throws Exception {
    TestGroup tg = createTestGroup(1);
    logoutAndLoginAs(tg.u1());
    // create 2  documents that will be autoshared
    createBasicDocumentInRootFolderWithText(tg.u1(), "");
    createBasicDocumentInRootFolderWithText(tg.u1(), "");

    String folderName = "folderNameTest";
    mockMvc
        .perform(
            post(
                    "/userform/ajax/enableAutoshare/{groupId}/{userId}",
                    tg.getGroup().getId(),
                    tg.u1().getId())
                .principal(tg.u1()::getUsername)
                .content(String.format("{\"autoshareFolderName\":\"%s\"}", folderName))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    User u1 = userMgr.get(tg.u1().getId());
    assertEquals(1, u1.getAutoshareGroups().size());
    Long fId = getAutoshareFolderForUser(u1);
    Folder sharedFolder = folderMgr.getFolder(fId, u1);
    assertEquals(folderName, sharedFolder.getName());

    // now disable and re-enable with a different folder.
    // note the original autoshare folder is not actually deleted.
    requestAndAssertAutoshare(tg.getGroup(), tg.u1(), tg.u1(), false, false);

    // other group members get a single notification that autosharing has stopped
    // ie. *not* 1 per unshared document
    logoutAndLoginAs(tg.getPi());
    ISearchResults<Notification> res =
        communicationMgr.getNewNotificationsForUser(
            tg.getPi().getUsername(),
            PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
    // 2 notifications as there are notifications for enabling and disabling autoshare
    assertEquals(2, res.getTotalHits().intValue());
    assertTrue(res.getFirstResult().getNotificationMessage().contains("enabled autosharing"));
    assertTrue(res.getLastResult().getNotificationMessage().contains("disabled autosharing"));

    String folderName2 = "folderNameTest2";
    logoutAndLoginAs(tg.u1());
    mockMvc
        .perform(
            post(
                    "/userform/ajax/enableAutoshare/{groupId}/{userId}",
                    tg.getGroup().getId(),
                    tg.u1().getId())
                .principal(tg.u1()::getUsername)
                .content(String.format("{\"autoshareFolderName\":\"%s\"}", folderName2))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    u1 = userMgr.get(tg.u1().getId());
    Long fId2 = getAutoshareFolderForUser(u1);
    Folder sharedFolder2 = folderMgr.getFolder(fId2, u1);
    assertEquals(folderName2, sharedFolder2.getName());
  }

  private Long getAutoshareFolderForUser(User u1) {
    return u1.getUserGroups().stream()
        .filter(UserGroup::isAutoshareEnabled)
        .map(ug -> ug.getAutoShareFolder().getId())
        .findFirst()
        .get();
  }

  @Test
  public void testAutosharePermissions() throws Exception {
    TestGroup tg = createTestGroup(2, new TestGroupConfig(false));
    Group group = tg.getGroup();

    User pi = group.getOwner();
    User targetMember = tg.u1();
    User otherMember = tg.u2();
    User labAdminViewAll = doCreateAndInitUser(getRandomName(10), Constants.USER_ROLE);
    User labAdminNoViewAll = doCreateAndInitUser(getRandomName(10), Constants.USER_ROLE);
    User memberWithGlobalPiRole = doCreateAndInitUser(getRandomName(10), Constants.PI_ROLE);

    logoutAndLoginAs(pi);
    group =
        grpMgr.addUserToGroup(
            labAdminViewAll.getUsername(), group.getId(), RoleInGroup.RS_LAB_ADMIN);
    labAdminViewAll =
        grpMgr.authorizeLabAdminToViewAll(labAdminViewAll.getId(), pi, group.getId(), true);
    group =
        grpMgr.addUserToGroup(
            labAdminNoViewAll.getUsername(), group.getId(), RoleInGroup.RS_LAB_ADMIN);
    group =
        grpMgr.addUserToGroup(
            memberWithGlobalPiRole.getUsername(), group.getId(), RoleInGroup.DEFAULT);
    group = grpMgr.saveGroup(group, false, pi);

    // Initial state for all user's autoshare status is false. Each of these asserts is
    // stateful and depends on the previous assert.
    requestAndAssertAutoshare(group, otherMember, targetMember, true, false);
    requestAndAssertAutoshare(group, labAdminNoViewAll, targetMember, true, false);
    requestAndAssertAutoshare(group, getSysAdminUser(), targetMember, true, false);
    requestAndAssertAutoshare(group, targetMember, targetMember, true, true);
    requestAndAssertAutoshare(group, pi, targetMember, false, false);
    requestAndAssertAutoshare(group, labAdminViewAll, targetMember, false, false);

    requestAndAssertAutoshare(group, otherMember, pi, true, false);
    requestAndAssertAutoshare(group, labAdminViewAll, pi, true, false);
    requestAndAssertAutoshare(group, labAdminNoViewAll, pi, true, false);
    requestAndAssertAutoshare(group, getSysAdminUser(), pi, true, false);
    requestAndAssertAutoshare(group, pi, pi, true, true);
    requestAndAssertAutoshare(group, memberWithGlobalPiRole, pi, false, true);
  }

  @Test
  public void testAutoshareSystemProperty() throws Exception {
    TestGroup tg = createTestGroup(1, new TestGroupConfig(false));
    Group group = tg.getGroup();

    User pi = group.getOwner();
    User targetMember = tg.u1();

    logoutAndLoginAs(getSysAdminUser(), SYS_ADMIN_PWD);
    systemPropertyManager.save("group_autosharing.available", "DENIED", getSysAdminUser());

    // PIs and lab admins with view all can no longer change the autoshare status for their non-pi
    // members
    requestAndAssertAutoshare(group, pi, targetMember, true, false);

    // Users can still manage their own autoshare status
    requestAndAssertAutoshare(group, targetMember, targetMember, true, true);

    // revert back, as the setting persists between test runs
    logoutAndLoginAs(getSysAdminUser(), SYS_ADMIN_PWD);
    systemPropertyManager.save("group_autosharing.available", "ALLOWED", getSysAdminUser());
  }

  // Attempt to set individual autoshare status for the targetUser as subject
  // and assert that the resulting status is expectedAutoshareStatus by looking at
  // the autoshare group size. This method is stateful - the initial status depends
  // on the previous user autoshare status!
  private void requestAndAssertAutoshare(
      Group group,
      User subject,
      User targetUser,
      Boolean targetAutoshareStatus,
      Boolean expectedAutoshareStatus)
      throws Exception {

    int expectedAutoshareGroupSize = (expectedAutoshareStatus) ? 1 : 0;

    if (subject.hasSysadminRole()) {
      logoutAndLoginAs(subject, SYS_ADMIN_PWD);
    } else {
      logoutAndLoginAs(subject);
    }

    String urlTemplate =
        (targetAutoshareStatus)
            ? "/userform/ajax/enableAutoshare/{groupId}/{userId}"
            : "/userform/ajax/disableAutoshare/{groupId}/{userId}";

    MockHttpServletRequestBuilder requestBuilder =
        post(urlTemplate, group.getId(), targetUser.getId()).principal(subject::getUsername);

    if (targetAutoshareStatus) {
      requestBuilder =
          requestBuilder
              .content(String.format("{\"autoshareFolderName\":\"%s\"}", targetUser.getUsername()))
              .contentType(MediaType.APPLICATION_JSON);
    }

    mockMvc.perform(requestBuilder).andExpect(status().isOk());

    targetUser = userMgr.get(targetUser.getId());
    int autoshareGroupCount = targetUser.getAutoshareGroups().size();

    assertEquals(expectedAutoshareGroupSize, autoshareGroupCount);
  }

  @Test
  public void showPublicLoginProfile() throws Exception {
    try {
      logoutAndLoginAsSysAdmin();
      allowPublicLastLogin();
      RSpaceTestUtils.logout();
      // force lastLogin to be set.
      User profileUser = createInitAndLoginAnyUser();
      profileUser = forceSetLastLogin(profileUser);
      RSpaceTestUtils.logout();
      //
      User sessionUser = createInitAndLoginAnyUser();
      MvcResult miniProfileResult = getMiniProfile(profileUser);
      // check ISO-8601 with timezone
      MiniProfile miniProfile = getFromJsonAjaxReturnObject(miniProfileResult, MiniProfile.class);
      assertNotNull(miniProfile.getLastLogin());

      denyPublicLastLogin();

      logoutAndLoginAs(sessionUser);
      miniProfileResult = getMiniProfile(profileUser);
      miniProfile = getFromJsonAjaxReturnObject(miniProfileResult, MiniProfile.class);
      assertNull(miniProfile.getLastLogin());
    } finally {
      allowPublicLastLogin();
    }
  }

  private User forceSetLastLogin(User profileUser) {
    profileUser = userMgr.get(profileUser.getId());
    profileUser.setLastLogin(new Date());
    profileUser = userMgr.save(profileUser);
    return profileUser;
  }

  private void allowPublicLastLogin() {
    systemPropertyManager.save("publicLastLogin.available", "ALLOWED", getSysAdminUser());
  }

  private void denyPublicLastLogin() {
    systemPropertyManager.save("publicLastLogin.available", "DENIED", getSysAdminUser());
  }

  private MvcResult getMiniProfile(User profileUser) throws Exception {
    MvcResult validPrefGetResult =
        mockMvc
            .perform(get("/userform/ajax/miniprofile/{id}", profileUser.getId() + ""))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    return validPrefGetResult;
  }

  @Test
  public void checkEnforcedOntologies() throws Exception {
    User pi = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    User user = createAndSaveUser(getRandomName(10));
    initUsers(pi, user);

    logoutAndLoginAs(user);
    mockPrincipal = new MockPrincipal(user.getUsername());
    MvcResult result =
        mockMvc
            .perform(get("/userform/ajax/enforcedOntologies").principal(mockPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    Boolean ontologiesEnforced = getFromJsonResponseBody(result, Boolean.class);
    assertFalse(ontologiesEnforced);

    Group g = createGroupForUsersWithDefaultPi(pi, user);
    g.setEnforceOntologies(true);
    groupManager.saveGroup(g, pi);

    result =
        mockMvc
            .perform(get("/userform/ajax/enforcedOntologies").principal(mockPrincipal))
            .andReturn();
    ontologiesEnforced = getFromJsonResponseBody(result, Boolean.class);
    assertTrue(ontologiesEnforced);
  }

  @Test
  public void selfDeclareAsPiThenAsRegularUser() throws Exception {
    User user = createInitAndLoginAnyUser();
    user = userMgr.get(user.getId());
    assertFalse(user.isPI());
    assertFalse(user.isPIOfLabGroup());

    // try with disabled deployment property
    propertyHolder.setSSOSelfDeclarePiEnabled(false);
    MvcResult result =
        mockMvc
            .perform(post("/userform/ajax/selfDeclareAsPi").principal(user::getUsername))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", is(false)))
            .andReturn();
    ErrorList errorList = getErrorListFromAjaxReturnObject(result);
    assertNotNull(errorList);
    assertEquals(
        "Self-declaring PI not enabled", errorList.getAllErrorMessagesAsStringsSeparatedBy(";"));

    // enable deployment property & try again
    propertyHolder.setSSOSelfDeclarePiEnabled(true);
    result =
        mockMvc
            .perform(post("/userform/ajax/selfDeclareAsPi").principal(user::getUsername))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", is(false)))
            .andReturn();
    errorList = getErrorListFromAjaxReturnObject(result);
    assertNotNull(errorList);
    assertEquals(
        "User cannot self-declare as a PI (isAllowedPiRole=false).",
        errorList.getAllErrorMessagesAsStringsSeparatedBy(";"));

    // need force-refresh for next mvcit call to pick up. still not a PI.
    user = userMgr.getUserByUsername(user.getUsername(), true);
    assertFalse(user.isPI());
    assertFalse(user.isPIOfLabGroup());

    // allow user to self-declare as a pi & try again
    user.setAllowedPiRole(true);
    userMgr.save(user);

    mockMvc
        .perform(post("/userform/ajax/selfDeclareAsPi").principal(user::getUsername))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", is(true)))
        .andReturn();
    user = userMgr.getUserByUsername(user.getUsername(), true);
    assertTrue(user.isPI());
    assertFalse(user.isPIOfLabGroup());

    // create a group for the user
    Group group = createGroupForPiAndUsers(user, new User[] {user});
    user = userMgr.getUserByUsername(user.getUsername(), true);
    assertTrue(user.isPI());
    assertTrue(user.isPIOfLabGroup());

    // try demoting to regular user
    result =
        mockMvc
            .perform(post("/userform/ajax/selfDeclareAsRegularUser").principal(user::getUsername))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", is(false)))
            .andReturn();
    errorList = getErrorListFromAjaxReturnObject(result);
    assertNotNull(errorList);
    assertEquals(
        "You must delete or transfer over all LabGroups before you can remove your PI status.",
        errorList.getAllErrorMessagesAsStringsSeparatedBy(";"));

    // force-refresh and verify still has PI role and group
    user = userMgr.getUserByUsername(user.getUsername(), true);
    assertTrue(user.isPI());
    assertTrue(user.isPIOfLabGroup());
    assertFalse(user.getGroups().isEmpty());

    // remove the group
    grpMgr.removeGroup(group.getId(), user);

    // refresh and verify no longer has a group
    user = userMgr.getUserByUsername(user.getUsername(), true);
    assertTrue(user.isPI());
    assertFalse(user.isPIOfLabGroup());
    assertTrue(user.getGroups().isEmpty());

    // demote to regular user
    result =
        mockMvc
            .perform(post("/userform/ajax/selfDeclareAsRegularUser").principal(user::getUsername))
            .andExpect(status().isOk())
            // .andExpect(jsonPath("$.data", is(true)))
            .andReturn();
    errorList = getErrorListFromAjaxReturnObject(result);
    assertNull(errorList);

    // refresh and verify no longer has PI role
    user = userMgr.getUserByUsername(user.getUsername(), true);
    assertFalse(user.isPI());
    assertFalse(user.isPIOfLabGroup());
  }
}

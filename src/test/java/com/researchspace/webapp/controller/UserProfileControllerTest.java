package com.researchspace.webapp.controller;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.model.record.TestFactory.createOAuthTokenForUI;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.dto.MiniProfile;
import com.researchspace.model.events.AccountEventType;
import com.researchspace.model.events.UserAccountEvent;
import com.researchspace.model.frontend.OAuthAppInfo;
import com.researchspace.model.frontend.PublicOAuthAppInfo;
import com.researchspace.model.frontend.PublicOAuthApps;
import com.researchspace.model.frontend.PublicOAuthConnApps;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.LicenseService;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.OAuthAppManager;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.UserProfileController.UserGroupInfo;
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;

public class UserProfileControllerTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock LicenseService licenseService;
  @Mock UserManager usrMgr;
  @Mock MessageSourceUtils messages;
  @Mock IPropertyHolder properties;
  @Mock AnalyticsManager analMgr;
  @Mock OAuthAppManager oAuthAppManager;
  @Mock OAuthTokenManager oAuthTokenManager;

  @InjectMocks UserProfileController userProfileController;
  User anyUser, sessionUser;
  MockHttpServletRequest mockRequest;

  @Before
  public void before() {
    anyUser = TestFactory.createAnyUser("any");
    anyUser.setId(2L);
    sessionUser = TestFactory.createAnyUser("session");
    sessionUser.setId(3L);
    this.mockRequest = new MockHttpServletRequest();
  }

  @Test
  public void getOAuthApps() {
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sessionUser);
    PublicOAuthAppInfo result = createAnyPublicOauthAppInfo("id");

    when(oAuthAppManager.getApps(sessionUser)).thenReturn(toList(result));
    AjaxReturnObject<PublicOAuthApps> aro = userProfileController.getOAuthApps();
    assertNotNull(aro.getData());
    assertNull(aro.getError());
    assertEquals(result.getClientId(), aro.getData().getOAuthApps().get(0).getClientId());
  }

  @Test
  public void addOAuthAppsSuccess() {
    // given
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sessionUser);
    String appName = "app";
    OAuthAppInfo info = createAnyOauthAppInfo(appName, "id");
    ServiceOperationResult<OAuthAppInfo> result = new ServiceOperationResult<>(info, true);
    when(oAuthAppManager.addApp(sessionUser, appName)).thenReturn(result);
    // when
    AjaxReturnObject<OAuthAppInfo> aro = userProfileController.addOAuthApp(appName);
    // then
    assertNotNull(aro.getData());
    assertNotNull(aro.getData().getUnhashedClientSecret());
    assertEquals(aro.getData().getClientId(), info.getClientId());
  }

  @Test
  public void addOAuthAppFails() {
    // given
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sessionUser);
    String appName = "app";
    ServiceOperationResult<OAuthAppInfo> result =
        new ServiceOperationResult<>(null, false, "failed-message");
    when(oAuthAppManager.addApp(sessionUser, appName)).thenReturn(result);
    // when
    AjaxReturnObject<OAuthAppInfo> aro = userProfileController.addOAuthApp(appName);
    // then
    assertNull(aro.getData());
    assertNotNull(aro.getError());
    assertThat(aro.getErrorMsg().getErrorMessages(), containsInAnyOrder("failed-message"));
  }

  @Test
  public void removeOAuthAppFails() {
    // given
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sessionUser);
    String clientId = "id";
    ServiceOperationResult<Void> result =
        new ServiceOperationResult<>(null, false, "failed-message");
    when(oAuthTokenManager.removeAllTokens(sessionUser, clientId)).thenReturn(result);

    // when
    AjaxReturnObject<Void> aro = userProfileController.removeOAuthApp(clientId);

    // then
    verify(oAuthAppManager, never()).removeApp(sessionUser, clientId);
    assertNull(aro.getData());
    assertNotNull(aro.getError());
    assertThat(aro.getErrorMsg().getErrorMessages(), containsInAnyOrder("failed-message"));
  }

  @Test
  public void removeOAuthAppSuccess() {
    // given
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sessionUser);
    String clientId = "id";
    ServiceOperationResult<Void> result = new ServiceOperationResult<>(null, true, "succeeded");
    when(oAuthTokenManager.removeAllTokens(sessionUser, clientId)).thenReturn(result);
    when(oAuthAppManager.removeApp(sessionUser, clientId)).thenReturn(result);

    // when
    AjaxReturnObject<Void> aro = userProfileController.removeOAuthApp(clientId);

    // then
    verify(oAuthAppManager).removeApp(sessionUser, clientId);
    assertNull(aro.getData());
    assertNotNull(aro.getError());
    assertThat(aro.getErrorMsg().getErrorMessages(), containsInAnyOrder("succeeded"));
  }

  @Test
  public void getConnectedApps() {
    // given
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sessionUser);
    OAuthToken token = createOAuthTokenForUI(sessionUser);
    when(oAuthTokenManager.getTokensForUser(sessionUser)).thenReturn(toList(token));
    PublicOAuthAppInfo appInfo = createAnyPublicOauthAppInfo(token.getClientId());
    when(oAuthAppManager.getApp(token.getClientId())).thenReturn(Optional.of(appInfo));

    // when
    AjaxReturnObject<PublicOAuthConnApps> aro = userProfileController.getOAuthConnectedApps();

    // then
    assertNotNull(aro.getData());
    assertEquals(1, aro.getData().getOAuthConnectedApps().size());
    assertEquals(
        appInfo.getAppName(), aro.getData().getOAuthConnectedApps().get(0).getClientName());
  }

  @Test
  public void disconnectOAuthConnectedAppSuccess() {
    // given
    ServiceOperationResult<OAuthToken> result =
        new ServiceOperationResult<>(createOAuthTokenForUI(sessionUser), true, "succeeded");
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sessionUser);
    when(oAuthTokenManager.removeToken(sessionUser, "id")).thenReturn(result);

    // when
    AjaxReturnObject<Boolean> aro = userProfileController.disconnectOAuthConnectedApp("id");

    // then
    assertTrue(aro.isSuccess());
    assertTrue(aro.getData());
  }

  @Test
  public void disconnectOAuthConnectedAppFailure() {
    // given
    ServiceOperationResult<OAuthToken> result = new ServiceOperationResult<>(null, false, "failed");
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sessionUser);
    when(oAuthTokenManager.removeToken(sessionUser, "id")).thenReturn(result);

    // when
    AjaxReturnObject<Boolean> aro = userProfileController.disconnectOAuthConnectedApp("id");

    // then
    assertFalse(aro.isSuccess());
    assertNull(aro.getData());
  }

  private OAuthAppInfo createAnyOauthAppInfo(String appName, String clientId) {
    OAuthAppInfo info = new OAuthAppInfo();
    info.setAppName(appName);
    info.setClientId(clientId);
    info.setUnhashedClientSecret("secret");
    return info;
  }

  private PublicOAuthAppInfo createAnyPublicOauthAppInfo(String id) {
    PublicOAuthAppInfo result = new PublicOAuthAppInfo();
    result.setAppName("myapp");
    result.setClientId(id);
    return result;
  }

  @Test
  public void miniProfileDoesNotReturnPrivateProfiles() {
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sessionUser);
    when(usrMgr.getUser(2L + "")).thenReturn(anyUser);
    when(properties.isProfileHidingEnabled()).thenReturn(true);
    when(usrMgr.populateConnectedUserList(sessionUser)).thenReturn(Collections.emptyList());
    when(messages.getMessage(Mockito.eq("record.inaccessible"), Mockito.any(Long[].class)))
        .thenReturn("error");
    sessionUser.setConnectedUsers(Collections.emptyList());
    anyUser.setPrivateProfile(true);

    AjaxReturnObject<MiniProfile> rc = userProfileController.miniprofile(2L);
    assertNotNull(rc.getError());
  }

  // rspac-2007
  @Test
  public void updatePreferences() throws IOException {
    List<String> prefToUpdate =
        TransformerUtils.toList(Preference.NOTIFICATION_DOCUMENT_DELETED_PREF.name());
    Principal mockPrincipal = anyUser::getUsername;
    Set<UserPreference> knownPrefs = createSavedPrefs();
    when(usrMgr.getUserByUsername(anyUser.getUsername())).thenReturn(anyUser);

    when(usrMgr.getUserAndPreferencesForUser(anyUser.getUsername())).thenReturn(knownPrefs);

    userProfileController.updatePreferences(prefToUpdate, mockPrincipal, mockRequest);
    Long messagePrefsCount =
        EnumSet.allOf(Preference.class).stream().filter(Preference::isMessagingPreference).count();

    verify(usrMgr, Mockito.times(1))
        .setPreference(Mockito.any(Preference.class), Mockito.eq("true"), _equalUsername());
    // message prefs *not* being updated are set to false
    verify(usrMgr, Mockito.times(messagePrefsCount.intValue() - 1))
        .setPreference(Mockito.any(Preference.class), _equalFalse(), _equalUsername());
    // non-message-prefs remain intact
    verify(usrMgr, never())
        .setPreference(Mockito.eq(Preference.CHEMISTRY), _equalFalse(), _equalUsername());
    // and this message pref is set to false
    verify(usrMgr)
        .setPreference(
            Mockito.eq(Preference.NOTIFICATION_REQUEST_STATUS_CHANGE_PREF),
            _equalFalse(),
            _equalUsername());
  }

  private String _equalFalse() {
    return Mockito.eq("false");
  }

  private String _equalUsername() {
    return Mockito.eq(anyUser.getUsername());
  }

  private Set<UserPreference> createSavedPrefs() {
    return EnumSet.of(
            Preference.BOX, Preference.CHEMISTRY, Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL)
        .stream()
        .map(pref -> new UserPreference(pref, anyUser, "true"))
        .collect(Collectors.toSet());
  }

  @Test
  public void getUserGroupInfo() throws IOException {
    User pi = TestFactory.createAnyUserWithRole("pi", Role.PI_ROLE.getName());
    Group g1 = TestFactory.createAnyGroup(pi, anyUser);
    g1.setPrivateProfile(Boolean.TRUE);
    // session user is anyUser, who is in group, can see group
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
    when(usrMgr.get(2L)).thenReturn(anyUser);
    when(properties.isProfileHidingEnabled()).thenReturn(Boolean.TRUE);
    anyUser.setConnectedGroups(TransformerUtils.toList(g1));
    AjaxReturnObject<List<UserGroupInfo>> ugs = userProfileController.getUserGroupInfo(2L);
    assertGroupViewable(ugs);

    // session user is now not in group
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sessionUser);
    sessionUser.setConnectedGroups(Collections.emptyList());
    ugs = userProfileController.getUserGroupInfo(2L);
    assertGroupHidden(ugs);

    // group is not private, can be seen by anyone - sesssionUser....
    g1.setPrivateProfile(Boolean.FALSE);
    ugs = userProfileController.getUserGroupInfo(2L);
    assertGroupViewable(ugs);

    // .. or groupmember
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
    ugs = userProfileController.getUserGroupInfo(2L);
    assertGroupViewable(ugs);
  }

  @Test
  public void getAccountEvents() throws IOException {
    User pi = TestFactory.createAnyUserWithRole("pi", Role.PI_ROLE.getName());
    Group g1 = TestFactory.createAnyGroup(pi, anyUser);

    UserAccountEvent event = new UserAccountEvent(anyUser, AccountEventType.ENABLED);
    when(usrMgr.getAccountEventsForUser(anyUser)).thenReturn(toList(event));

    // session user == requested profile
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
    when(usrMgr.get(anyUser.getId())).thenReturn(anyUser);
    assertAccountEventsVisible(anyUser, true);

    // session user is pi, ok, is in group
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(pi);
    assertAccountEventsVisible(anyUser, true);

    // session user is sysadmin
    User sysadmin = TestFactory.createAnyUserWithRole("admin", Role.SYSTEM_ROLE.getName());
    sysadmin.setId(anyUser.getId()); // so that when() works
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sysadmin);
    assertAccountEventsVisible(anyUser, true);

    // random user can't see
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sessionUser);
    assertAccountEventsVisible(anyUser, false);
  }

  private void assertAccountEventsVisible(User queryUser, boolean expected) {
    assertEquals(
        expected ? 1 : 0,
        userProfileController.getAccountEventsByUser(queryUser.getId()).getData().size());
  }

  private void assertGroupHidden(AjaxReturnObject<List<UserGroupInfo>> ugs) {
    assertEquals(1, ugs.getData().size());
    assertTrue(ugs.getData().get(0).getPrivateGroup());
    assertNull(ugs.getData().get(0).getGroupDisplayName());
  }

  private void assertGroupViewable(AjaxReturnObject<List<UserGroupInfo>> ugs) {
    assertEquals(1, ugs.getData().size());
    assertFalse(ugs.getData().get(0).getPrivateGroup());
    assertNotNull(ugs.getData().get(0).getGroupDisplayName());
  }
}

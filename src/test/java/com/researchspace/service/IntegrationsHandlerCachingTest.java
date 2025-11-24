package com.researchspace.service;

import static com.researchspace.service.IntegrationsHandler.ACCESS_TOKEN_SETTING;
import static com.researchspace.service.IntegrationsHandler.FIELDMARK_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.FIELDMARK_USER_TOKEN;
import static com.researchspace.service.IntegrationsHandler.PROTOCOLS_IO_APP_NAME;
import static com.researchspace.service.impl.IntegrationsHandlerImpl.MASKED_TOKEN;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IntegrationsHandlerCachingTest extends SpringTransactionalTest {

  private @Autowired IntegrationsHandler integrationsHandler;
  private @Autowired SystemPropertyManager sysPropMger;
  private @Autowired UserConnectionManager userConn;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testCaching() {
    User user1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user1"));
    User user2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user2"));
    // basic caching/retrieval for single user
    initialiseContentWithEmptyContent(user1, user2);
    Preference dropboxPreference = Preference.DROPBOX;
    Preference googledrivePreference = Preference.GOOGLEDRIVE;

    IntegrationInfo infoUser1 = integrationsHandler.getIntegration(user1, dropboxPreference.name());
    IntegrationInfo cachedInfoUser1 =
        integrationsHandler.getIntegration(user1, dropboxPreference.name());
    IntegrationInfo infoUser2 = integrationsHandler.getIntegration(user2, dropboxPreference.name());

    // is cached for 2nd get, keyed by user
    assertThat(cachedInfoUser1, sameInstance(infoUser1));
    assertThat(cachedInfoUser1, not(sameInstance(infoUser2)));
    // update refreshes cache
    IntegrationInfo newInfoUser1 =
        createNewInfoWithDifferentProperties(dropboxPreference.name(), infoUser1);
    integrationsHandler.updateIntegrationInfo(user1, newInfoUser1);
    IntegrationInfo reloadedNewInfoUser1 =
        integrationsHandler.getIntegration(user1, dropboxPreference.name());
    assertThat(cachedInfoUser1, not(sameInstance(reloadedNewInfoUser1)));
    assertEquals(reloadedNewInfoUser1, newInfoUser1);

    assertThatUpdatingAvailabilityTriggersCacheRefresh(
        user1, dropboxPreference.name(), reloadedNewInfoUser1);
    assertThatUpdatingAUserPrefDoesNotAffectOtherUsersOrPrefs(
        user1, user2, dropboxPreference, googledrivePreference);
  }

  private void assertThatUpdatingAUserPrefDoesNotAffectOtherUsersOrPrefs(
      User user1, User user2, Preference pref1, Preference pref2) {

    IntegrationInfo user1Info1;
    IntegrationInfo reloadedUser1Info1;

    IntegrationInfo user1Info2;
    IntegrationInfo reloadedUser1Info2;

    IntegrationInfo user2Info1;
    IntegrationInfo reloadedUser2Info1;

    // now test that updating user preference invalidates for user, but not other user
    user1Info1 = integrationsHandler.getIntegration(user1, pref1.name());
    user1Info2 = integrationsHandler.getIntegration(user1, pref2.name());
    user2Info1 = integrationsHandler.getIntegration(user2, pref1.name());
    userMgr.setPreference(pref1, Boolean.FALSE.toString(), user1.getUsername());
    reloadedUser1Info1 = integrationsHandler.getIntegration(user1, pref1.name());

    assertThat(user1Info1, not(sameInstance(reloadedUser1Info1)));
    // but other pref for same user is cached OK
    reloadedUser1Info2 = integrationsHandler.getIntegration(user1, pref2.name());
    assertThat(user1Info2, sameInstance(reloadedUser1Info2));
    // but other user pref still cached OK:
    reloadedUser2Info1 = integrationsHandler.getIntegration(user2, pref1.name());
    assertThat(reloadedUser2Info1, sameInstance(user2Info1));
  }

  // now, we login as sysadmin and alter the availability, via 3 save methods,
  // this should expire the cache for everyone.
  private void assertThatUpdatingAvailabilityTriggersCacheRefresh(
      User user, String propertyToUpdate, IntegrationInfo reloaded) {
    SystemPropertyValue dropboxAvailable =
        sysPropMger.findByName(SystemPropertyName.DROPBOX_AVAILABLE);
    // save by name
    sysPropMger.save(
        SystemPropertyName.DROPBOX_AVAILABLE,
        dropboxAvailable.getValue(),
        user); // doesn't matter if value is different; just saving will trigger cache invalidation
    IntegrationInfo dropboxAvailableReloaded =
        integrationsHandler.getIntegration(user, propertyToUpdate);
    assertThat(dropboxAvailableReloaded, not(sameInstance(reloaded)));
    // save by object
    sysPropMger.save(dropboxAvailable, user);
    IntegrationInfo dropboxAvailableReloaded2 =
        integrationsHandler.getIntegration(user, propertyToUpdate);
    assertThat(dropboxAvailableReloaded2, not(sameInstance(dropboxAvailableReloaded)));
    // save by id
    sysPropMger.save(dropboxAvailable.getId(), dropboxAvailable.getValue(), user);
    IntegrationInfo dropboxAvailableReloaded3 =
        integrationsHandler.getIntegration(user, propertyToUpdate);
    assertThat(dropboxAvailableReloaded3, not(sameInstance(dropboxAvailableReloaded2)));

    // now let's update a child property; should refresh all properties
    SystemPropertyValue dropboxLinking =
        sysPropMger.findByName(SystemPropertyName.DROPBOX_LINKING_ENABLED);
    sysPropMger.save(dropboxLinking, user);
    IntegrationInfo dropboxAvailableReloaded4 =
        integrationsHandler.getIntegration(user, propertyToUpdate);
    assertThat(dropboxAvailableReloaded4, not(sameInstance(dropboxAvailableReloaded3)));
  }

  private IntegrationInfo createNewInfoWithDifferentProperties(
      String propertyToUpdate, IntegrationInfo info) {
    IntegrationInfo newValue = new IntegrationInfo();
    newValue.setAvailable(!info.isAvailable());
    newValue.setEnabled(!info.isEnabled());
    newValue.setName(propertyToUpdate);
    return newValue;
  }

  @Test
  public void testCachingForAccessTokensStoredInUserConnectionForProtocolsIo() {
    // RSPAC-1614
    User anyUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(anyUser);
    // add a user connection
    UserConnection protocolsIOConnection = TestFactory.createUserConnection(anyUser.getUsername());
    protocolsIOConnection.getId().setProviderId(PROTOCOLS_IO_APP_NAME);
    protocolsIOConnection = userConn.save(protocolsIOConnection);

    // this will cache the info
    IntegrationInfo info = integrationsHandler.getIntegration(anyUser, PROTOCOLS_IO_APP_NAME);
    IntegrationInfo infoCached = integrationsHandler.getIntegration(anyUser, PROTOCOLS_IO_APP_NAME);
    assertThat(info, sameInstance(infoCached));
    assertEquals(
        getAccessTokenFromIntegrationInfo(info), getAccessTokenFromIntegrationInfo(infoCached));

    // now set new access token - this should trigger a reload and re-cache.
    protocolsIOConnection.setAccessToken("newToken");
    protocolsIOConnection = userConn.save(protocolsIOConnection);
    IntegrationInfo reloaded = integrationsHandler.getIntegration(anyUser, PROTOCOLS_IO_APP_NAME);
    IntegrationInfo reloadedCached =
        integrationsHandler.getIntegration(anyUser, PROTOCOLS_IO_APP_NAME);
    assertEquals("newToken", getAccessTokenFromIntegrationInfo(reloaded));
    // sanity check that it is now being cached again
    assertThat(reloaded, sameInstance(reloadedCached));

    // now we remove the UserConnection, should evict cache
    userConn.deleteByUserAndProvider(anyUser.getUsername(), PROTOCOLS_IO_APP_NAME);
    IntegrationInfo reloaded2 = integrationsHandler.getIntegration(anyUser, PROTOCOLS_IO_APP_NAME);
    // ... and is now picking up  no UserConnection
    assertThat(reloaded2, not(sameInstance(reloaded)));
    assertNull(getAccessTokenFromIntegrationInfo(reloaded2));
  }

  @Test
  public void testCachingForAccessTokensStoredInUserConnectionForNonProtocolsIO() {
    User anyUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(anyUser);
    // add a user connection
    UserConnection fieldmarkConnection = TestFactory.createUserConnection(anyUser.getUsername());
    fieldmarkConnection.getId().setProviderId(FIELDMARK_APP_NAME);
    fieldmarkConnection = userConn.save(fieldmarkConnection);

    // this will cache the info
    IntegrationInfo info = integrationsHandler.getIntegration(anyUser, FIELDMARK_APP_NAME);
    IntegrationInfo infoCached = integrationsHandler.getIntegration(anyUser, FIELDMARK_APP_NAME);
    assertThat(info, sameInstance(infoCached));
    assertEquals(
        getAccessTokenFromIntegrationInfo(info), getAccessTokenFromIntegrationInfo(infoCached));

    // now set new access token - this should trigger a reload and re-cache.
    fieldmarkConnection.setAccessToken("newToken");
    fieldmarkConnection = userConn.save(fieldmarkConnection);
    IntegrationInfo reloaded = integrationsHandler.getIntegration(anyUser, FIELDMARK_APP_NAME);
    IntegrationInfo reloadedCached =
        integrationsHandler.getIntegration(anyUser, FIELDMARK_APP_NAME);
    assertEquals(MASKED_TOKEN, reloaded.getOptions().get(FIELDMARK_USER_TOKEN));
    // sanity check that it is now being cached again
    assertThat(reloaded, sameInstance(reloadedCached));

    // now we remove the UserConnection, should evict cache
    userConn.deleteByUserAndProvider(anyUser.getUsername(), FIELDMARK_APP_NAME);
    IntegrationInfo reloaded2 = integrationsHandler.getIntegration(anyUser, FIELDMARK_APP_NAME);
    // ... and is now picking up  no UserConnection
    assertThat(reloaded2, not(sameInstance(reloaded)));
    assertNull(reloaded2.getOptions().get(FIELDMARK_USER_TOKEN));
  }

  private Object getAccessTokenFromIntegrationInfo(IntegrationInfo infoCached) {
    return infoCached.getOptions().get(ACCESS_TOKEN_SETTING);
  }
}

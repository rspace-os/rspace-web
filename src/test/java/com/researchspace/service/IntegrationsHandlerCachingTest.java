package com.researchspace.service;

import static com.researchspace.service.IntegrationsHandler.ACCESS_TOKEN_SETTING;
import static com.researchspace.service.IntegrationsHandler.PROTOCOLS_IO_APP_NAME;
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
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    User other = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    // basic caching/retrieval for single user
    initialiseContentWithEmptyContent(user, other);
    Preference prefToUpdate = Preference.DROPBOX;
    String prefToUpdateName = prefToUpdate.name();
    Preference anotherPref = Preference.BOX;
    IntegrationInfo info = integrationsHandler.getIntegration(user, prefToUpdateName);
    IntegrationInfo cached = integrationsHandler.getIntegration(user, prefToUpdateName);
    IntegrationInfo otherUserInfo = integrationsHandler.getIntegration(other, prefToUpdateName);
    // is cached for 2nd get, keyed by user
    assertThat(cached, sameInstance(info));
    assertThat(cached, not(sameInstance(otherUserInfo)));
    // update refreshes cache
    IntegrationInfo newValue = createNewInfoWithDifferentProperties(prefToUpdateName, info);
    integrationsHandler.updateIntegrationInfo(user, newValue);
    IntegrationInfo reloaded = integrationsHandler.getIntegration(user, prefToUpdateName);
    assertThat(cached, not(sameInstance(reloaded)));
    assertEquals(reloaded, newValue);

    assertThatUpdatingAvailabilityTriggersCacheRefresh(user, prefToUpdateName, reloaded);
    assertThatUpdatingAUserPrefDoesNotAffectOtherUsersOrPrefs(
        user, other, prefToUpdate, prefToUpdateName, anotherPref);
  }

  private void assertThatUpdatingAUserPrefDoesNotAffectOtherUsersOrPrefs(
      User user,
      User other,
      Preference prefToUpdate,
      String prefToUpdateName,
      Preference anotherPref) {
    IntegrationInfo info;
    IntegrationInfo otherUserInfo;
    // now test that updating userpreference invalidates for user, but not other user
    info = integrationsHandler.getIntegration(user, prefToUpdateName);
    IntegrationInfo infoOtherPref = integrationsHandler.getIntegration(user, anotherPref.name());
    otherUserInfo = integrationsHandler.getIntegration(other, prefToUpdateName);
    userMgr.setPreference(prefToUpdate, Boolean.FALSE.toString(), user.getUsername());
    IntegrationInfo info2 = integrationsHandler.getIntegration(user, prefToUpdateName);

    assertThat(info, not(sameInstance(info2)));
    // but other pref for same user is cached OK
    IntegrationInfo infoOtherPrefReloaded =
        integrationsHandler.getIntegration(user, anotherPref.name());
    assertThat(infoOtherPref, sameInstance(infoOtherPrefReloaded));
    // but other user pref still cached OK:
    IntegrationInfo otherUser2 = integrationsHandler.getIntegration(other, prefToUpdateName);
    assertThat(otherUser2, sameInstance(otherUserInfo));
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
  public void testCachingForAccessTokensStoredInUserConnection() {
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
        protocolsIOConnection.getAccessToken(), getAccessTokenFromIntegrationInfo(infoCached));

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
    userConn.deleteByUserAndProvider(PROTOCOLS_IO_APP_NAME, anyUser.getUsername());
    IntegrationInfo reloaded2 = integrationsHandler.getIntegration(anyUser, PROTOCOLS_IO_APP_NAME);
    // ... and is now picking up  no UserConnection
    assertThat(reloaded2, not(sameInstance(reloaded)));
    assertNull(getAccessTokenFromIntegrationInfo(reloaded2));
  }

  private Object getAccessTokenFromIntegrationInfo(IntegrationInfo infoCached) {
    return infoCached.getOptions().get(ACCESS_TOKEN_SETTING);
  }
}

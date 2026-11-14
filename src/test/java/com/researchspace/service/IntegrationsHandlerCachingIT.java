package com.researchspace.service;

import static com.researchspace.service.IntegrationsHandler.ACCESS_TOKEN_SETTING;
import static com.researchspace.service.IntegrationsHandler.FIELDMARK_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.FIELDMARK_USER_TOKEN;
import static com.researchspace.service.IntegrationsHandler.PROTOCOLS_IO_APP_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IntegrationsHandlerCachingIT extends RealTransactionSpringTestBase {

  private final IntegrationsHandler integrationsHandler;
  private final SystemPropertyManager sysPropMger;
  private final UserConnectionManager userConn;

  @Autowired
  IntegrationsHandlerCachingIT(
      IntegrationsHandler integrationsHandler,
      SystemPropertyManager sysPropMger,
      UserConnectionManager userConn) {
    this.integrationsHandler = integrationsHandler;
    this.sysPropMger = sysPropMger;
    this.userConn = userConn;
  }

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testCaching() {
    User user1 = createAndSaveUser(getRandomAlphabeticString("user1"));
    User user2 = createAndSaveUser(getRandomAlphabeticString("user2"));
    // basic caching/retrieval for single user
    initUser(user1);
    initUser(user2);
    Preference dropboxPreference = Preference.DROPBOX;

    IntegrationInfo infoUser1 = integrationsHandler.getIntegration(user1, dropboxPreference.name());
    IntegrationInfo cachedInfoUser1 =
        integrationsHandler.getIntegration(user1, dropboxPreference.name());
    IntegrationInfo infoUser2 = integrationsHandler.getIntegration(user2, dropboxPreference.name());

    // is cached for 2nd get, keyed by user
    assertSame(infoUser1, cachedInfoUser1);
    assertNotSame(infoUser2, cachedInfoUser1);
    // update refreshes cache
    IntegrationInfo newInfoUser1 =
        createNewInfoWithDifferentProperties(dropboxPreference.name(), infoUser1);
    integrationsHandler.updateIntegrationInfo(user1, newInfoUser1);
    IntegrationInfo reloadedNewInfoUser1 =
        integrationsHandler.getIntegration(user1, dropboxPreference.name());
    assertNotSame(reloadedNewInfoUser1, cachedInfoUser1);
    assertEquals(reloadedNewInfoUser1, newInfoUser1);

    assertThatUpdatingAvailabilityTriggersCacheRefresh(
        user1, dropboxPreference.name(), reloadedNewInfoUser1);
  }

  // now, we login as sysadmin and alter the availability, via 3 save methods,
  // this should expire the cache for everyone.
  private void assertThatUpdatingAvailabilityTriggersCacheRefresh(
      User user, String propertyToUpdate, IntegrationInfo reloaded) {
    User sysadmin = getSysAdminUser();
    SystemPropertyValue dropboxAvailable =
        sysPropMger.findByName(SystemPropertyName.DROPBOX_AVAILABLE);
    // save by name; the same value still triggers cache invalidation
    sysPropMger.save(SystemPropertyName.DROPBOX_AVAILABLE, dropboxAvailable.getValue(), sysadmin);
    IntegrationInfo dropboxAvailableReloaded =
        integrationsHandler.getIntegration(user, propertyToUpdate);
    assertNotSame(reloaded, dropboxAvailableReloaded);
    // save by object
    sysPropMger.save(dropboxAvailable, sysadmin);
    IntegrationInfo dropboxAvailableReloaded2 =
        integrationsHandler.getIntegration(user, propertyToUpdate);
    assertNotSame(dropboxAvailableReloaded, dropboxAvailableReloaded2);
    // save by id
    sysPropMger.save(dropboxAvailable.getId(), dropboxAvailable.getValue(), sysadmin);
    IntegrationInfo dropboxAvailableReloaded3 =
        integrationsHandler.getIntegration(user, propertyToUpdate);
    assertNotSame(dropboxAvailableReloaded2, dropboxAvailableReloaded3);

    // now let's update a child property; should refresh all properties
    SystemPropertyValue dropboxLinking =
        sysPropMger.findByName(SystemPropertyName.DROPBOX_LINKING_ENABLED);
    sysPropMger.save(dropboxLinking, sysadmin);
    IntegrationInfo dropboxAvailableReloaded4 =
        integrationsHandler.getIntegration(user, propertyToUpdate);
    assertNotSame(dropboxAvailableReloaded3, dropboxAvailableReloaded4);
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
    User anyUser = createAndSaveUser(getRandomAlphabeticString("user"));
    initUser(anyUser);
    // add a user connection
    UserConnection protocolsIOConnection = TestFactory.createUserConnection(anyUser.getUsername());
    protocolsIOConnection.getId().setProviderId(PROTOCOLS_IO_APP_NAME);
    protocolsIOConnection = userConn.save(protocolsIOConnection);

    // this will cache the info
    IntegrationInfo info = integrationsHandler.getIntegration(anyUser, PROTOCOLS_IO_APP_NAME);
    IntegrationInfo infoCached = integrationsHandler.getIntegration(anyUser, PROTOCOLS_IO_APP_NAME);
    assertSame(infoCached, info);
    assertEquals(
        getAccessTokenFromIntegrationInfo(info), getAccessTokenFromIntegrationInfo(infoCached));

    // now set new access token - this should trigger a reload and re-cache.
    protocolsIOConnection =
        userConn
            .findByUserNameProviderName(anyUser.getUsername(), PROTOCOLS_IO_APP_NAME)
            .orElseThrow();
    protocolsIOConnection.setAccessToken("newToken");
    protocolsIOConnection = userConn.save(protocolsIOConnection);
    IntegrationInfo reloaded = integrationsHandler.getIntegration(anyUser, PROTOCOLS_IO_APP_NAME);
    IntegrationInfo reloadedCached =
        integrationsHandler.getIntegration(anyUser, PROTOCOLS_IO_APP_NAME);
    assertEquals("newToken", getAccessTokenFromIntegrationInfo(reloaded));
    // sanity check that it is now being cached again
    assertSame(reloadedCached, reloaded);

    // now we remove the UserConnection, should evict cache
    userConn.deleteByUserAndProvider(anyUser.getUsername(), PROTOCOLS_IO_APP_NAME);
    IntegrationInfo reloaded2 = integrationsHandler.getIntegration(anyUser, PROTOCOLS_IO_APP_NAME);
    // ... and is now picking up  no UserConnection
    assertNotSame(reloaded, reloaded2);
    assertNull(getAccessTokenFromIntegrationInfo(reloaded2));
  }

  @Test
  public void testCachingForAccessTokensStoredInUserConnectionForNonProtocolsIO() {
    User anyUser = createAndSaveUser(getRandomAlphabeticString("user"));
    initUser(anyUser);
    // add a user connection
    UserConnection fieldmarkConnection = TestFactory.createUserConnection(anyUser.getUsername());
    fieldmarkConnection.getId().setProviderId(FIELDMARK_APP_NAME);
    fieldmarkConnection = userConn.save(fieldmarkConnection);

    // this will cache the info
    IntegrationInfo info = integrationsHandler.getIntegration(anyUser, FIELDMARK_APP_NAME);
    IntegrationInfo infoCached = integrationsHandler.getIntegration(anyUser, FIELDMARK_APP_NAME);
    assertSame(infoCached, info);
    assertEquals(
        getAccessTokenFromIntegrationInfo(info), getAccessTokenFromIntegrationInfo(infoCached));

    // now set new access token - this should trigger a reload and re-cache.
    fieldmarkConnection =
        userConn
            .findByUserNameProviderName(anyUser.getUsername(), FIELDMARK_APP_NAME)
            .orElseThrow();
    fieldmarkConnection.setAccessToken("newToken");
    fieldmarkConnection = userConn.save(fieldmarkConnection);
    IntegrationInfo reloaded = integrationsHandler.getIntegration(anyUser, FIELDMARK_APP_NAME);
    IntegrationInfo reloadedCached =
        integrationsHandler.getIntegration(anyUser, FIELDMARK_APP_NAME);
    assertThat(reloaded.getOptions()).containsEntry(FIELDMARK_USER_TOKEN, null);
    // sanity check that it is now being cached again
    assertSame(reloadedCached, reloaded);

    // now we remove the UserConnection, should evict cache
    userConn.deleteByUserAndProvider(anyUser.getUsername(), FIELDMARK_APP_NAME);
    IntegrationInfo reloaded2 = integrationsHandler.getIntegration(anyUser, FIELDMARK_APP_NAME);
    // ... and is now picking up  no UserConnection
    assertNotSame(reloaded, reloaded2);
    assertNull(reloaded2.getOptions().get(FIELDMARK_USER_TOKEN));
  }

  private Object getAccessTokenFromIntegrationInfo(IntegrationInfo infoCached) {
    return infoCached.getOptions().get(ACCESS_TOKEN_SETTING);
  }
}

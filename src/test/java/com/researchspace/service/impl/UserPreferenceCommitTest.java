package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.CacheNames;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.dao.CommunityDao;
import com.researchspace.dao.RoleDao;
import com.researchspace.dao.UserAccountEventDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.preference.Preference;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IVerificationPasswordValidator;
import com.researchspace.service.MessageSourceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class UserPreferenceCommitTest {
  private final UserDao dao = mock(UserDao.class);
  private final CacheManager caches = mock(CacheManager.class);
  private final Cache preferences = mock(Cache.class);
  private final Cache integrations = mock(Cache.class);
  private final UserManagerImpl manager =
      spy(
          new UserManagerImpl(
              dao,
              caches,
              mock(CommunityDao.class),
              mock(RoleDao.class),
              mock(UserAccountEventDao.class),
              mock(IPermissionUtils.class),
              mock(AnalyticsManager.class),
              mock(IPropertyHolder.class),
              mock(IVerificationPasswordValidator.class),
              mock(MessageSourceUtils.class),
              mock(RSpacePasswordEncoder.class),
              mock(LoginBookkeeping.class)));
  private final Preference preference = Preference.NOTIFICATION_DOCUMENT_SHARED_PREF;
  private UserPreference saved;

  @BeforeEach
  void setup() {
    TransactionSynchronizationManager.initSynchronization();
    User user = new User();
    saved = new UserPreference(preference, user, "false");
    doReturn(user).when(manager).getUserByUsername("alice", true);
    when(dao.upsertPreference(user, preference, "false")).thenReturn(saved);
  }

  @AfterEach
  void cleanup() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  @Test
  void evictsBothCachesOnlyAfterCommit() {
    when(caches.getCache(CacheNames.USER_PREFERENCE)).thenReturn(preferences);
    when(caches.getCache(CacheNames.INTEGRATION_INFO)).thenReturn(integrations);
    assertSame(saved, manager.setPreference(preference, "false", "alice"));
    verifyNoInteractions(caches);
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(preferences).evict("alice" + preference.name());
    verify(integrations).evict("alice" + preference.name());
  }

  @Test
  void rollbackLeavesCachesUntouched() {
    manager.setPreference(preference, "false", "alice");
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(
            callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    verifyNoInteractions(caches);
  }
}

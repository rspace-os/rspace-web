package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.CryptoUtils;
import com.researchspace.model.User;
import com.researchspace.model.oauth.OAuthApp;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class OAuthAppDaoTest extends SpringTransactionalTest {
  @Autowired private OAuthAppDao appDao;

  @Test
  public void listAppsForUser() {
    User user1 = createAndSaveRandomUser();
    User user2 = createAndSaveRandomUser();

    appDao.save(createOAuthApp(user1, "app1"));
    appDao.save(createOAuthApp(user1, "app2"));
    appDao.save(createOAuthApp(user2, "app1"));
    appDao.save(createOAuthApp(user2, "app3"));

    assertEquals(2, appDao.getApps(user1.getId()).size());
    assertEquals(2, appDao.getApps(user2.getId()).size());
    assertEquals(0, appDao.getApps(-100L).size()); // Non existing user id
  }

  @Test
  public void saveAndDeleteApp() {
    User user = createAndSaveRandomUser();

    OAuthApp app = createOAuthApp(user, "app1");

    appDao.save(app);

    assertTrue(appDao.getApp(user.getId(), app.getClientId()).isPresent());

    boolean isAppRemoved = appDao.removeApp(user.getId(), app.getClientId());

    assertTrue(isAppRemoved);
    assertFalse(appDao.getApp(user.getId(), app.getClientId()).isPresent());

    isAppRemoved = appDao.removeApp(user.getId(), app.getClientId());
    assertFalse(isAppRemoved);
  }

  private OAuthApp createOAuthApp(User user, String name) {
    return new OAuthApp(
        user, name, CryptoUtils.generateClientId(), CryptoUtils.generateHashedClientSecret());
  }
}

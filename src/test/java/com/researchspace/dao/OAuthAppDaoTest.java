package com.researchspace.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.util.CryptoUtils;
import com.researchspace.model.User;
import com.researchspace.model.oauth.OAuthApp;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.Test;
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

    assertThat(appDao.getApps(user1.getId())).hasSize(2);
    assertThat(appDao.getApps(user2.getId())).hasSize(2);
    assertThat(appDao.getApps(-100L)).isEmpty(); // Non existing user id
  }

  @Test
  public void saveAndDeleteApp() {
    User user = createAndSaveRandomUser();

    OAuthApp app = createOAuthApp(user, "app1");

    appDao.save(app);

    assertThat(appDao.getApp(user.getId(), app.getClientId())).isPresent();

    boolean isAppRemoved = appDao.removeApp(user.getId(), app.getClientId());

    assertTrue(isAppRemoved);
    assertThat(appDao.getApp(user.getId(), app.getClientId())).isNotPresent();

    isAppRemoved = appDao.removeApp(user.getId(), app.getClientId());
    assertFalse(isAppRemoved);
  }

  private OAuthApp createOAuthApp(User user, String name) {
    return new OAuthApp(
        user, name, CryptoUtils.generateClientId(), CryptoUtils.generateHashedClientSecret());
  }
}

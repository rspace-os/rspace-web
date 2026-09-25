package com.researchspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.frontend.OAuthAppInfo;
import com.researchspace.model.frontend.PublicOAuthAppInfo;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class OAuthAppManagerTest extends SpringTransactionalTest {
  @Autowired private OAuthAppManager appManager;

  @Test
  public void testAddViewRemoveApp() {
    List<PublicOAuthAppInfo> apps;

    User user = createAndSaveRandomUser();

    ServiceOperationResult<OAuthAppInfo> additionResult = appManager.addApp(user, "newApp1");
    assertTrue(additionResult.isSucceeded());

    apps = appManager.getApps(user);
    assertThat(apps).hasSize(1);

    OAuthAppInfo appInfo = additionResult.getEntity();
    assertThat(apps).contains(new PublicOAuthAppInfo(appInfo.getAppName(), appInfo.getClientId()));

    assertTrue(
        appManager.isClientSecretCorrect(appInfo.getClientId(), appInfo.getUnhashedClientSecret()));

    ServiceOperationResult<Void> removalResult = appManager.removeApp(user, appInfo.getClientId());
    assertTrue(removalResult.isSucceeded());

    apps = appManager.getApps(user);
    assertThat(apps).isEmpty();
  }

  @Test
  public void testActionsRestrictedToAppCreator() {
    User user1 = createAndSaveRandomUser();
    User user2 = createAndSaveRandomUser();

    OAuthAppInfo app1 = appManager.addApp(user1, "newApp1").getEntity();
    OAuthAppInfo app2 = appManager.addApp(user2, "newApp2").getEntity();

    assertFalse(appManager.removeApp(user1, app2.getClientId()).isSucceeded());
    assertThat(appManager.getApp(user2, app1.getClientId())).isNotPresent();
    assertThat(appManager.getApps(user1)).hasSize(1);
    assertThat(appManager.getApps(user2)).hasSize(1);
  }

  @Test
  public void testClientSecretVerification() {
    User user = createAndSaveRandomUser();

    OAuthAppInfo app = appManager.addApp(user, "newApp1").getEntity();

    assertTrue(appManager.isClientSecretCorrect(app.getClientId(), app.getUnhashedClientSecret()));
    assertFalse(appManager.isClientSecretCorrect(app.getClientId(), "IncorrectClientSecret"));
    assertFalse(
        appManager.isClientSecretCorrect("incorrectClientId", app.getUnhashedClientSecret()));
  }
}

package com.researchspace.dao.customliquibaseupdates;

import static com.researchspace.service.IntegrationsHandler.GITHUB_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.SLACK_APP_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Map;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Recreates the plaintext settings the migration removes, since the test database has already run
 * it, and checks the values end up encrypted in UserConnection.
 */
class MoveGitHubAndSlackSecretsToUserConnection_RSDEV1525Test extends SpringTransactionalTest {

  private @Autowired UserAppConfigManager appConfigManager;
  private @Autowired UserConnectionManager userConnectionManager;

  @Test
  void movesTheSecretsIntoEncryptedConnectionsAndDeletesTheSettings() {
    User user = createAndSaveRandomUser();
    logoutAndLoginAs(user);
    Long gitHubSet =
        newSet(user, Map.of("GITHUB_REPOSITORY_FULL_NAME", "owner/repo"), App.APP_GITHUB);
    Long slackSet = newSlackChannel(user, "C1");
    // channels connected before Slack tokens were stored have a webhook URL only
    Long webhookOnlySlackSet = newSlackChannel(user, "C2");
    Session session = sessionFactory.getCurrentSession();
    addLegacyDescriptor(session, -1525, "app.github", "GITHUB_ACCESS_TOKEN");
    addLegacyDescriptor(session, -1526, "app.slack", "SLACK_USER_ACCESS_TOKEN");
    addLegacyDescriptor(session, -1527, "app.slack", "SLACK_WEBHOOK_URL");
    addLegacyValue(session, -1525, gitHubSet, "gh-token");
    addLegacyValue(session, -1526, slackSet, "xoxp-1");
    addLegacyValue(session, -1527, slackSet, "https://hook");
    addLegacyValue(session, -1527, webhookOnlySlackSet, "https://old-hook");
    session.flush();
    session.clear();

    MoveGitHubAndSlackSecretsToUserConnection_RSDEV1525 migration =
        new MoveGitHubAndSlackSecretsToUserConnection_RSDEV1525();
    migration.context = applicationContext;
    migration.sessionFactory = sessionFactory;
    migration.addBeans();
    migration.doExecute(null);
    session.flush();

    assertNotEquals(
        "gh-token",
        session
            .createNativeQuery(
                "select accessToken from UserConnection where userId = :user and providerId ="
                    + " 'GITHUB'",
                String.class)
            .setParameter("user", user.getUsername())
            .getSingleResult());
    session.clear();
    assertEquals(
        "gh-token",
        userConnectionManager
            .findByUserNameProviderName(user.getUsername(), GITHUB_APP_NAME, GITHUB_APP_NAME)
            .map(UserConnection::getAccessToken)
            .orElseThrow());
    UserConnection slack =
        userConnectionManager
            .findByUserNameProviderName(user.getUsername(), SLACK_APP_NAME, slackSet.toString())
            .orElseThrow();
    assertEquals("xoxp-1", slack.getAccessToken());
    assertEquals("https://hook", slack.getSecret());
    UserConnection webhookOnly =
        userConnectionManager
            .findByUserNameProviderName(
                user.getUsername(), SLACK_APP_NAME, webhookOnlySlackSet.toString())
            .orElseThrow();
    assertEquals("", webhookOnly.getAccessToken());
    assertEquals("https://old-hook", webhookOnly.getSecret());
    assertEquals(
        0L,
        session
            .createNativeQuery(
                "select count(*) from AppConfigElementDescriptor where id in (-1525, -1526, -1527)",
                Long.class)
            .getSingleResult());
  }

  private Long newSlackChannel(User user, String channelId) {
    return newSet(
        user,
        Map.of(
            "SLACK_TEAM_NAME",
            "Team",
            "SLACK_CHANNEL_NAME",
            "#" + channelId,
            "SLACK_CHANNEL_LABEL",
            "#" + channelId,
            "SLACK_USER_ID",
            "U1",
            "SLACK_TEAM_ID",
            "T1",
            "SLACK_CHANNEL_ID",
            channelId),
        App.APP_SLACK);
  }

  // the newest set of the app, i.e. the one just saved
  private Long newSet(User user, Map<String, String> settings, String appName) {
    return appConfigManager
        .saveAppConfigElementSet(settings, null, true, user, appName)
        .getAppConfigElementSets()
        .stream()
        .filter(set -> set.getApp().getName().equals(appName))
        .mapToLong(AppConfigElementSet::getId)
        .max()
        .orElseThrow();
  }

  private static void addLegacyDescriptor(
      Session session, long descriptorId, String app, String setting) {
    session
        .createNativeQuery(
            "insert into AppConfigElementDescriptor (id, descriptor_id, app_id)"
                + " select :id, pd.id, a.id from PropertyDescriptor pd, App a"
                + " where pd.name = :setting and a.name = :app")
        .setParameter("id", descriptorId)
        .setParameter("setting", setting)
        .setParameter("app", app)
        .executeUpdate();
  }

  private static void addLegacyValue(Session session, long descriptorId, Long setId, String value) {
    session
        .createNativeQuery(
            "insert into AppConfigElement (value, appConfigElementDescriptor_id,"
                + " appConfigElementSet_id) values (:value, :descriptor, :set)")
        .setParameter("value", value)
        .setParameter("descriptor", descriptorId)
        .setParameter("set", setId)
        .executeUpdate();
  }
}

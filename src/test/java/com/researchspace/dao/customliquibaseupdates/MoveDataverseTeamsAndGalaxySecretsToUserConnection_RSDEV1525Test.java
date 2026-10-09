package com.researchspace.dao.customliquibaseupdates;

import static com.researchspace.service.IntegrationsHandler.DATAVERSE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.MSTEAMS_APP_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Map;
import liquibase.database.Database;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class MoveDataverseTeamsAndGalaxySecretsToUserConnection_RSDEV1525Test
    extends SpringTransactionalTest {

  private static final String GALAXY_APP_NAME = "GALAXY";
  private @Autowired UserAppConfigManager appConfigManager;
  private @Autowired UserConnectionManager userConnectionManager;

  @Test
  void copiesAndVerifiesAllCredentialsBeforeDeletingTheirSettings() {
    User user = createAndSaveRandomUser();
    logoutAndLoginAs(user);
    Long dataverseSet =
        newSet(
            user,
            App.APP_DATAVERSE,
            Map.of("DATAVERSE_ALIAS", "tenant", "DATAVERSE_URL", "https://dataverse.example"));
    Long teamsSet = newSet(user, App.APP_MSTEAMS, Map.of("MSTEAMS_CHANNEL_LABEL", "general"));
    Long galaxySet =
        newSet(
            user,
            "app.galaxy",
            Map.of("GALAXY_ALIAS", "galaxy", "GALAXY_URL", "https://galaxy.example"));
    userConnectionManager.save(
        new UserConnection(
            new UserConnectionId(user.getUsername(), DATAVERSE_APP_NAME, dataverseSet.toString()),
            ""));
    Session session = sessionFactory.getCurrentSession();
    addLegacyDescriptor(session, -1530, App.APP_DATAVERSE, "DATAVERSE_APIKEY");
    addLegacyDescriptor(session, -1531, App.APP_MSTEAMS, "MSTEAMS_WEBHOOK_URL");
    addLegacyDescriptor(session, -1532, "app.galaxy", "GALAXY_APIKEY");
    addLegacyValue(session, -1530, dataverseSet, "dataverse-token");
    addLegacyValue(session, -1531, teamsSet, "https://teams.example/webhook");
    addLegacyValue(session, -1532, galaxySet, "galaxy-token");
    session.flush();
    session.clear();

    MoveDataverseTeamsAndGalaxySecretsToUserConnection_RSDEV1525 migration = newMigration();
    migration.doExecute(null);
    session.flush();
    session.clear();

    UserConnection dataverse =
        userConnectionManager
            .findByUserNameProviderName(
                user.getUsername(), DATAVERSE_APP_NAME, dataverseSet.toString())
            .orElseThrow();
    assertEquals("dataverse-token", dataverse.getAccessToken());
    assertNotEquals(
        "dataverse-token",
        rawCredential(
            user.getUsername(), DATAVERSE_APP_NAME, dataverseSet.toString(), "accessToken"));

    UserConnection teams =
        userConnectionManager
            .findByUserNameProviderName(user.getUsername(), MSTEAMS_APP_NAME, teamsSet.toString())
            .orElseThrow();
    assertEquals("https://teams.example/webhook", teams.getSecret());
    assertNotEquals(
        "https://teams.example/webhook",
        rawCredential(user.getUsername(), MSTEAMS_APP_NAME, teamsSet.toString(), "secret"));

    UserConnection galaxy =
        userConnectionManager
            .findByUserNameProviderName(user.getUsername(), GALAXY_APP_NAME, "galaxy")
            .orElseThrow();
    assertEquals("galaxy-token", galaxy.getAccessToken());
    assertNotEquals(
        "galaxy-token",
        rawCredential(user.getUsername(), GALAXY_APP_NAME, "galaxy", "accessToken"));
    assertEquals(0L, plaintextSettingCount(session, App.APP_DATAVERSE, "DATAVERSE_APIKEY"));
    assertEquals(0L, plaintextSettingCount(session, App.APP_MSTEAMS, "MSTEAMS_WEBHOOK_URL"));
    assertEquals(0L, plaintextSettingCount(session, "app.galaxy", "GALAXY_APIKEY"));
  }

  @Test
  void conflictingExistingConnectionAbortsBeforeDeletingPlaintext() {
    User user = createAndSaveRandomUser();
    logoutAndLoginAs(user);
    Long dataverseSet =
        newSet(
            user,
            App.APP_DATAVERSE,
            Map.of("DATAVERSE_ALIAS", "tenant", "DATAVERSE_URL", "https://dataverse.example"));
    Session session = sessionFactory.getCurrentSession();
    addLegacyDescriptor(session, -1530, App.APP_DATAVERSE, "DATAVERSE_APIKEY");
    addLegacyValue(session, -1530, dataverseSet, "legacy-token");
    UserConnection existing =
        new UserConnection(
            new UserConnectionId(user.getUsername(), DATAVERSE_APP_NAME, dataverseSet.toString()),
            "different-token");
    userConnectionManager.save(existing);
    session.flush();
    session.clear();

    MoveDataverseTeamsAndGalaxySecretsToUserConnection_RSDEV1525 migration = newMigration();

    assertThrows(IllegalStateException.class, () -> migration.doExecute(null));
    assertEquals(1L, plaintextSettingCount(session, App.APP_DATAVERSE, "DATAVERSE_APIKEY"));
    assertEquals(
        "different-token",
        userConnectionManager
            .findByUserNameProviderName(
                user.getUsername(), DATAVERSE_APP_NAME, dataverseSet.toString())
            .orElseThrow()
            .getAccessToken());
  }

  @Test
  void executeRollsBackWhenTheMigrationFails() throws Exception {
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    TransactionStatus status = mock(TransactionStatus.class);
    ApplicationContext context = mock(ApplicationContext.class);
    when(context.getBean(PlatformTransactionManager.class)).thenReturn(txManager);
    when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
    MoveDataverseTeamsAndGalaxySecretsToUserConnection_RSDEV1525 migration =
        new MoveDataverseTeamsAndGalaxySecretsToUserConnection_RSDEV1525() {
          @Override
          protected void doExecute(Database database) {
            throw new IllegalStateException("migration failed");
          }
        };
    migration.context = context;

    assertThrows(IllegalStateException.class, () -> migration.execute(null));

    verify(txManager).rollback(status);
    verify(txManager, never()).commit(status);
  }

  private MoveDataverseTeamsAndGalaxySecretsToUserConnection_RSDEV1525 newMigration() {
    MoveDataverseTeamsAndGalaxySecretsToUserConnection_RSDEV1525 migration =
        new MoveDataverseTeamsAndGalaxySecretsToUserConnection_RSDEV1525();
    migration.context = applicationContext;
    migration.sessionFactory = sessionFactory;
    migration.addBeans();
    return migration;
  }

  private Long newSet(User user, String appName, Map<String, String> settings) {
    return appConfigManager
        .saveAppConfigElementSet(settings, null, true, user, appName)
        .getAppConfigElementSets()
        .stream()
        .filter(set -> set.getApp().getName().equals(appName))
        .mapToLong(AppConfigElementSet::getId)
        .max()
        .orElseThrow();
  }

  private void addLegacyDescriptor(Session session, long descriptorId, String app, String setting) {
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

  private void addLegacyValue(Session session, long descriptorId, Long setId, String value) {
    session
        .createNativeQuery(
            "insert into AppConfigElement (value, appConfigElementDescriptor_id,"
                + " appConfigElementSet_id) values (:value, :descriptor, :set)")
        .setParameter("value", value)
        .setParameter("descriptor", descriptorId)
        .setParameter("set", setId)
        .executeUpdate();
  }

  private String rawCredential(String username, String provider, String id, String column) {
    return sessionFactory
        .getCurrentSession()
        .createNativeQuery(
            "select "
                + column
                + " from UserConnection"
                + " where userId = :username and providerId = :provider and providerUserId = :id",
            String.class)
        .setParameter("username", username)
        .setParameter("provider", provider)
        .setParameter("id", id)
        .getSingleResult();
  }

  private long plaintextSettingCount(Session session, String appName, String settingName) {
    return session
        .createNativeQuery(
            "select count(*) from AppConfigElement e"
                + " join AppConfigElementDescriptor d on e.appConfigElementDescriptor_id = d.id"
                + " join PropertyDescriptor pd on d.descriptor_id = pd.id"
                + " join App a on d.app_id = a.id"
                + " where a.name = :app and pd.name = :setting",
            Long.class)
        .setParameter("app", appName)
        .setParameter("setting", settingName)
        .getSingleResult();
  }
}

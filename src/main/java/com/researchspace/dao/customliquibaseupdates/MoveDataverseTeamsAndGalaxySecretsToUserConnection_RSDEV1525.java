package com.researchspace.dao.customliquibaseupdates;

import static com.researchspace.model.apps.App.APP_DATAVERSE;
import static com.researchspace.model.apps.App.APP_MSTEAMS;
import static com.researchspace.service.IntegrationsHandler.DATAVERSE_APIKEY;
import static com.researchspace.service.IntegrationsHandler.GALAXY_APIKEY;
import static com.researchspace.service.IntegrationsHandler.MSTEAMS_WEBHOOK_URL;

import com.researchspace.dao.UserConnectionDao;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import java.util.List;
import java.util.Optional;
import liquibase.database.Database;
import liquibase.exception.CustomChangeException;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Moves remaining plaintext Dataverse, Teams, and Galaxy credentials into encrypted connections.
 */
public class MoveDataverseTeamsAndGalaxySecretsToUserConnection_RSDEV1525
    extends AbstractCustomLiquibaseUpdater {

  private static final String APP_GALAXY = "app.galaxy";
  private UserConnectionDao userConnectionDao;
  private int moved;

  @Override
  protected void addBeans() {
    userConnectionDao = context.getBean(UserConnectionDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Moved " + moved + " Dataverse, Teams, and Galaxy credentials to UserConnection";
  }

  /** Rolls back copied credentials if verification or cleanup fails. */
  @Override
  public void execute(Database database) throws CustomChangeException {
    PlatformTransactionManager txManager = getTxMger();
    openTransaction(txManager);
    try {
      doExecute(database);
      commitTransaction();
    } catch (RuntimeException | Error failure) {
      if (!status.isCompleted()) {
        try {
          txManager.rollback(status);
        } catch (RuntimeException rollbackFailure) {
          failure.addSuppressed(rollbackFailure);
        }
      }
      throw failure;
    }
  }

  @Override
  protected void doExecute(Database database) {
    Session session = sessionFactory.getCurrentSession();
    List<AppConfigElementSet> sets =
        session
            .createQuery(
                "from AppConfigElementSet where userAppConfig.app.name in (:apps) order by id",
                AppConfigElementSet.class)
            .setParameterList("apps", List.of(APP_DATAVERSE, APP_MSTEAMS, APP_GALAXY))
            .list();

    for (AppConfigElementSet set : sets) {
      String appName = set.getApp().getName();
      String providerName = set.getApp().toIntegrationInfoName();
      String settingName = settingFor(appName);
      String credential = value(set, settingName);
      if (!StringUtils.isEmpty(credential)) {
        String discriminant = discriminantFor(set, appName);
        boolean secretField = APP_MSTEAMS.equals(appName);
        copyAndVerify(
            session,
            set.getUserAppConfig().getUser().getUsername(),
            providerName,
            discriminant,
            credential,
            secretField,
            set.getId());
        moved++;
      }
    }

    deleteSetting(session, APP_DATAVERSE, DATAVERSE_APIKEY);
    deleteSetting(session, APP_MSTEAMS, MSTEAMS_WEBHOOK_URL);
    deleteSetting(session, APP_GALAXY, GALAXY_APIKEY);
    session.flush();
    verifyNoPlaintextSettingsRemain(session);
  }

  private void copyAndVerify(
      Session session,
      String username,
      String providerName,
      String discriminant,
      String credential,
      boolean secretField,
      Long configSetId) {
    Optional<UserConnection> existing =
        userConnectionDao.findByUserNameProviderName(username, providerName, discriminant);
    UserConnection connection;
    if (existing.isPresent()) {
      connection = existing.get();
      String existingCredential =
          secretField ? connection.getSecret() : connection.getAccessToken();
      if (StringUtils.isNotEmpty(existingCredential) && !credential.equals(existingCredential)) {
        throw new IllegalStateException(
            "Conflicting stored credential for " + providerName + " config " + discriminant);
      }
    } else {
      connection =
          new UserConnection(new UserConnectionId(username, providerName, discriminant), "");
      connection.setDisplayName(providerName + " credential");
      connection.setRank(Math.toIntExact(configSetId));
    }
    if (secretField) {
      connection.setSecret(credential);
    } else {
      connection.setAccessToken(credential);
    }
    connection.setExpireTime(0L);
    userConnectionDao.save(connection);
    session.flush();
    session.evict(connection);

    UserConnection persisted =
        userConnectionDao
            .findByUserNameProviderName(username, providerName, discriminant)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Could not verify migrated credential for "
                            + providerName
                            + " config "
                            + discriminant));
    String decryptedCredential = secretField ? persisted.getSecret() : persisted.getAccessToken();
    if (!persisted.isEncrypted() || !credential.equals(decryptedCredential)) {
      throw new IllegalStateException(
          "Could not verify encrypted credential for " + providerName + " config " + discriminant);
    }
    String encryptedCredential =
        session
            .createNativeQuery(
                "select "
                    + (secretField ? "secret" : "accessToken")
                    + " from UserConnection where userId = :username and providerId = :provider and"
                    + " providerUserId = :id",
                String.class)
            .setParameter("username", username)
            .setParameter("provider", providerName)
            .setParameter("id", discriminant)
            .getSingleResult();
    if (credential.equals(encryptedCredential)) {
      throw new IllegalStateException(
          "Credential was not encrypted for " + providerName + " config " + discriminant);
    }
  }

  private static String settingFor(String appName) {
    return switch (appName) {
      case APP_DATAVERSE -> DATAVERSE_APIKEY;
      case APP_MSTEAMS -> MSTEAMS_WEBHOOK_URL;
      case APP_GALAXY -> GALAXY_APIKEY;
      default -> throw new IllegalArgumentException("Unexpected app " + appName);
    };
  }

  private static String discriminantFor(AppConfigElementSet set, String appName) {
    if (APP_GALAXY.equals(appName)) {
      String alias = value(set, "GALAXY_ALIAS");
      if (StringUtils.isEmpty(alias)) {
        throw new IllegalStateException(
            "Galaxy API key has no server alias for config " + set.getId());
      }
      return alias;
    }
    if (set.getId() == null) {
      throw new IllegalStateException("App config has no id for " + set.getApp().getName());
    }
    return String.valueOf(set.getId());
  }

  private static String value(AppConfigElementSet set, String settingName) {
    AppConfigElement element = set.findElementByPropertyName(settingName);
    return element == null ? null : element.getValue();
  }

  private static void deleteSetting(Session session, String appName, String settingName) {
    session
        .createNativeQuery(
            "delete from AppConfigElement where appConfigElementDescriptor_id in ("
                + " select d.id from AppConfigElementDescriptor d"
                + " join PropertyDescriptor pd on d.descriptor_id = pd.id"
                + " join App a on d.app_id = a.id"
                + " where a.name = :app and pd.name = :setting)")
        .setParameter("app", appName)
        .setParameter("setting", settingName)
        .executeUpdate();
    session
        .createNativeQuery(
            "delete from AppConfigElementDescriptor"
                + " where app_id in (select id from App where name = :app)"
                + " and descriptor_id in (select id from PropertyDescriptor where name = :setting)")
        .setParameter("app", appName)
        .setParameter("setting", settingName)
        .executeUpdate();
  }

  private static void verifyNoPlaintextSettingsRemain(Session session) {
    for (String[] setting :
        List.of(
            new String[] {APP_DATAVERSE, DATAVERSE_APIKEY},
            new String[] {APP_MSTEAMS, MSTEAMS_WEBHOOK_URL},
            new String[] {APP_GALAXY, GALAXY_APIKEY})) {
      Long count =
          session
              .createNativeQuery(
                  "select count(*) from AppConfigElement e join AppConfigElementDescriptor d on"
                      + " e.appConfigElementDescriptor_id = d.id join PropertyDescriptor pd on"
                      + " d.descriptor_id = pd.id join App a on d.app_id = a.id where a.name = :app"
                      + " and pd.name = :setting",
                  Long.class)
              .setParameter("app", setting[0])
              .setParameter("setting", setting[1])
              .getSingleResult();
      if (count != 0) {
        throw new IllegalStateException(
            "Plaintext setting remains for " + setting[0] + ": " + setting[1]);
      }
    }
  }
}

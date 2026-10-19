package com.researchspace.dao.customliquibaseupdates;

import static com.researchspace.service.IntegrationsHandler.GITHUB_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.SLACK_APP_NAME;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.researchspace.dao.UserConnectionDao;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import java.util.List;
import liquibase.database.Database;
import org.hibernate.Session;

/**
 * RSDEV-1525: GitHub and Slack kept their credentials as plaintext app settings. This copies them
 * into the encrypted UserConnection table, where the controllers now read them, and then deletes
 * the app settings: one GitHub token per repository, and one Slack connection per channel holding
 * its token and webhook URL, keyed by the config set id.
 */
public class MoveGitHubAndSlackSecretsToUserConnection_RSDEV1525
    extends AbstractCustomLiquibaseUpdater {

  private UserConnectionDao userConnectionDao;
  private int moved = 0;

  @Override
  protected void addBeans() {
    userConnectionDao = context.getBean(UserConnectionDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Moved " + moved + " GitHub and Slack credentials to UserConnection";
  }

  @Override
  protected void doExecute(Database database) {
    Session session = sessionFactory.getCurrentSession();
    List<AppConfigElementSet> sets =
        session
            .createQuery(
                "from AppConfigElementSet where userAppConfig.app.name in (:apps) order by id",
                AppConfigElementSet.class)
            .setParameterList("apps", List.of(App.APP_GITHUB, App.APP_SLACK))
            .list();

    for (AppConfigElementSet set : sets) {
      String username = set.getUserAppConfig().getUser().getUsername();
      if (App.APP_GITHUB.equals(set.getUserAppConfig().getApp().getName())) {
        String token = value(set, "GITHUB_ACCESS_TOKEN");
        if (!isEmpty(token)) {
          save(
              new UserConnection(
                  new UserConnectionId(username, GITHUB_APP_NAME, String.valueOf(set.getId())),
                  token),
              set);
        }
      } else {
        String token = value(set, "SLACK_USER_ACCESS_TOKEN");
        String webhookUrl = value(set, "SLACK_WEBHOOK_URL");
        if (!isEmpty(token) || !isEmpty(webhookUrl)) {
          UserConnection connection =
              new UserConnection(
                  new UserConnectionId(username, SLACK_APP_NAME, String.valueOf(set.getId())),
                  token == null ? "" : token);
          connection.setSecret(webhookUrl);
          save(connection, set);
        }
      }
    }

    deleteSetting(session, App.APP_GITHUB, "GITHUB_ACCESS_TOKEN");
    deleteSetting(session, App.APP_SLACK, "SLACK_USER_ACCESS_TOKEN");
    deleteSetting(session, App.APP_SLACK, "SLACK_WEBHOOK_URL");
  }

  private void save(UserConnection connection, AppConfigElementSet set) {
    // as the connect flow does: set ids are unique, so ranks cannot collide
    connection.setRank(Math.toIntExact(set.getId()));
    userConnectionDao.save(connection);
    moved++;
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
}

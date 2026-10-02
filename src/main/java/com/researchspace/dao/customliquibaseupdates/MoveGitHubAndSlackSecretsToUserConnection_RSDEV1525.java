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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import liquibase.database.Database;
import org.hibernate.Session;

/**
 * RSDEV-1525: GitHub and Slack kept their credentials as plaintext app settings. This copies them
 * into the encrypted UserConnection table, where the controllers now read them, and then deletes
 * the app settings: one GitHub token per user, and one Slack connection per channel holding its
 * token and webhook URL, keyed by the channel's config set id.
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

    // ponytail: the connect flow now keeps one GitHub token per user, so the latest repository's
    // wins. Repositories linked from a second GitHub account need connecting again.
    Map<String, String> gitHubTokens = new LinkedHashMap<>();
    // a user's connections to one provider need distinct ranks
    Map<String, Integer> slackRanks = new HashMap<>();
    for (AppConfigElementSet set : sets) {
      String username = set.getUserAppConfig().getUser().getUsername();
      if (App.APP_GITHUB.equals(set.getUserAppConfig().getApp().getName())) {
        String token = value(set, "GITHUB_ACCESS_TOKEN");
        if (!isEmpty(token)) {
          gitHubTokens.put(username, token);
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
          connection.setRank(slackRanks.merge(username, 1, Integer::sum));
          save(connection);
        }
      }
    }
    gitHubTokens.forEach(
        (username, token) ->
            save(
                new UserConnection(
                    new UserConnectionId(username, GITHUB_APP_NAME, GITHUB_APP_NAME), token)));

    deleteSetting(session, App.APP_GITHUB, "GITHUB_ACCESS_TOKEN");
    deleteSetting(session, App.APP_SLACK, "SLACK_USER_ACCESS_TOKEN");
    deleteSetting(session, App.APP_SLACK, "SLACK_WEBHOOK_URL");
  }

  private void save(UserConnection connection) {
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

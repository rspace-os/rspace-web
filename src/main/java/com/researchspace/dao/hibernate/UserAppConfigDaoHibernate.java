package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.UserAppConfigDao;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository("userAppConfigDaoHibernate")
public class UserAppConfigDaoHibernate extends GenericDaoHibernate<UserAppConfig, Long>
    implements UserAppConfigDao {

  public UserAppConfigDaoHibernate() {
    super(UserAppConfig.class);
  }

  @Override
  public UserAppConfig findByPropertyNameUser(String propertyName, User user) {
    return getSession()
        .createQuery(
            "from UserAppConfig uac where user = :user and uac.app = (select appdesc.app from"
                + " AppConfigElementDescriptor appdesc where"
                + " appdesc.descriptor.name=:propertyName)",
            UserAppConfig.class)
        .setParameter("user", user)
        .setParameter("propertyName", propertyName)
        .uniqueResult();
  }

  @Override
  public App findAppByPropertyName(String propertyName) {
    return getSession()
        .createQuery(
            "SELECT app FROM App app inner join app.appConfigElementDescriptors  appdesc "
                + "where appdesc.descriptor.name=:propertyName",
            App.class)
        .setParameter("propertyName", propertyName)
        .uniqueResult();
  }

  @Override
  public AppConfigElementSet getAppConfigElementSetById(Long appConfigSetDataId) {
    return getSession().get(AppConfigElementSet.class, appConfigSetDataId);
  }

  @Override
  public void saveAppConfigElement(AppConfigElementSet saved) {
    getSession().merge(saved);
  }

  @Override
  public Optional<App> findAppByAppName(String appName) {
    App app =
        getSession()
            .createQuery("from App where name=:name", App.class)
            .setParameter("name", appName)
            .uniqueResult();
    return Optional.ofNullable(app);
  }

  @Override
  public Optional<UserAppConfig> findByAppUser(App app, User user) {
    UserAppConfig uac =
        getSession()
            .createQuery("from UserAppConfig where user=:user and app=:app", UserAppConfig.class)
            .setParameter("app", app)
            .setParameter("user", user)
            .uniqueResult();
    return Optional.ofNullable(uac);
  }

  @Override
  public Optional<AppConfigElementSet> findByAppConfigElementSetId(Long appConfigElementSetId) {
    AppConfigElementSet set = getSession().get(AppConfigElementSet.class, appConfigElementSetId);
    return Optional.ofNullable(set);
  }

  @Override
  public List<User> findUsersByPropertyValue(String propertyName, String value) {
    return getSession()
        .createQuery(
            "select userAppConfig.user "
                + "from UserAppConfig userAppConfig "
                + "inner join userAppConfig.appConfigElementSets appConfigElemSet "
                + "inner join appConfigElemSet.configElements configElement "
                + "inner join configElement.appConfigElementDescriptor appConfigElementDescriptor "
                + "inner join appConfigElementDescriptor.descriptor descriptor "
                + "where descriptor.name=:propertyName and configElement.value=:value",
            User.class)
        .setParameter("propertyName", propertyName)
        .setParameter("value", value)
        .list();
  }
}

package com.researchspace.service.impl;

import com.researchspace.dao.UserAppConfigDao;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementDescriptor;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.service.UserAppConfigManager;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.apache.commons.lang.Validate;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("appConfigManager")
public class AppConfigManagerImpl extends GenericManagerImpl<UserAppConfig, Long>
    implements UserAppConfigManager {

  Logger logger = LoggerFactory.getLogger(AppConfigManagerImpl.class);

  private @Autowired UserAppConfigDao appCfgDao;
  private @Autowired IPermissionUtils permUtils;

  public AppConfigManagerImpl() {
    super();
  }

  @PostConstruct
  public void init() {
    setDao(appCfgDao);
  }

  @Override
  public UserAppConfig getByAppName(String appName, User user) {
    Optional<App> appOpt = appCfgDao.findAppByAppName(appName);
    if (appOpt.isPresent()) {
      App app = appOpt.get();
      Optional<UserAppConfig> appConfig = appCfgDao.findByAppUser(app, user);
      return appConfig.orElseGet(
          () -> {
            UserAppConfig uacNew = new UserAppConfig(user, app, app.isDefaultEnabled());
            return appCfgDao.save(uacNew);
          });
    } else {
      throw new IllegalStateException("No app with name [" + appName + "]");
    }
  }

  public AppConfigElementSet getAppConfigElementSetById(Long appConfigSetDataId) {
    return appCfgDao.getAppConfigElementSetById(appConfigSetDataId);
  }

  @Override
  public UserAppConfig saveAppConfigElementSet(
      Map<String, String> appConfigSetData,
      Long appConfigSetDataId,
      boolean trustedOrigin,
      User user) {
    Validate.isTrue(!appConfigSetData.isEmpty(), "appConfigSetData is empty!");
    String anyProperty = appConfigSetData.keySet().iterator().next();

    UserAppConfig cfg = appCfgDao.findByPropertyNameUser(anyProperty, user);
    if (cfg == null) {
      logger.info(
          "No UserAppConfig set for user {} for property {}, setting",
          user.getUsername(),
          anyProperty);
      App app = appCfgDao.findAppByPropertyName(anyProperty);
      Validate.notNull(app, "App could not be identified for property " + anyProperty);
      cfg = new UserAppConfig(user, app, true);
      cfg = appCfgDao.save(cfg);
    }
    permUtils.assertIsPermitted(cfg, PermissionType.WRITE, user, " Update AppConfig ");
    App app = cfg.getApp();
    if (!trustedOrigin) {
      assertAppCanBeUpdatedByUntrustedOrigin(app);
    }
    AppConfigElementSet set = null;
    if (appConfigSetDataId == null) {
      set = createAppConfigElementSetFromMap(appConfigSetData, app);
      cfg.addConfigSet(set);
      appCfgDao.save(cfg);
    } else {
      AppConfigElementSet transientSet = createAppConfigElementSetFromMap(appConfigSetData, app);
      AppConfigElementSet saved = appCfgDao.getAppConfigElementSetById(appConfigSetDataId);
      Validate.isTrue(transientSet.propertiesMatch(saved), "properties do not match");
      saved.merge(transientSet);
      appCfgDao.saveAppConfigElement(saved);
    }
    return appCfgDao.get(cfg.getId());
  }

  private void assertAppCanBeUpdatedByUntrustedOrigin(App app) {
    if ("app.orcid".equals(app.getName())) {
      throw new AuthorizationException("This App cannot be updated this way");
    }
    ;
  }

  private AppConfigElementSet createAppConfigElementSetFromMap(
      Map<String, String> appConfigSetData, App app) {
    AppConfigElementSet rc = new AppConfigElementSet();
    Validate.isTrue(
        app.getConfigElementCount() == appConfigSetData.size(),
        "Mismatch between required config elements and supplied - expected "
            + app.getConfigElementCount()
            + " but  was "
            + appConfigSetData.size());

    for (Entry<String, String> entry : appConfigSetData.entrySet()) {
      AppConfigElementDescriptor descriptor = app.getDescriptorByName(entry.getKey());
      Validate.notNull(descriptor, "unknown property " + entry.getKey());
      descriptor.validate(entry.getValue());
      AppConfigElement newElem = new AppConfigElement(descriptor, entry.getValue());
      rc.addConfigElement(newElem);
    }

    return rc;
  }

  @Override
  public AppConfigElementSet deleteAppConfigSet(Long appConfigElementSetId, User subject) {
    AppConfigElementSet set = appCfgDao.getAppConfigElementSetById(appConfigElementSetId);
    UserAppConfig cfg = set.getUserAppConfig();
    permUtils.assertIsPermitted(cfg, PermissionType.DELETE, subject, " Update AppConfig ");
    cfg.removeConfigSet(set);
    appCfgDao.save(cfg);
    return set;
  }

  @Override
  public Optional<AppConfigElementSet> findByAppConfigElementSetId(Long appConfigElementSetId) {
    return appCfgDao.findByAppConfigElementSetId(appConfigElementSetId);
  }

  @Override
  public List<User> findByAppConfigValue(String propertyName, String value) {
    return appCfgDao.findUsersByPropertyValue(propertyName, value);
  }
}

package com.researchspace.service.impl;

import com.researchspace.dao.RSMetaDataDao;
import com.researchspace.model.RSMetaData;
import com.researchspace.model.Version;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.service.GlobalInitManager;
import com.researchspace.service.IApplicationInitialisor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Performs global initialisation actions on application startup. Should only be called once after
 * application startup and hence keeps state about whether initialisation has been run or not.
 *
 * <p>This class is the main entry point to Spring/Hibernate based functionality at startup.
 *
 * <p>Classes wishing to run at startup should implement {@link IApplicationInitialisor} and
 * register themselves in profile configuration beans in com.axiope.service.cfg.
 */
public class GlobalInitManagerImpl implements GlobalInitManager {

  Logger logger = LoggerFactory.getLogger(getClass());

  private List<IApplicationInitialisor> applicationInitialisors =
      new ArrayList<IApplicationInitialisor>();

  public void setApplicationInitialisors(List<IApplicationInitialisor> applicationInitialisors) {
    this.applicationInitialisors = applicationInitialisors;
  }

  RSMetaDataDao metadataDao;

  // db version from rs.properties
  @Value("${dbversion}")
  String versionFromRsProperties;

  @Autowired
  public void setMetadataDao(RSMetaDataDao metadataDao) {
    this.metadataDao = metadataDao;
  }

  private boolean contextRefreshEventHandled = false;

  private Realm shiroRealm;
  private Realm sysadminRealm;

  @Autowired
  @Qualifier("myRealm")
  public void setShiroRealm(Realm shiroRealm) {
    this.shiroRealm = shiroRealm;
  }

  @Autowired
  @Qualifier("globalInitSysadminRealm")
  public void setSysadminRealm(Realm sysadminRealm) {
    this.sysadminRealm = sysadminRealm;
  }

  /*
   * For subclassing in unit testing
   */
  protected Subject createSubject() {
    // see shiro docs at http://shiro.apache.org/subject.html
    DefaultSecurityManager sn = new DefaultSecurityManager();
    Collection<Realm> realms = new ArrayList<Realm>(2);
    realms.add(shiroRealm);
    realms.add(sysadminRealm);
    sn.setRealms(realms);
    SecurityUtils.setSecurityManager(sn);
    // creates an on-the-fly subject and session
    final Subject subject = new Subject.Builder(sn).buildSubject();
    return subject;
  }

  @Override
  public void onApplicationEvent(final ApplicationEvent appEvent) {
    if (!(appEvent instanceof ContextRefreshedEvent)) {
      return;
    }
    if (contextRefreshEventHandled) {
      return; // only need to handle event once
    }
    String[] profiles =
        ((ContextRefreshedEvent) appEvent)
            .getApplicationContext()
            .getEnvironment()
            .getActiveProfiles();
    logger.info("Running under profile: {}", StringUtils.join(profiles));

    createSubject();
    RSMetaData meta = null;
    List<RSMetaData> metas = metadataDao.getAll();
    if (metas.isEmpty()) {
      meta = doCoreStartUpOnInitialDeployment(null);
    } else {
      meta = metas.get(0);
      if (!meta.isInitialized()) meta = doCoreStartUpOnInitialDeployment(meta);
    }
    try {
      globalInit((ApplicationContext) appEvent.getSource());
    } catch (IllegalAddChildOperation e) {
      logger.error("Error while initialising.", e);
    }

    // create test groups if asked

    meta.setInitialized(true); // set at end so only set if all OK
    metadataDao.save(meta);
    contextRefreshEventHandled = true;
  }

  private RSMetaData doCoreStartUpOnInitialDeployment(RSMetaData meta) {
    if (meta == null) {
      meta = initializeRSMetaData();
      logger.info("First time launch, performing core start up routine");
    }
    for (IApplicationInitialisor init : getApplicationInitialisors()) {
      init.onInitialAppDeployment();
    }

    meta.setInitialized(true);
    return meta;
  }

  private RSMetaData initializeRSMetaData() {
    RSMetaData meta;
    meta = new RSMetaData();
    meta.setInitialized(false);
    if (versionFromRsProperties != null) {
      try {
        Long version = Long.parseLong(versionFromRsProperties);
        meta.setDBVersion(new Version(version));
      } catch (NumberFormatException e) {
        logger.error(
            "Could not parse application version - should be an integer but was [{}]",
            versionFromRsProperties);
      }
    }
    return meta;
  }

  @Override
  public void globalInit(ApplicationContext applicationContext) throws IllegalAddChildOperation {
    for (IApplicationInitialisor init : getApplicationInitialisors()) {
      init.onAppStartup(applicationContext);
    }
  }

  @Override
  public List<IApplicationInitialisor> getApplicationInitialisors() {
    return applicationInitialisors;
  }
}

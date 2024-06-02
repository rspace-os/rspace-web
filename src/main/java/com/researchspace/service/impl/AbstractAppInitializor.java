package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.IGroupCreationStrategy;
import com.researchspace.service.UserManager;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Abstract base class for implementations of IApplicationInitialisor. <br>
 * This class provides null implementations of the methods provide by the interface. Implementations
 * therefore need not implement all the methods.
 */
public class AbstractAppInitializor implements IApplicationInitialisor {

  public static final Logger log = LogManager.getLogger(AbstractAppInitializor.class);

  public static final String SANITY_CHECK_FAILURE_PREFIX = "RSpace initialization failure: ";

  public static final String ADMIN_UNAME = "admin";

  public static final String ADMIN_PWD = "admin23!";

  public static final String SYSADMIN_UNAME = "sysadmin1";

  public static final String SYSADMIN_PWD = "sysWisc23!";

  protected void fatalStartUpLog(String msg) {
    log.error(SANITY_CHECK_FAILURE_PREFIX + msg);
  }

  /** For common usage in subclasses. */
  @Autowired IGroupCreationStrategy grpStrategy;

  @Autowired UserManager userMgr;

  void login(UsernamePasswordToken token) {
    SecurityUtils.getSubject().login(token);
  }

  void logout() {
    SecurityUtils.getSubject().logout();
  }

  /** Inner class that provides common functionality to initiate a user account. */
  public static class UserAction implements Callable<Boolean> {
    private Function<User, Object> userAction;
    private User user;
    private Logger logger;

    /**
     * Does something with a user account of given ID
     *
     * @param userAction A Function to apply to the users' account
     * @param user The user
     * @param logger
     */
    UserAction(Function<User, Object> userAction, User user, Logger logger) {
      super();
      this.userAction = userAction;
      this.user = user;
      this.logger = logger;
    }

    @Override
    public Boolean call() throws Exception {
      try {
        userAction.apply(user);
      } catch (Throwable throwable) {
        logger.error(throwable.getMessage());
        return false;
      }
      return true;
    }
  }

  public static class UserIdAction implements Callable<Boolean> {
    private Function<Long, Object> userAction;
    private User user;
    private Logger logger;

    /**
     * Does something with a user account of given ID
     *
     * @param userAction A Function to apply to the users' account
     * @param user The user
     * @param logger
     */
    UserIdAction(Function<Long, Object> userAction, User user, Logger logger) {
      super();
      this.userAction = userAction;
      this.user = user;
      this.logger = logger;
    }

    @Override
    public Boolean call() throws Exception {
      try {
        userAction.apply(user.getId());
      } catch (Throwable throwable) {
        logger.error(throwable.getMessage());
        return false;
      }
      return true;
    }
  }

  boolean performAuthenticatedAction(final Subject subject, Callable<Boolean> callback) {
    try {
      Boolean rc = subject.execute(callback);
      return rc;
    } finally {
      subject.logout();
    }
  }

  @Override
  public void onInitialAppDeployment() {}

  @Override
  public void onAppVersionUpdate() {}

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {}

  protected StringBuffer getEnvironmentVarsAsString() {
    Map<String, String> envVars = System.getenv();
    StringBuffer sb = new StringBuffer("\n");

    for (Iterator<Entry<String, String>> it = envVars.entrySet().iterator(); it.hasNext(); ) {
      Entry<String, String> entry = it.next();
      String key = entry.getKey();
      String value = entry.getValue();
      sb.append(key + ":" + value + "\n");
    }
    return sb;
  }

  protected StringBuffer getSystemPropertiesAsString() {
    Properties props = System.getProperties();
    StringBuffer sb = new StringBuffer("\n");
    for (Object name : props.keySet()) {
      if (name instanceof String) {
        sb.append(name + ":" + props.getProperty((String) name) + "\n");
      }
    }
    return sb;
  }
}

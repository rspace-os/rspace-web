package com.researchspace.auth;

import static com.researchspace.session.SessionAttributeUtils.ANALYTICS_USER_ID;
import static com.researchspace.session.SessionAttributeUtils.FIRST_LOGIN;
import static com.researchspace.session.SessionAttributeUtils.FIRST_LOGIN_HANDLED;
import static com.researchspace.session.SessionAttributeUtils.RSPACE_VERSION;
import static com.researchspace.session.SessionAttributeUtils.USER_INFO;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.User;
import com.researchspace.model.events.AccountEventType;
import com.researchspace.model.events.UserAccountEvent;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.IMessageAndNotificationTracker;
import com.researchspace.service.UserManager;
import java.util.Calendar;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseLoginHelperImpl implements LoginHelper {
  Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private @Autowired AnalyticsManager analyticsMgr;
  private @Autowired IMessageAndNotificationTracker commTracker;
  private @Autowired CommunicationManager commMgr;
  private @Autowired UserManager userMgr;
  private @Autowired TimezoneAdjuster timezoneUtils;
  private @Autowired PropertyHolder properties;

  static final String USER_IN_GROUP_ATTR = "inGroup";
  static final String AUTOSHARE_ENABLED_ATTR = "autoshareEnabled";
  public static final String RECENT_SIGNUP_ATTR = "recentSignup";

  public HttpSession login(User toLogin, String originalPwd, HttpServletRequest request) {
    if (toLogin.isLoginDisabled()) {
      String msg =
          String.format(
              "Login for User %s is blocked. Locked? %s. Enabled? %s ",
              toLogin.getUsername(), toLogin.isAccountLocked(), toLogin.isEnabled());
      SECURITY_LOG.warn(msg);
      throw new IllegalStateException(msg);
    }
    doLogin(toLogin, originalPwd);
    return postLogin(toLogin, request);
  }

  /**
   * Subclasses should implement for type of login
   *
   * @param toLogin
   * @param originalPwd
   */
  abstract void doLogin(User toLogin, String originalPwd);

  public HttpSession postLogin(User loggedIn, HttpServletRequest request) {
    HttpSession sssn = request.getSession();

    sssn.setAttribute(USER_INFO, loggedIn.toPublicInfo());
    sssn.setAttribute("userName", loggedIn.getUsername());
    sssn.setAttribute(ANALYTICS_USER_ID, analyticsMgr.getAnalyticsUserId(loggedIn));

    sssn.setAttribute(RSPACE_VERSION, properties.getVersionMessage());
    sssn.setAttribute(AUTOSHARE_ENABLED_ATTR, loggedIn.hasAutoshareGroups());
    sssn.setAttribute(USER_IN_GROUP_ATTR, loggedIn.getGroups().size() > 0);

    boolean firstLogin = loggedIn.getLastLogin() == null;
    sssn.setAttribute(FIRST_LOGIN, firstLogin);

    // RSPAC-1417 user signing up again without logging out from previous session
    if (firstLogin) {
      sssn.removeAttribute(FIRST_LOGIN_HANDLED);
      userMgr.saveUserAccountEvent(new UserAccountEvent(loggedIn, AccountEventType.FIRST_LOGIN));
    }

    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DATE, -7);
    boolean recentSignup = loggedIn.getCreationDate().after(cal.getTime());
    sssn.setAttribute(RECENT_SIGNUP_ATTR, recentSignup);

    timezoneUtils.setUserTimezoneInSession(request, sssn);
    updateUserLoginHistory(loggedIn);
    SECURITY_LOG.info(
        "Successful login by [{}] from {}",
        loggedIn.getUsername(),
        RequestUtil.remoteAddr(request));

    analyticsMgr.userLoggedIn(loggedIn, request);

    // Initialize number of notifications, messages and special messages for user
    commTracker.initCount(loggedIn.getId(), commMgr.getNotificationStatus(loggedIn));

    return sssn;
  }

  private void updateUserLoginHistory(User subject) {
    Date loginAttempt = new Date();
    subject.setLastLogin(loginAttempt);
    subject.setLoginFailure(null);
    subject.setNumConsecutiveLoginFailures((byte) 0);
    userMgr.save(subject);
  }
}

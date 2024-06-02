package com.researchspace.webapp.controller;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.User;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.webapp.filter.SSOShiroFormAuthFilterExt;
import java.io.IOException;
import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/** Controller handling logout actions of the user. */
@Controller
@RequestMapping("/logout")
public class LogoutController extends BaseController {

  /** redirect to workspace */
  // public static final String WORKSPACE_REDIRECT = "redirect:/workspace";

  @Autowired private AnalyticsManager analyticsManager;

  /** Ends user session and redirects. */
  @GetMapping
  public ModelAndView logout(Principal principal, HttpServletRequest req) throws IOException {

    String remoteUserUsername =
        (String) req.getSession().getAttribute(SSOShiroFormAuthFilterExt.REMOTE_USER_USERNAME_ATTR);
    boolean isRemoteUser = StringUtils.isNotBlank(remoteUserUsername);

    releaseRunAsUserIfAny(principal, req.getSession());

    User user = (User) req.getSession().getAttribute(SessionAttributeUtils.USER);
    if (user != null) {
      analyticsManager.userLoggedOut(user, null);
    }

    SecurityUtils.getSubject().logout();
    SECURITY_LOG.info("[{}] logged out", principal.getName());

    // Needed for clearing RS Inventory session storage on log out
    ModelAndView mav = new ModelAndView();
    mav.addObject("redirectLocation", (isRemoteUser) ? properties.getSSOLogoutUrl() : "/workspace");

    return mav;
  }

  /**
   * Releases 'runAs' functionality. Is not in system/ URL since the user will not be running as the
   * system user when this method is called, so needs to be accessible to any authenticated user.
   *
   * @param principal
   * @return the view name to redirect to
   */
  @GetMapping("/runAsRelease")
  public String runAsRelease(Principal principal, HttpSession session) {
    releaseRunAsUserIfAny(principal, session);
    return "redirect:/workspace";
  }

  private void releaseRunAsUserIfAny(Principal principal, HttpSession session) {
    Object isSessionRunAs = session.getAttribute(SessionAttributeUtils.IS_RUN_AS);
    if (Boolean.TRUE.equals(isSessionRunAs)) {
      PrincipalCollection prev = SecurityUtils.getSubject().getPreviousPrincipals();
      String adminUname = (String) prev.getPrimaryPrincipal();
      SecurityUtils.getSubject().releaseRunAs();
      session.removeAttribute(SessionAttributeUtils.IS_RUN_AS);
      User previousAdminUser = userManager.getUserByUsername(adminUname, true);
      getCurrentActiveUsers().removeUser(principal.getName(), session);
      updateSessionUser(previousAdminUser, session);
      SECURITY_LOG.info(
          "[{}] no longer running as [{}]", prev.getPrimaryPrincipal(), principal.getName());
    }
  }
}

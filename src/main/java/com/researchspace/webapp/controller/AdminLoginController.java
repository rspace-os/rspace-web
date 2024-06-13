package com.researchspace.webapp.controller;

import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import com.researchspace.webapp.filter.SSOShiroFormAuthFilterExt;
import java.io.IOException;
import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Controller handling Admin Login page and actions. */
@Controller
@RequestMapping("/adminLogin")
public class AdminLoginController extends BaseController {

  @Autowired private LogoutController logoutController;

  @Autowired private RemoteUserRetrievalPolicy remoteUserPolicy;

  /** Ends current user session (if there is one) and shows adminLogin page. */
  @GetMapping
  public String getAdminLoginPage(Principal principal, Model model, HttpServletRequest request)
      throws IOException {
    if (!(properties.isSSO() && properties.getSSOAdminLoginEnabled())) {
      return "/sso/adminLoginUnavailable";
    }

    String remoteUser = remoteUserPolicy.getRemoteUser(request);
    // if remoteUser is unpopulated on request level, let's retrieve from session
    if (StringUtils.isBlank(remoteUser)) {
      remoteUser =
          (String)
              request
                  .getSession()
                  .getAttribute(SSOShiroFormAuthFilterExt.REMOTE_USER_USERNAME_ATTR);
    }
    model.addAttribute(SSOShiroFormAuthFilterExt.REMOTE_USER_USERNAME_ATTR, remoteUser);

    // log out current user, if there is one
    if (principal != null) {
      logoutController.logout(principal, request);
    }

    return "/sso/adminLogin";
  }

  @GetMapping("/backToSsoUserWorkspace")
  public String navigateBackToSsoUserWorkspace(Principal principal, HttpServletRequest request)
      throws IOException {

    // log out current user, if there is one
    if (principal != null) {
      logoutController.logout(principal, request);
    }

    // now redirect to login page, that should re-log the user based on their SSO identity
    return "redirect:/login";
  }

  /**
   * Method for direct logout from SSO. The user may not be logged into RSpace at this point, so the
   * call to /logout would not always work.
   *
   * @param principal
   * @param request
   * @return
   * @throws IOException
   */
  @GetMapping("/logoutFromSso")
  public String logoutFromSso(Principal principal, HttpServletRequest request) throws IOException {

    // log out RSpace user first, if there is one
    if (principal != null) {
      logoutController.logout(principal, request);
    }
    return "redirect:" + properties.getSSOLogoutUrl();
  }
}

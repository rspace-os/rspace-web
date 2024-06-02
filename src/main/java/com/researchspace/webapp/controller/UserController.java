package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.service.EmailBroadcast;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.velocity.VelocityEngineUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/users*")
public class UserController extends BaseController {

  @Value("${server.urls.prefix}")
  private String domain;

  private EmailBroadcast emailer;

  @Autowired
  @Qualifier("emailBroadcast")
  public void setEmailer(EmailBroadcast emailer) {
    this.emailer = emailer;
  }

  @Autowired private VelocityEngine velocity;

  @GetMapping("authorise/{userid}")
  public String authoriseSignUpRequest(@PathVariable(value = "userid") Long userId) {
    User principal = userManager.getAuthenticatedUserInSession();
    if (!principal.hasAdminRole()) {
      throw new AuthorizationException(
          "Unauthorized attempt to authorise user account [" + userId + "]");
    }
    User user = userManager.get(userId);
    user.setAccountLocked(false);
    userManager.save(user);

    Map<String, Object> velocityModel = new HashMap<String, Object>();

    String link = domain + "/workspace";
    velocityModel.put("link", link);
    velocityModel.put("fullName", user.getFullName());
    String message =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "genericAccountActivation.vm", "UTF-8", velocityModel);
    emailer.sendTextEmail(
        "RSpace account activated", message, Arrays.asList(new String[] {user.getEmail()}), null);
    return "accountActivated";
  }

  @GetMapping("deny/{userid}")
  public String denySignUpRequest(@PathVariable(value = "userid") Long userId) {
    User principal = userManager.getAuthenticatedUserInSession();
    if (!principal.hasAdminRole()) {
      throw new AuthorizationException(
          "Unauthorized attempt to authorise user account [" + userId + "]");
    }
    User user = userManager.get(userId);
    user.setAccountLocked(true);
    userManager.save(user);

    Map<String, Object> rc = new HashMap<String, Object>();

    rc.put("fullName", user.getFullName());
    String message =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "genericAccountDenial.vm", "UTF-8", rc);
    emailer.sendTextEmail(
        "RSpace account denied", message, Arrays.asList(new String[] {user.getEmail()}), null);
    return "accountDenied";
  }
}

package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
import com.researchspace.service.impl.EmailContentGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
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

  @Autowired private EmailContentGenerator contentGenerator;

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

    Map<String, Object> velocityModel = new HashMap<>();

    String link = domain + "/workspace";
    velocityModel.put("link", link);
    velocityModel.put("fullName", user.getFullName());
    EmailContent content =
        contentGenerator.render(
            "email.account.activated.subject", "genericAccountActivation.vm", velocityModel);
    emailer.sendEmail(content, List.of(user.getEmail()), null);
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

    Map<String, Object> rc = new HashMap<>();

    rc.put("fullName", user.getFullName());
    EmailContent content =
        contentGenerator.render("email.account.denied.subject", "genericAccountDenial.vm", rc);
    emailer.sendEmail(content, List.of(user.getEmail()), null);
    return "accountDenied";
  }
}

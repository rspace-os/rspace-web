package com.axiope.userimport;

import com.researchspace.model.User;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.UserManager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.ui.velocity.VelocityEngineUtils;

/**
 * This class sends an email to ResearchSpace staff to authorise the signup request.<br>
 * The user account is created, but is disabled until the administrator authorises the request.
 *
 * <p>Much of this class can be reused generically in future for other installations
 */
@Component("authorisedPostSignup")
public class AuthorisedPostSignUp implements IPostUserSignup {

  private static Logger log = LoggerFactory.getLogger(AuthorisedPostSignUp.class);

  /**
   * These values are from deployment.properties files. Which one is used is specified at build time
   * by a Maven build property, -Ddeployment
   */
  @Value("${rs.postsignon.emailtoadmin.template}")
  private String emailTemplateResource;

  @Value("${server.urls.prefix}")
  private String domain;

  @Value("${email.signup.title.from}")
  private String installationName;

  @Value("${email.signup.authoriser.emails}")
  private String authoriserEmails;

  private EmailBroadcast emailSender;

  @Autowired private UserManager userMgr;
  @Autowired private VelocityEngine velocity;

  @Autowired
  public void setBroadcaster(EmailBroadcast broadcaster) {
    this.emailSender = broadcaster;
  }

  @Override
  public void postUserCreate(User created, HttpServletRequest req, String origPwd) {
    created.setAccountLocked(true);
    userMgr.save(created);
    notifyUser(created);
  }

  private void notifyUser(User created) {

    Map<String, Object> rc = new HashMap<String, Object>();

    rc.put("installation", installationName);
    rc.put("fullName", created.getDisplayName());
    rc.put("email", created.getEmail());

    String acceptLink = domain + "/admin/users/authorise/" + created.getId();
    String denyLink = domain + "/admin/users/deny/" + created.getId();
    rc.put("acceptlink", acceptLink);
    rc.put("denylink", denyLink);
    List<String> recipients = getRecipients();

    try {
      String message =
          VelocityEngineUtils.mergeTemplateIntoString(velocity, emailTemplateResource, "UTF-8", rc);
      emailSender.sendTextEmail(
          "Sign up request from " + installationName, message, recipients, null);
      log.info("Emailed signup request to admin");

    } catch (MailException me) {
      log.error(me.getMostSpecificCause().getMessage());
    }
  }

  private List<String> getRecipients() {
    String[] emails = StringUtils.split(authoriserEmails, ",");
    return Arrays.asList(emails);
  }

  @Override
  public String getRedirect(User user) {
    return "redirect:/public/signupConfirmation";
  }
}

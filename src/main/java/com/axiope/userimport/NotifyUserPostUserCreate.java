package com.axiope.userimport;

import com.researchspace.model.User;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import com.researchspace.service.impl.StrictEmailContentGenerator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Notifies user by email that an account was created for them. */
@Component("notifyUserPostUserCreate")
@Profile(value = "prod")
public class NotifyUserPostUserCreate implements IPostUserCreationSetUp {

  private static Logger log = LoggerFactory.getLogger(NotifyUserPostUserCreate.class);

  @Value("${rs.postbatchsignup.emailtemplate}")
  private String emailTemplateResource;

  @Value("${server.urls.prefix}")
  private String htmlDomainPrefix;

  @Autowired
  @Qualifier("emailBroadcast")
  private EmailBroadcast emailSender;

  private @Autowired StrictEmailContentGenerator strictEmailContentGenerator;

  @Override
  public void postUserCreate(User created, HttpServletRequest req, String origPassword) {
    notifyUser(created, origPassword);
  }

  private void notifyUser(User newUser, String origPwd) {
    Map<String, Object> rc = new HashMap<>();

    rc.put("userPassword", origPwd);
    rc.put("userUsername", newUser.getUsername());
    rc.put("htmlDomainPrefix", htmlDomainPrefix);
    rc.put("userFirstName", newUser.getFirstName());
    if (newUser.getGroups().size() > 0) {
      rc.put("groupName", newUser.getGroups().iterator().next().getDisplayName());
    }
    EmailContent message =
        strictEmailContentGenerator.generatePlainTextAndHtmlContent(emailTemplateResource, rc);
    log.info("Sending mail to {} at {}", newUser.getUsername(), newUser.getEmail());
    emailSender.sendHtmlEmail(
        "Welcome to RSpace", message, Arrays.asList(new String[] {newUser.getEmail()}), null);
  }
}

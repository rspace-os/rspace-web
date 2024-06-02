package com.researchspace.service.cloud.impl;

import com.axiope.userimport.IPostUserSignup;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import com.researchspace.service.impl.StrictEmailContentGenerator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * This is called by Community signup following creation of a temporary user. <br>
 * It emails a verification link to the user's submitted email. Will throw {@link
 * IllegalStateException} if public methods in this class are called when it is not a Community
 * instance
 */
@Component("communityPostSignup")
@Slf4j
public class CommunityPostSignupVerification implements IPostUserSignup {

  public static final String CLOUD_SIGNUP_ACCOUNT_ACTIVATION_COMPLETE =
      "cloud/signup/accountActivationComplete";

  private static final String SIGNUP_VERIFICATION_TEMPLATE = "signupVerificationMsg.vm";

  private @Autowired UserManager userMgr;
  private @Autowired IPropertyHolder properties;
  private @Autowired StrictEmailContentGenerator strictEmailContentGenerator;

  @Autowired
  @Qualifier("emailBroadcast")
  private EmailBroadcast emailSender;

  @Override
  public void postUserCreate(User created, HttpServletRequest req, String origPwd) {
    if (!properties.isCloud()) {
      throw new IllegalStateException("This configuration is invalid for a non-cloud deployment!");
    }
    if (StringUtils.isEmpty(created.getToken())) {
      // we're a genuine new user
      TokenBasedVerification upc =
          userMgr.createTokenBasedVerificationRequest(
              created,
              created.getEmail(),
              RequestUtil.remoteAddr(req),
              TokenBasedVerificationType.VERIFIED_SIGNUP);
      notifyUser(created, origPwd, req, upc);
    }
  }

  private void notifyUser(
      User newUser, String origPwd, HttpServletRequest req, TokenBasedVerification upc) {

    Map<String, Object> model = new HashMap<>();
    model.put("userUsername", newUser.getUsername());
    model.put("htmlDomainPrefix", properties.getUrlPrefix());
    model.put("firstName", newUser.getFirstName());
    model.put("ipAddress", RequestUtil.remoteAddr(req));
    model.put("verifyLink", createVerifyLink(upc.getToken()));

    EmailContent content =
        strictEmailContentGenerator.generatePlainTextAndHtmlContent(
            SIGNUP_VERIFICATION_TEMPLATE, model);
    log.info("Sending mail to " + newUser.getUsername() + " at " + newUser.getEmail());
    emailSender.sendHtmlEmail(
        "Welcome to RSpace", content, Arrays.asList(new String[] {newUser.getEmail()}), null);
  }

  private String createVerifyLink(String token) {
    return properties.getUrlPrefix() + "/cloud/verifysignup?token=" + token;
  }

  @Override
  public String getRedirect(User user) {
    if (StringUtils.isEmpty(user.getToken())) {
      return "redirect:/cloud/signup/cloudSignupConfirmation?email=" + user.getEmail();
    }
    // this is an invited user and the token has been verified.
    return CLOUD_SIGNUP_ACCOUNT_ACTIVATION_COMPLETE;
  }
}

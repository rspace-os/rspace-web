package com.axiope.userimport;

import com.researchspace.auth.LoginHelper;
import com.researchspace.auth.password.LoginVerificationBusyException;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserRoleHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Performs default operations post sign up; activating user, logging in and redirecting to
 * workspace page. This is the default handler for standalone instances.
 */
@Component("defaultPostSignUp")
@Slf4j
public class DefaultPostUserCreate implements IPostUserSignup {

  private @Autowired AuditTrailService auditService;

  @Autowired
  @Qualifier("manualLoginHelper")
  private LoginHelper loginHelper;

  private @Autowired UserRoleHandler roleHandler;
  private @Autowired IPropertyHolder properties;

  /**
   * {@inheritDoc}
   *
   * <p>A {@link LoginVerificationBusyException} from the login is held until the PI promotion and
   * the audit event have run, then rethrown: the account already exists, so the rest of signup must
   * complete even when the verifier has no free slot. Other exceptions propagate at once.
   */
  @Override
  public void postUserCreate(User created, HttpServletRequest req, String origPwd) {
    LoginVerificationBusyException loginRefused = null;
    try {
      loginHelper.login(created, origPwd, req);
    } catch (LoginVerificationBusyException e) {
      loginRefused = e;
    }
    // Send user an e-mail
    if (properties.isPicreateGroupOnSignupEnabled() && created.isPicreateGroupOnSignup()) {
      log.info("User {} has chosen to be a PI", created.getUsername());
      created.setPicreateGroupOnSignup(true);
      created = roleHandler.setNewlySignedUpUserAsPi(created);
    }

    log.debug("Sending user '{}' an account information e-mail", created.getUsername());
    auditService.notify(new GenericEvent(created, created, AuditAction.CREATE));
    if (loginRefused != null) {
      throw loginRefused;
    }
  }

  @Override
  public String getRedirect(User user) {
    return "redirect:workspace";
  }
}

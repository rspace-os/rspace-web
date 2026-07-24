package com.researchspace.service.impl;

import com.researchspace.licensews.LicenseExceededException;
import com.researchspace.licensews.LicenseServerUnavailableException;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.events.AccountEventType;
import com.researchspace.model.events.UserAccountEvent;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
import com.researchspace.service.LicenseRequestResult;
import com.researchspace.service.LicenseService;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.service.UserManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class UserEnablementUtilsImpl implements UserEnablementUtils {
  @Autowired private EmailContentGenerator emailContentGenerator;

  @Autowired private UserManager userManager;

  @Autowired private IPropertyHolder properties;

  @Autowired
  @Qualifier("emailBroadcast")
  private EmailBroadcast emailer;

  @Autowired @Setter private LicenseService licenseService;

  @Autowired protected MessageSourceUtils messageSourceUtils;

  @Override
  public void notifyByEmailUserEnablementChange(User user, User systemUser, boolean newStatus) {
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("user", user);
    velocityModel.put("accountDisabled", !newStatus);
    velocityModel.put("systemUser", systemUser);
    velocityModel.put("baseURL", properties.getServerUrl());
    String subjectKey =
        newStatus
            ? "email.account.accountEnablementNotification.subjectEnabled"
            : "email.account.accountEnablementNotification.subjectDisabled";
    EmailContent content =
        emailContentGenerator.render(subjectKey, "accountEnablementNotification.vm", velocityModel);
    emailer.sendEmail(content, List.of(user.getEmail()), null);
  }

  @Override
  public void auditUserEnablementChangeEvent(boolean status, User user) {
    UserAccountEvent accountEvent =
        new UserAccountEvent(user, status ? AccountEventType.ENABLED : AccountEventType.DISABLED);
    userManager.saveUserAccountEvent(accountEvent);
  }

  @Override
  public void checkLicenseForUserInRole(int numSeatsRequested, Role role) {
    LicenseRequestResult result = licenseService.requestUserLicenses(numSeatsRequested, role);
    if (result.isLicenseServerAvailable() && !result.isRequestOK()) {
      String customMessage = properties.getLicenseExceededCustomMessage();

      throw new LicenseExceededException(
          getMessage(
              "license.insufficientSeats.details",
              new Object[] {result.getAvailableSeats(), numSeatsRequested, customMessage}));
    } else if (!result.isLicenseServerAvailable()) {
      throw new LicenseServerUnavailableException();
    }
  }

  /**
   * Convenience method for getting a i18n key's value with arguments and default locale.
   *
   * @param msgKey
   * @param args
   * @return
   */
  private String getMessage(String msgKey, Object[] args) {
    return messageSourceUtils.getMessage(msgKey, args);
  }
}

package com.researchspace.service.impl;

import static com.researchspace.core.util.TransformerUtils.toList;

import com.researchspace.licensews.LicenseExceededException;
import com.researchspace.licensews.LicenseServerUnavailableException;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.events.AccountEventType;
import com.researchspace.model.events.UserAccountEvent;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.LicenseRequestResult;
import com.researchspace.service.LicenseService;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import java.util.HashMap;
import java.util.Map;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class UserEnablementUtilsImpl implements UserEnablementUtils {
  @Autowired private StrictEmailContentGenerator strictEmailContentGenerator;

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
    velocityModel.put("htmlPrefix", properties.getServerUrl());
    EmailContent content =
        strictEmailContentGenerator.generatePlainTextAndHtmlContent(
            "accountEnablementNotification.vm", velocityModel);
    String title = newStatus ? "RSpace account enabled" : "RSpace account disabled";
    emailer.sendHtmlEmail(title, content, toList(user.getEmail()), null);
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

      if (numSeatsRequested == 1) {
        throw new LicenseExceededException(
            getMessage("license.insufficientSeatsSingle.msg", new Object[] {customMessage}));
      } else {
        throw new LicenseExceededException(
            getMessage(
                "license.insufficientSeatsMultiple.msg",
                new Object[] {result.getAvailableSeats(), numSeatsRequested, customMessage}));
      }
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

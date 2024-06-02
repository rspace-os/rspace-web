package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;

public class SysadminCreateUserFormConfigurerImpl implements SysadminCreateUserFormConfigurer {

  @Value("${deployment.sso.backdoorUserCreation.enabled:false}")
  private boolean ssoBackdoorUserCreationEnabled;

  private IPropertyHolder properties;
  private @Autowired MessageSource messageSource;

  public SysadminCreateUserFormConfigurerImpl(@Autowired IPropertyHolder propertyHolder) {
    this.properties = propertyHolder;
  }

  /**
   * Package scoped for testing. We require strict username only if standalone=true and we are not
   * using LDAP. e.g.
   *
   * <table>
   *  <tr> <th>Condition</th><th>1</th><th>2</th><th>3</th><th>4</th> </tr>
   *     <tr> <td>Standalone</td><td>T</td><td>T</td><td>F</td><td>F</td> </tr>
   *     <tr> <td>ldap.authentication</td><td>T</td><td>F</td><td>T</td><td>F</td> </tr>
   *     <tr> <th>Action</th><th>1</th><th>2</th><th>3</th><th>4</th> </tr>
   *     <tr> <td>Strict username required</td><td>F</td><td>T</td><td>F</td><td>F</td> </tr>
   * </table>
   *
   * @return
   */
  boolean isStrictUsername() {
    return properties.isStandalone() && !properties.isLdapAuthenticationEnabled();
  }

  @Override
  public String getUsernamePattern() {
    if (isStrictUsername()) {
      return properties.getMinUsernameLength() > 0
          ? StringUtils.replace(
              User.ALLOWED_USERNAME_CHARS_REGEX,
              User.MIN_UNAME_LENGTH + ",",
              properties.getMinUsernameLength() + ",")
          : User.ALLOWED_USERNAME_CHARS_REGEX;
    } else {
      return User.ALLOWED_USERNAME_CHARS_RELAXED_LENGTH_REGEX;
    }
  }

  @Override
  public String getUsernamePatternTitle() {
    if (isStrictUsername()) {
      return messageSource.getMessage(
          "system.createAccountForm.userName.strictTitle", new Object[] {}, Locale.getDefault());
    } else {
      return messageSource.getMessage(
          "system.createAccountForm.userName.relaxedTitle", new Object[] {}, Locale.getDefault());
    }
  }

  @Override
  public boolean isAffiliationRequired() {
    return properties.isCloud();
  }

  @Override
  public boolean isDisplayLdapLookupRequired() {
    return Boolean.parseBoolean(properties.getLdapEnabled()) && properties.isSSO();
  }

  @Override
  public Boolean isBackdoorSysadminCreationEnabled() {
    return ssoBackdoorUserCreationEnabled; // RSPAC-2189
  }

  /*
   * ==================
   *   for testing
   * ==================
   */
  void setInternalUserCreationEnabled(boolean enabled) {
    this.ssoBackdoorUserCreationEnabled = enabled;
  }
}

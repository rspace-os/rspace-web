package com.researchspace.webapp.controller;

/**
 * Abstraction over deployment property setup to configure how sysadmin's create user form should be
 * displayed
 */
public interface SysadminCreateUserFormConfigurer {

  /**
   * Regexp string for acceptable username characters
   *
   * @return
   */
  String getUsernamePattern();

  /**
   * Placeholder text in username input field
   *
   * @return
   */
  String getUsernamePatternTitle();

  /**
   * Whether or not affiliation input should be shown
   *
   * @return
   */
  boolean isAffiliationRequired();

  /**
   * Whether LDAP lookup is enabled to get user details
   *
   * @return
   */
  boolean isDisplayLdapLookupRequired();

  /**
   * Whether sysadmin should be able to create users with signupSource = 'SSO_BACKDOOR'. This can be
   * used to create non-sso users in sso environment.
   */
  Boolean isBackdoorSysadminCreationEnabled();
}

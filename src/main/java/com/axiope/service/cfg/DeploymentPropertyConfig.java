package com.axiope.service.cfg;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * This class makes some properties available to other @Configuration classes during application
 * startup to configure classes and services.
 *
 * <p>This class is only intended to support Spring bean configuration at startup. <br>
 * Code that implements features should use IPropertyHolder to access properties or inject them
 * directly.
 */
@Configuration
public class DeploymentPropertyConfig {

  // from relevant deployment.properties file
  @Value("${deployment.standalone}")
  private String standalone = "true"; // default

  @Value("${deployment.cloud}")
  private String cloud;

  @Value("${ldap.authentication.enabled}")
  private String ldapAuthenticationEnabled;

  @Value("${deployment.sso.adminLogin.enabled:false}")
  private boolean ssoAdminLoginEnabled;

  @Value("${msoffice.wopi.enabled:false}")
  private boolean msOfficeEnabled;

  @Value("${collabora.wopi.enabled:false}")
  private boolean collaboraEnabled;

  boolean isStandalone() {
    return Boolean.parseBoolean(standalone);
  }

  boolean isCloud() {
    return Boolean.parseBoolean(cloud);
  }

  boolean isLdapAuthenticationEnabled() {
    return Boolean.parseBoolean(ldapAuthenticationEnabled);
  }

  boolean isSsoAdminLoginEnabled() {
    return ssoAdminLoginEnabled;
  }

  boolean isCollaboraEnabled() {
    return collaboraEnabled;
  }

  boolean isMsOfficeEnabled() {
    return msOfficeEnabled;
  }
}

package com.researchspace.webapp.filter;

import org.apache.shiro.web.filter.authz.SslFilter;

/**
 * This class handles values of 'isEnabled' which aren't 'true' or 'false'. <br>
 * Shiro provides a mechanism to enable or disable filters with a single switch. If we want to
 * enable ssl filter for production, but disable for development, the obvious solution is to use
 * Maven resource filtering via the pom.xml. However, when running in Jetty during development,
 * resource filtering of files under WEB-INF (where security.xml is defined) doesn't happen, and the
 * Shiro filter mechanism chokes on the unresolved variable ${ssl.enabled} instead of 'true' or
 * 'false'. This class overrides setEnabled to handle this unresolved case.
 */
public class ShiroSslFilterMavenAgnostic extends SslFilter {

  public void setEnabledOverride(String isEnabled) {
    if (isUnresolved(isEnabled)) {
      setEnabled(false);
    } else {
      setEnabled(Boolean.parseBoolean(isEnabled));
    }
  }

  private boolean isUnresolved(String isEnabled) {
    return isEnabled.startsWith("${");
  }

  public String getEnabledOverride() {
    return Boolean.toString(isEnabled());
  }
}

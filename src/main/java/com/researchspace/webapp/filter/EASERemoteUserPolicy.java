package com.researchspace.webapp.filter;

import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;

/** Gets the EASE username based on HttpHeader information. */
public class EASERemoteUserPolicy extends AbstractSsoRemoteUserPolicy {

  @Value("${deployment.REMOTE_HEADER_NAME_FROM_PROPERTY}")
  private String remoteUserProperty;

  protected static final String RMU_HEADER = "REMOTE_USER";

  @Override
  public String getRemoteUser(HttpServletRequest httpRequest) {
    if (remoteUserProperty == null) {
      remoteUserProperty = RMU_HEADER;
      SECURITY_LOG.debug(
          "Warning: remote user key not set from the property file, using default: {}",
          remoteUserProperty);
    }
    String remoteUser = httpRequest.getHeader(remoteUserProperty);
    SECURITY_LOG.info("[{}] header from HttpRequest: {}", remoteUserProperty, remoteUser);

    if (remoteUser == null) {
      remoteUser = httpRequest.getRemoteUser();
      SECURITY_LOG.info("HttpRequest.getRemoteUser(): {}", remoteUser);
    }
    return remoteUser;
  }
}

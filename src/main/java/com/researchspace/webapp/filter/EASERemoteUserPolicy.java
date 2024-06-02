package com.researchspace.webapp.filter;

import com.researchspace.model.permissions.SecurityLogger;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/** Gets the EASE username based on HttpHeader information. */
public class EASERemoteUserPolicy implements RemoteUserRetrievalPolicy {

  @Value("${deployment.REMOTE_HEADER_NAME_FROM_PROPERTY}")
  private String remoteUserProperty;

  protected static final String RMU_HEADER = "REMOTE_USER";

  private static final Logger log = LoggerFactory.getLogger(SecurityLogger.class);

  @Override
  public String getRemoteUser(HttpServletRequest httpRequest) {
    if (remoteUserProperty == null) {
      remoteUserProperty = RMU_HEADER;
      log.debug(
          "Warning: remote user key not set from the property file, using default: {}",
          remoteUserProperty);
    }
    String remoteUser = httpRequest.getHeader(remoteUserProperty);
    log.info("[{}] header from HttpRequest: ", remoteUserProperty, remoteUser);

    if (remoteUser == null) {
      remoteUser = httpRequest.getRemoteUser();
      log.info("HttpRequest.getRemoteUser(): {}", remoteUser);
    }
    return remoteUser;
  }

  @Override
  public String getPassword() {
    return SSO_DUMMY_PASSWORD;
  }
}

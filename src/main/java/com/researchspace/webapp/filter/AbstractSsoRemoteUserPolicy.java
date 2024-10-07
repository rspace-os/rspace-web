package com.researchspace.webapp.filter;

import com.researchspace.model.permissions.SecurityLogger;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Translates OpenId claims form httpRequest headers into user information */
public abstract class AbstractSsoRemoteUserPolicy implements RemoteUserRetrievalPolicy {

  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  @Override
  public String getPassword() {
    return SSO_DUMMY_PASSWORD;
  }

  @Override
  public Map<RemoteUserAttribute, String> getOtherRemoteAttributes(HttpServletRequest httpRequest) {
    return Collections.emptyMap();
  }

  protected StringBuilder logHeaders(HttpServletRequest httpRequest) {
    StringBuilder sb = new StringBuilder();
    for (String header : Collections.list(httpRequest.getHeaderNames())) {
      sb.append(header + ":" + httpRequest.getHeader(header) + ", ");
    }
    return sb;
  }

  protected StringBuilder logReqAttributes(HttpServletRequest httpRequest) {
    Enumeration<String> attrNames = httpRequest.getAttributeNames();
    StringBuilder sb = new StringBuilder();
    while (attrNames.hasMoreElements()) {
      String attr = attrNames.nextElement();
      sb.append(attr + ":" + httpRequest.getAttribute(attr).toString() + ", ");
    }
    return sb;
  }

  protected StringBuilder logEnv() {
    StringBuilder sb = new StringBuilder();
    System.getenv().entrySet().stream()
        .forEach(e -> sb.append(e.getKey() + ":" + e.getValue() + ", "));
    return sb;
  }
}

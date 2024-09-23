package com.researchspace.webapp.filter;

import com.researchspace.model.permissions.SecurityLogger;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Gets the Shibboleth remote username based on HttpHeader information. */
public class SAMLRemoteUserPolicy implements RemoteUserRetrievalPolicy {

  protected static final String SHIBBOLETH_ID_ATTRIBUTE = "eduPersonPrincipalName";

  private static final Logger log = LoggerFactory.getLogger(SecurityLogger.class);

  // don't alter, edit or remove these as they may be used in production deployments
  // only add new values if needed.
  static final String[] shib_attributes = {
    "persistent-id",
    "eppn",
    "mail",
    "Shib-user",
    "Shib-displayName",
    "Shib-surName",
    "Shib-commonName",
    "Shib-givenName",
    "Shib-eduPersonPN",
    "Shib-email",
    "Shib-HomeOrg",
    "Shib-uid",
    "Shib-userStatus",
    "Shib-voName",
    "Shib-memberOf",
    "isAllowedPiRole"
  };

  @Override
  public String getRemoteUser(HttpServletRequest httpRequest) {

    log.debug("Req headers: {}", logHeaders(httpRequest).toString());
    log.debug("Environment variables: {}", logEnv().toString());
    log.debug("Attributes: {}", logReqAttributes(httpRequest).toString());
    log.debug("SAML attributes: {}", logSamlAttributes(httpRequest).toString());
    log.debug("Remote user:  {}", httpRequest.getRemoteUser());
    Object username = httpRequest.getAttribute(SHIBBOLETH_ID_ATTRIBUTE);
    log.info("SAML [{}] attribute from HttpRequest: {}", SHIBBOLETH_ID_ATTRIBUTE, username);

    if (username == null) {
      username = httpRequest.getAttribute("eppn");
      log.info("SAML [eppn] attribute from HttpRequest: {}", username);
    }
    return username == null ? null : username.toString();
  }

  private Object logReqAttributes(HttpServletRequest httpRequest) {
    Enumeration<String> attrNames = httpRequest.getAttributeNames();
    StringBuilder sb = new StringBuilder();
    while (attrNames.hasMoreElements()) {
      String attr = attrNames.nextElement();
      sb.append(attr + ":" + httpRequest.getAttribute(attr).toString() + ", ");
    }
    return sb;
  }

  private Object logSamlAttributes(HttpServletRequest httpRequest) {
    StringBuilder sb = new StringBuilder();
    /* names of the SAML attributes to display */

    for (int i = 0; i < shib_attributes.length; i++) {
      sb.append(shib_attributes[i] + " : " + httpRequest.getAttribute(shib_attributes[i]) + ", ");
    }
    return sb;
  }

  private StringBuilder logEnv() {
    StringBuilder sb = new StringBuilder();
    System.getenv().entrySet().stream()
        .forEach(e -> sb.append(e.getKey() + ":" + e.getValue() + ", "));
    return sb;
  }

  private StringBuilder logHeaders(HttpServletRequest httpRequest) {
    Enumeration<String> headrenames = httpRequest.getHeaderNames();
    StringBuilder sb = new StringBuilder();
    while (headrenames.hasMoreElements()) {
      String header = headrenames.nextElement();
      sb.append(header + ":" + httpRequest.getHeader(header) + ", ");
    }
    return sb;
  }

  @Override
  public String getPassword() {
    return SSO_DUMMY_PASSWORD;
  }

  public Map<String, String> getOtherRemoteAttributes(HttpServletRequest httpRequest) {
    Map<String, String> rc = new TreeMap<>();
    for (String attribute : shib_attributes) {
      String value = (String) httpRequest.getAttribute(attribute);
      if (!StringUtils.isBlank(value)) {
        rc.put(attribute, value);
      }
    }
    return rc;
  }
}

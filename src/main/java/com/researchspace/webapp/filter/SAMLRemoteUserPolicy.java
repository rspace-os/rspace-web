package com.researchspace.webapp.filter;

import java.util.Map;
import java.util.TreeMap;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

/** Gets the Shibboleth remote username based on HttpHeader information. */
public class SAMLRemoteUserPolicy extends AbstractSsoRemoteUserPolicy {

  protected static final String SHIBBOLETH_ID_ATTRIBUTE = "eduPersonPrincipalName";

  /* don't alter, edit or remove these as they may be used in production deployments;
  only add new values if needed. */
  private static final String[] shib_attributes = {
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

    SECURITY_LOG.debug("Req headers: {}", logHeaders(httpRequest));
    SECURITY_LOG.debug("Environment variables: {}", logEnv());
    SECURITY_LOG.debug("Attributes: {}", logReqAttributes(httpRequest));
    SECURITY_LOG.debug("SAML attributes: {}", logSamlAttributes(httpRequest));
    SECURITY_LOG.debug("Remote user:  {}", httpRequest.getRemoteUser());
    Object username = httpRequest.getAttribute(SHIBBOLETH_ID_ATTRIBUTE);
    SECURITY_LOG.info(
        "SAML [{}] attribute from HttpRequest: {}", SHIBBOLETH_ID_ATTRIBUTE, username);

    if (username == null) {
      username = httpRequest.getAttribute("eppn");
      SECURITY_LOG.info("SAML [eppn] attribute from HttpRequest: {}", username);
    }
    return username == null ? null : username.toString();
  }

  @Override
  public Map<RemoteUserAttribute, String> getOtherRemoteAttributes(HttpServletRequest httpRequest) {
    Map<RemoteUserAttribute, String> rc = new TreeMap<>();

    String mail = (String) httpRequest.getAttribute("mail");
    if (StringUtils.isBlank(mail)) {
      mail = (String) httpRequest.getAttribute("Shib-email");
    }
    if (!StringUtils.isBlank(mail)) {
      rc.put(RemoteUserAttribute.EMAIL, mail);
    }

    String givenName = (String) httpRequest.getAttribute("Shib-givenName");
    if (!StringUtils.isBlank(givenName)) {
      rc.put(RemoteUserAttribute.FIRST_NAME, givenName);
    }

    String surName = (String) httpRequest.getAttribute("Shib-surName");
    if (!StringUtils.isBlank(surName)) {
      rc.put(RemoteUserAttribute.LAST_NAME, surName);
    }

    String isAllowedPiRole = (String) httpRequest.getAttribute("isAllowedPiRole");
    if (!StringUtils.isBlank(isAllowedPiRole)) {
      rc.put(RemoteUserAttribute.IS_ALLOWED_PI_ROLE, isAllowedPiRole);
    }

    return rc;
  }

  private Object logSamlAttributes(HttpServletRequest httpRequest) {
    StringBuilder sb = new StringBuilder();
    for (String shibAttribute : shib_attributes) {
      sb.append(shibAttribute + " : " + httpRequest.getAttribute(shibAttribute) + ", ");
    }
    return sb;
  }
}

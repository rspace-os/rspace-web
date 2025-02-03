package com.researchspace.webapp.filter;

import com.researchspace.core.util.CryptoUtils;
import java.util.Map;
import java.util.TreeMap;
import javax.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;

/** Translates OpenId claims form httpRequest headers into user information */
@Slf4j
@Setter(AccessLevel.PROTECTED)
public class OpenIdRemoteUserPolicy extends AbstractSsoRemoteUserPolicy {

  private static final String ADDITIONAL_CLAIMS_SEPARATOR = ".";

  @Value("${deployment.sso.openid.usernameClaim}")
  private String usernameClaim;

  @Value("${deployment.sso.openid.additionalUsernameClaim}")
  private String additionalUsernameClaim;

  @Value("${deployment.sso.openid.additionalHashedUsernameClaim}")
  private String additionalHashedUsernameClaim;

  @Value("${deployment.sso.openid.additionalUsernameClaimLength}")
  private int additionalUsernameClaimLength = 4;

  @Value("${deployment.sso.openid.emailClaim}")
  private String emailClaim;

  @Value("${deployment.sso.openid.firstNameClaim}")
  private String firstNameClaim;

  @Value("${deployment.sso.openid.lastNameClaim}")
  private String lastNameClaim;

  @Override
  public String getRemoteUser(HttpServletRequest httpRequest) {
    log.debug("Req headers: {}", logHeaders(httpRequest));
    String username = getOpenIdUsernameFromRequestClaims(httpRequest);
    log.info("Logging in remote user: {}", username);
    return username;
  }

  private String getOpenIdUsernameFromRequestClaims(HttpServletRequest httpRequest) {
    log.debug("using usernameClaim: {}", usernameClaim);
    log.debug("using additionalUsernameClaim: {}", additionalUsernameClaim);
    log.debug("using additionalHashedUsernameClaim: {}", additionalHashedUsernameClaim);

    String username = httpRequest.getHeader(usernameClaim);

    // additional claim
    if (StringUtils.isNotEmpty(additionalUsernameClaim)) {
      String additionalClaim = httpRequest.getHeader(additionalUsernameClaim);
      if (StringUtils.length(additionalClaim) > additionalUsernameClaimLength) {
        additionalClaim = StringUtils.substring(additionalClaim, 0, additionalUsernameClaimLength);
      }
      if (StringUtils.isNotEmpty(additionalClaim)) {
        username += ADDITIONAL_CLAIMS_SEPARATOR + additionalClaim;
      }
    }

    // additional hashed claim
    if (StringUtils.isNotEmpty(additionalHashedUsernameClaim)) {
      String additionalHashedClaim = httpRequest.getHeader(additionalHashedUsernameClaim);
      if (StringUtils.isNotEmpty(additionalHashedClaim)) {
        additionalHashedClaim = CryptoUtils.hashWithSha256inHex(additionalHashedClaim);
        username +=
            ADDITIONAL_CLAIMS_SEPARATOR
                + StringUtils.substring(additionalHashedClaim, 0, additionalUsernameClaimLength);
      }
    }

    return username;
  }

  @Override
  public Map<RemoteUserAttribute, String> getOtherRemoteAttributes(HttpServletRequest httpRequest) {
    Map<RemoteUserAttribute, String> rc = new TreeMap<>();

    String mail = httpRequest.getHeader(emailClaim);
    if (!StringUtils.isBlank(mail)) {
      rc.put(RemoteUserAttribute.EMAIL, mail);
    }

    String firstName = httpRequest.getHeader(firstNameClaim);
    if (!StringUtils.isBlank(firstName)) {
      rc.put(RemoteUserAttribute.FIRST_NAME, firstName);
    }

    String lastName = httpRequest.getHeader(lastNameClaim);
    if (!StringUtils.isBlank(lastName)) {
      rc.put(RemoteUserAttribute.LAST_NAME, lastName);
    }

    return rc;
  }
}

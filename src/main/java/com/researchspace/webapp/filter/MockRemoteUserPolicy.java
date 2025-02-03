package com.researchspace.webapp.filter;

import java.util.Map;
import java.util.TreeMap;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;

/**
 * Use this in dev/ test environments to set in a remote username/password using a default property
 * name
 */
public class MockRemoteUserPolicy extends AbstractSsoRemoteUserPolicy {

  @Value("${mock.remote.username:}")
  private String username = "user1a";

  @Value("${mock.remote.email:somebody@somwhere.com}")
  private String email = "somebody@somwhere.com";

  @Value("${mock.remote.firstName:Fred}")
  private String firstName = "Fred";

  @Value("${mock.remote.lastName:Smith}")
  private String lastName = "Smith";

  @Value("${mock.remote.isAllowedPiRole:}")
  private String isAllowedPiRole;

  @Override
  public String getRemoteUser(HttpServletRequest request) {
    return username;
  }

  @Override
  public Map<RemoteUserAttribute, String> getOtherRemoteAttributes(HttpServletRequest httpRequest) {
    Map<RemoteUserAttribute, String> rc = new TreeMap<>();
    rc.put(RemoteUserAttribute.EMAIL, email);
    rc.put(RemoteUserAttribute.FIRST_NAME, firstName);
    rc.put(RemoteUserAttribute.LAST_NAME, lastName);
    rc.put(RemoteUserAttribute.IS_ALLOWED_PI_ROLE, isAllowedPiRole);

    return rc;
  }

  /* ===============
   * for testing
   * ============== */

  public void setUsername(String username) {
    this.username = username;
  }

  void setIsAllowedPiRole(String isAllowedPiRole) {
    this.isAllowedPiRole = isAllowedPiRole;
  }
}

package com.researchspace.webapp.filter;

import java.util.Map;
import java.util.TreeMap;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;

/**
 * Use this in dev/ test environments to set in a remote username/password using a default property
 * name
 */
public class MockRemoteUserPolicy implements RemoteUserRetrievalPolicy {

  @Value("${mock.remote.username:}")
  private String username = "user1a";

  @Value("${default.user.password}")
  private String password;

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
  public String getPassword() {
    return password;
  }

  @Override
  public Map<String, String> getOtherRemoteAttributes(HttpServletRequest httpRequest) {
    Map<String, String> rc = new TreeMap<>();
    rc.put("mail", email);
    rc.put("Shib-surName", lastName);
    rc.put("Shib-givenName", firstName);
    rc.put("isAllowedPiRole", isAllowedPiRole);

    return rc;
  }

  /* ===============
   * for testing
   * ============== */

  public void setUsername(String username) {
    this.username = username;
  }

  void setPassword(String pword) {
    this.password = pword;
  }

  void setIsAllowedPiRole(String isAllowedPiRole) {
    this.isAllowedPiRole = isAllowedPiRole;
  }
}

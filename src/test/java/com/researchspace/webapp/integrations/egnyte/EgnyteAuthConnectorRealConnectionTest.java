package com.researchspace.webapp.integrations.egnyte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@RunWith(ConditionalTestRunner.class)
public class EgnyteAuthConnectorRealConnectionTest extends SpringTransactionalTest {

  @Autowired private EgnyteAuthConnector connector;

  // credentials for real test account created at https://apprspace.egnyte.com
  @Value("${egnyte.realConnectionTest.username}")
  private String testUsername;

  @Value("${egnyte.realConnectionTest.password}")
  private String testPassword;

  // real access token - can be retrieved with testAccessTokenQuery() test below
  @Value("${egnyte.realConnectionTest.token}")
  private String testUserAccessToken;

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testAccessTokenQuery() throws Exception {

    // non-existing user credentials
    Map<String, Object> authenticationError =
        connector.queryForEgnyteAccessToken("dummyUser", "dummyPassword");
    assertNull(authenticationError);

    // existing user credentials
    Map<String, Object> happyTokenRequest =
        connector.queryForEgnyteAccessToken(testUsername, testPassword);
    assertNotNull(happyTokenRequest, "no token after querying egnyte with test credentials");
    assertEquals(3, happyTokenRequest.size());
    assertEquals(testUserAccessToken, happyTokenRequest.get("access_token"));
    assertEquals("bearer", happyTokenRequest.get("token_type"));
    assertEquals(-1, happyTokenRequest.get("expires_in"));
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testTokenVerificationQuery() throws Exception {

    // invalid token - no entity connected
    Map<String, Object> tokenError = connector.queryForEgnyteUserInfoWithAccessToken("dummyToken");
    assertNull(tokenError);

    // valid token should retrieve user details
    Map<String, Object> happyTokenValidation =
        connector.queryForEgnyteUserInfoWithAccessToken(testUserAccessToken);
    assertNotNull(happyTokenValidation, "no user info after querying egnyte with test token");
    assertEquals(
        6,
        happyTokenValidation
            .size()); // six fields are returned in successful response from 'userinfo' endpoint
    assertEquals(13, happyTokenValidation.get("id")); // rspaceTest user id (in egnyte)
    assertEquals("rspacetest", happyTokenValidation.get("username"));
    assertEquals("dev@researchspace.com", happyTokenValidation.get("email"));
    assertEquals("RSpace", happyTokenValidation.get("first_name"));
    assertEquals("Test", happyTokenValidation.get("last_name"));
    assertEquals("standard", happyTokenValidation.get("user_type"));
  }
}

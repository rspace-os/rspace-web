package com.researchspace.webapp.integrations.egnyte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@EnabledIfSystemProperty(named = "nightly", matches = "(|true)")
public class EgnyteAuthConnectorRealConnectionTest extends SpringTransactionalTest {

  @Autowired private EgnyteAuthConnector connector;

  // credentials for real test account created at https://apprspace.egnyte.com
  @Value("${egnyte.realConnectionTest.username}")
  private String testUsername;

  @Value("${egnyte.realConnectionTest.password}")
  private String testPassword;

  @Test
  public void testAccessTokenQuery() throws Exception {

    // non-existing user credentials
    Map<String, Object> authenticationError =
        connector.queryForEgnyteAccessToken("dummyUser", "dummyPassword");
    assertNull(authenticationError);

    // existing user credentials
    Map<String, Object> happyTokenRequest =
        connector.queryForEgnyteAccessToken(testUsername, testPassword);
    assertNotNull(happyTokenRequest, "no token after querying egnyte with test credentials");
    assertTrue(happyTokenRequest.size() >= 3, "lack of expected data in egynte response");
    assertThat(happyTokenRequest).containsEntry("token_type", "Bearer");
    assertNotNull(happyTokenRequest.get("access_token"));
    assertNotNull(happyTokenRequest.get("expires_in"));
  }

  @Test
  public void testTokenVerificationQuery() throws Exception {

    // invalid token - no entity connected
    Map<String, Object> tokenError = connector.queryForEgnyteUserInfoWithAccessToken("dummyToken");
    assertNull(tokenError);

    // get latest token from user credentials
    Map<String, Object> happyTokenRequest =
        connector.queryForEgnyteAccessToken(testUsername, testPassword);
    assertNotNull(happyTokenRequest, "no token after querying egnyte with test credentials");
    String testUserAccessToken = (String) happyTokenRequest.get("access_token");

    // valid token should retrieve user's details
    Map<String, Object> happyTokenValidation =
        connector.queryForEgnyteUserInfoWithAccessToken(testUserAccessToken);
    assertNotNull(happyTokenValidation, "no user info after querying egnyte with test token");
    assertThat(happyTokenValidation)
        .hasSize(6); // six fields are returned in successful response from 'userinfo' endpoint
    assertThat(happyTokenValidation).containsEntry("id", 13); // rspaceTest user id (in egnyte)
    assertThat(happyTokenValidation).containsEntry("username", "rspacetest");
    assertThat(happyTokenValidation).containsEntry("email", "dev@researchspace.com");
    assertThat(happyTokenValidation).containsEntry("first_name", "RSpace");
    assertThat(happyTokenValidation).containsEntry("last_name", "Test");
    assertThat(happyTokenValidation).containsEntry("user_type", "standard");
  }
}

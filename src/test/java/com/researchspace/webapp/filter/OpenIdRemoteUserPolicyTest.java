package com.researchspace.webapp.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy.RemoteUserAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class OpenIdRemoteUserPolicyTest {

  OpenIdRemoteUserPolicy openIdPolicy;
  MockHttpServletRequest req;

  @BeforeEach
  public void before() {
    openIdPolicy = new OpenIdRemoteUserPolicy();
    req = new MockHttpServletRequest();
  }

  @Test
  public void getRemoteUsername() {

    // empty request
    assertNull(openIdPolicy.getRemoteUser(req));

    // request with all headers
    req.addHeader("OIDC_CLAIM_preferred_username", "user1234");
    req.addHeader("OIDC_CLAIM_sub", "0a3ba4df5");
    req.addHeader("OIDC_CLAIM_iss", "https://test.researchspace.com");

    // policy setting only a username claim, based on preferred_username
    openIdPolicy.setUsernameClaim("OIDC_CLAIM_preferred_username");
    assertEquals("user1234", openIdPolicy.getRemoteUser(req));

    // policy also setting additional claim, based on sub
    openIdPolicy.setAdditionalUsernameClaim("OIDC_CLAIM_sub");
    assertEquals("user1234.0a3b", openIdPolicy.getRemoteUser(req));

    // policy also setting additional hashed claim, based on iss
    openIdPolicy.setAdditionalHashedUsernameClaim("OIDC_CLAIM_iss");
    assertEquals("user1234.0a3b.3e07", openIdPolicy.getRemoteUser(req));

    // policy setting only hashed claim as additional
    openIdPolicy.setAdditionalUsernameClaim("");
    assertEquals("user1234.3e07", openIdPolicy.getRemoteUser(req));
  }

  @Test
  public void getRemoteOtherAttributes() {
    openIdPolicy.setEmailClaim("OIDC_CLAIM_email");
    openIdPolicy.setFirstNameClaim("OIDC_CLAIM_given_name");
    openIdPolicy.setLastNameClaim("OIDC_CLAIM_family_name");

    assertNotNull(openIdPolicy.getOtherRemoteAttributes(req));
    assertThat(openIdPolicy.getOtherRemoteAttributes(req)).isEmpty();

    req.addHeader("OIDC_CLAIM_email", "someone@somewhere.com");
    req.addHeader("OIDC_CLAIM_given_name", "Mark");
    req.addHeader("OIDC_CLAIM_family_name", "Smith");
    req.addHeader("OIDC_CLAIM_unknown", "unknown");

    assertThat(openIdPolicy.getOtherRemoteAttributes(req)).isNotEmpty();
    assertThat(openIdPolicy.getOtherRemoteAttributes(req)).hasSize(3);
    assertThat(openIdPolicy.getOtherRemoteAttributes(req))
        .containsEntry(RemoteUserAttribute.EMAIL, "someone@somewhere.com");
    assertThat(openIdPolicy.getOtherRemoteAttributes(req))
        .containsEntry(RemoteUserAttribute.FIRST_NAME, "Mark");
    assertThat(openIdPolicy.getOtherRemoteAttributes(req))
        .containsEntry(RemoteUserAttribute.LAST_NAME, "Smith");
  }
}

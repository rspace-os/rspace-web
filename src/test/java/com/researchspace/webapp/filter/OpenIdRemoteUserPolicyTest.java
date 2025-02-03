package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy.RemoteUserAttribute;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class OpenIdRemoteUserPolicyTest {

  OpenIdRemoteUserPolicy openIdPolicy;
  MockHttpServletRequest req;

  @Before
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
    assertTrue(openIdPolicy.getOtherRemoteAttributes(req).isEmpty());

    req.addHeader("OIDC_CLAIM_email", "someone@somewhere.com");
    req.addHeader("OIDC_CLAIM_given_name", "Mark");
    req.addHeader("OIDC_CLAIM_family_name", "Smith");
    req.addHeader("OIDC_CLAIM_unknown", "unknown");

    assertFalse(openIdPolicy.getOtherRemoteAttributes(req).isEmpty());
    assertEquals(3, openIdPolicy.getOtherRemoteAttributes(req).size());
    assertEquals(
        "someone@somewhere.com",
        openIdPolicy.getOtherRemoteAttributes(req).get(RemoteUserAttribute.EMAIL));
    assertEquals(
        "Mark", openIdPolicy.getOtherRemoteAttributes(req).get(RemoteUserAttribute.FIRST_NAME));
    assertEquals(
        "Smith", openIdPolicy.getOtherRemoteAttributes(req).get(RemoteUserAttribute.LAST_NAME));
  }
}

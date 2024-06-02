package com.researchspace.webapp.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class SAMLRemoteUserPolicyTest {

  SAMLRemoteUserPolicy samlPolicy;
  MockHttpServletRequest req;

  @Before
  public void before() {
    samlPolicy = new SAMLRemoteUserPolicy();
    req = new MockHttpServletRequest();
  }

  @Test
  public void getRemoteUser() {
    assertNull(samlPolicy.getRemoteUser(req));
    req.setAttribute("eppn", "username");
    assertEquals("username", samlPolicy.getRemoteUser(req));
  }

  // rspac-2117
  @Test
  public void getRemoteOtherAttributes() {
    assertNotNull(samlPolicy.getOtherRemoteAttributes(req));
    // this is a known saml attribute ID
    req.setAttribute("mail", "someone@somewhere.com");
    assertEquals("someone@somewhere.com", samlPolicy.getOtherRemoteAttributes(req).get("mail"));

    // get null if not set
    req.setAttribute("unknown-attribute", "someone@somewhere.com");
    assertNull(samlPolicy.getOtherRemoteAttributes(req).get("unknown-attribute"));
  }
}

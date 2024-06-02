package com.researchspace.webapp.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.researchspace.service.UserExistsException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class RemoteUserRetrievalPolicyTest {

  private static final String TEST_REMOTE_USERNAME = "user@institution";

  private MockHttpServletRequest req;

  @Before
  public void setUp() {
    req = new MockHttpServletRequest();
  }

  @Test
  public void checkEASERemoteUserRetrieval() throws UserExistsException {

    RemoteUserRetrievalPolicy policy = new EASERemoteUserPolicy();
    assertNull(policy.getRemoteUser(req));

    req.addHeader(EASERemoteUserPolicy.RMU_HEADER, TEST_REMOTE_USERNAME);
    assertEquals(TEST_REMOTE_USERNAME, policy.getRemoteUser(req));

    req = new MockHttpServletRequest();
    assertNull(policy.getRemoteUser(req));

    req.setRemoteUser(TEST_REMOTE_USERNAME);
    assertEquals(TEST_REMOTE_USERNAME, policy.getRemoteUser(req));
  }

  @Test
  public void checkSAMLRemoteUserRetrieval() throws UserExistsException {

    RemoteUserRetrievalPolicy policy = new SAMLRemoteUserPolicy();
    assertNull(policy.getRemoteUser(req));

    req.setAttribute(SAMLRemoteUserPolicy.SHIBBOLETH_ID_ATTRIBUTE, TEST_REMOTE_USERNAME);
    assertEquals(TEST_REMOTE_USERNAME, policy.getRemoteUser(req));
  }
}

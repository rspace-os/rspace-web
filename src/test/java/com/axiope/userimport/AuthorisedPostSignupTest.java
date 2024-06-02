package com.axiope.userimport;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import javax.servlet.http.HttpServletRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;

public class AuthorisedPostSignupTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("authorisedPostSignup")
  IPostUserSignup auIPostUserSignup;

  HttpServletRequest mockRequest;

  @Before
  public void setUp() throws Exception {
    mockRequest = new MockHttpServletRequest();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testPostUserCreate() {
    User user = TestFactory.createAnyUser("any");
    auIPostUserSignup.postUserCreate(user, mockRequest, "any");

    assertTrue(userDao.get(user.getId()).isAccountLocked());
  }

  @Test
  public void testGetRedirect() {
    assertNotNull(auIPostUserSignup.getRedirect(null));
  }
}

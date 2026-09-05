package com.axiope.userimport;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;

public class AuthorisedPostSignupTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("authorisedPostSignup")
  IPostUserSignup auIPostUserSignup;

  HttpServletRequest mockRequest;

  @BeforeEach
  public void setUp() throws Exception {
    mockRequest = new MockHttpServletRequest();
  }

  @AfterEach
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

package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.UserExistsException;
import com.researchspace.service.UserManager;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectRetrievalFailureException;

@RunWith(MockitoJUnitRunner.class)
public class ExternalOauthUserSignupPolicyTest {

  @InjectMocks ExternalOauthUserSignupPolicy policy;
  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock UserManager userMgr;
  MockHttpServletRequest req;
  ;

  @Before
  public void setUp() throws Exception {
    req = new MockHttpServletRequest();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSaveNewUserHappyCase() throws UserExistsException {
    User user = TestFactory.createAnyUser("user1");
    policy.saveUser(user, req);
    Mockito.verify(userMgr, Mockito.times(1)).saveNewUser(user);
  }

  @Test
  public void testSaveNewUserExistingEmailDoesntLoging() throws UserExistsException {
    User user = TestFactory.createAnyUser("user1");
    User fromDBUserWithSameEmail = TestFactory.createAnyUser("user2");
    fromDBUserWithSameEmail.setEmail(user.getEmail());
    List<User> fromDB = TransformerUtils.toList(fromDBUserWithSameEmail);
    when(userMgr.saveNewUser(user)).thenThrow(userExistsException());
    when(userMgr.getUserByEmail(user.getEmail())).thenReturn(fromDB);
    assertEquals(fromDBUserWithSameEmail, policy.saveUser(user, req));
    // login is done elsewhere, we don't need to login here

  }

  @Test(expected = UserExistsException.class)
  public void testSaveNewUserExistingUsername() throws UserExistsException {
    User user = TestFactory.createAnyUser("user1");
    User fromDBUserWithSameUsername = TestFactory.createAnyUser("user1");
    fromDBUserWithSameUsername.setEmail("differeentEmail@test.com");

    List<User> fromDB = Collections.emptyList();
    when(userMgr.saveNewUser(user)).thenThrow(userExistsException());
    when(userMgr.getUserByEmail(user.getEmail())).thenReturn(fromDB);
    policy.saveUser(user, req);
  }

  @Test(expected = IllegalStateException.class)
  public void testSaveNewUserISEForUnlikelyScenario() throws UserExistsException {
    // diff usernames and email but not signed up
    User user = TestFactory.createAnyUser("user1");
    User other = TestFactory.createAnyUser("user2");
    other.setEmail("differeentEmail@test.com");

    List<User> fromDB = Collections.emptyList();
    when(userMgr.saveNewUser(user)).thenThrow(userExistsException());
    when(userMgr.getUserByEmail(user.getEmail())).thenReturn(fromDB);
    when(userMgr.getUserByUsername(user.getUsername())).thenThrow(objectRetrievalEXc());
    policy.saveUser(user, req);
  }

  private ObjectRetrievalFailureException objectRetrievalEXc() {
    return new ObjectRetrievalFailureException("no user", new RuntimeException());
  }

  private UserExistsException userExistsException() {
    return new UserExistsException("User exists");
  }
}

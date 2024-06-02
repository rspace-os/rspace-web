package com.researchspace.ldap;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.UserManager;
import com.researchspace.service.UserSignupException;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

public class UserLdapRepoTest extends SpringTransactionalTest {

  @Autowired private UserLdapRepo userLdapRepo;

  @Autowired private UserManager userManager;

  @Autowired private IPropertyHolder iProperties;

  private PropertyHolder properties;
  private String origProp_ldapEnabled;
  private boolean origProp_ldapAuthEnabled;

  @Before
  public void setUp() throws Exception {
    properties = (PropertyHolder) iProperties;
    origProp_ldapEnabled = properties.getLdapEnabled();
    origProp_ldapAuthEnabled = properties.isLdapAuthenticationEnabled();
  }

  @After
  public void tearDown() throws Exception {
    properties.setLdapEnabled(origProp_ldapEnabled);
    properties.setLdapAuthenticationEnabled(origProp_ldapAuthEnabled + "");
  }

  @Test
  public void testLdapDisabledNoAction() throws Exception {

    properties.setLdapEnabled("false");

    CoreTestUtils.assertExceptionThrown(
        () -> userLdapRepo.findUserByUsername("user"),
        IllegalStateException.class,
        containsString("LDAP not configured"));
    CoreTestUtils.assertExceptionThrown(
        () -> userLdapRepo.authenticate("user", "pass"),
        IllegalStateException.class,
        containsString("LDAP not configured"));
    CoreTestUtils.assertExceptionThrown(
        () -> userLdapRepo.signupLdapUser(null),
        IllegalStateException.class,
        containsString("LDAP not configured"));
  }

  @Test
  public void testLdapUserSignup() throws UserSignupException {
    properties.setLdapEnabled("true");
    properties.setLdapAuthenticationEnabled("true");

    // let's try user with email and last name missing in LDAP
    User testUser = new User("testLdapUser");
    testUser.setFirstName("first");
    testUser.setSid("S-1-5-32-546");
    userLdapRepo.signupLdapUser(testUser);

    User signedUser = userManager.getUserByUsername(testUser.getUsername());
    assertEquals(SignupSource.LDAP, signedUser.getSignupSource());
    assertEquals(testUser.getFirstName(), signedUser.getFirstName());
    assertEquals(testUser.getSid(), signedUser.getSid());
    assertEquals("-", signedUser.getLastName());
    assertEquals("testLdapUser-unknown@researchspace.com", signedUser.getEmail());

    // let's try with short username (relaxed username rules)
    User testUser2 = new User("ldap1");
    testUser2.setLastName("lastName");
    userLdapRepo.signupLdapUser(testUser2);

    User signedUser2 = userManager.getUserByUsername(testUser2.getUsername());
    assertEquals(SignupSource.LDAP, signedUser2.getSignupSource());
    assertEquals("-", signedUser2.getFirstName());
    assertEquals("lastName", signedUser2.getLastName());
    assertEquals("ldap1-unknown@researchspace.com", signedUser2.getEmail());
  }

  @Test
  public void testSidRetrieval() throws Exception {

    // let's add non-LDAP user
    User testUser = createAndSaveRandomUser();
    testUser.setSignupSource(SignupSource.MANUAL);
    userManager.save(testUser);

    // an ldap user who doesn't have SID
    User testUser2 = createAndSaveRandomUser();
    testUser2.setSignupSource(SignupSource.LDAP);
    userManager.save(testUser2);

    // and third one who already have a SID
    User testUser3 = createAndSaveRandomUser();
    testUser3.setSignupSource(SignupSource.LDAP);
    testUser3.setSid("S-1-5-32-3");
    userManager.save(testUser3);

    // let's use a spy to stub 'findUserByUsername' method that does lookup in ldap
    UserLdapRepo spyUserLdapRepo = Mockito.spy(userLdapRepo);
    User testLdapUser2 = new User(testUser2.getUsername());
    testLdapUser2.setSid("S-1-5-32-546");

    Mockito.doThrow(new IllegalStateException("test not expected to ask for testuser"))
        .when(spyUserLdapRepo)
        .findUserByUsername(testUser.getUsername());
    Mockito.doReturn(testLdapUser2)
        .when(spyUserLdapRepo)
        .findUserByUsername(testUser2.getUsername());
    Mockito.doThrow(new IllegalStateException("test not expected to ask for testuser3"))
        .when(spyUserLdapRepo)
        .findUserByUsername(testUser3.getUsername());

    // run sid retrieval for non-ldap user
    CoreTestUtils.assertExceptionThrown(
        () -> spyUserLdapRepo.retrieveSidForLdapUser(testUser.getUsername()),
        IllegalArgumentException.class,
        containsString("non-ldap"));

    // run for ldap user without sid
    String retrievedSID = spyUserLdapRepo.retrieveSidForLdapUser(testUser2.getUsername());
    assertEquals(testLdapUser2.getSid(), retrievedSID);

    // run for ldap user with sid
    CoreTestUtils.assertExceptionThrown(
        () -> spyUserLdapRepo.retrieveSidForLdapUser(testUser3.getUsername()),
        IllegalArgumentException.class,
        containsString("user with SID"));
  }
}

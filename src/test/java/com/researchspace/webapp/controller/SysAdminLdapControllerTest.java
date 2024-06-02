package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;

import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.UserManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class SysAdminLdapControllerTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock UserManager userManager;

  @Mock UserLdapRepo userLdapRepo;
  @InjectMocks SysAdminLdapController controller;

  @Test
  public void findUsersForSidRetrieval() {

    // a non-LDAP user
    User nonLdapUser = TestFactory.createAnyUser("nonldap");
    nonLdapUser.setSignupSource(SignupSource.MANUAL);

    // ldap user without sid
    User ldapUserNoSid = TestFactory.createAnyUser("ldapNoSid");
    ldapUserNoSid.setSignupSource(SignupSource.LDAP);

    // and third one who already have a SID
    User ldapUserWithSid = TestFactory.createAnyUser("ldapNoSid");
    ldapUserWithSid.setSignupSource(SignupSource.LDAP);
    ldapUserWithSid.setSid("S-1-5-32-3");

    List<User> users = new ArrayList<>();
    users.add(nonLdapUser);
    users.add(ldapUserNoSid);
    users.add(ldapUserWithSid);
    Mockito.when(userManager.getAll()).thenReturn(users);

    // call user retrieval - only user without sid should be found
    List<String> retrievedUsers = controller.getLdapUsersWithoutSID().getData();
    assertEquals(1, retrievedUsers.size());
    assertEquals(ldapUserNoSid.getUsername(), retrievedUsers.get(0));
  }

  @Test
  public void retrieveSidForUser() {
    String testUsername = "testUser";
    String testSID = "test-sid";
    Mockito.when(userLdapRepo.retrieveSidForLdapUser(testUsername)).thenReturn(testSID);
    assertEquals(testSID, controller.retrieveSidForLdapUser(testUsername).getData());
  }
}

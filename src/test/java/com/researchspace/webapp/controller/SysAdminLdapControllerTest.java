package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SysAdminLdapControllerTest {

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

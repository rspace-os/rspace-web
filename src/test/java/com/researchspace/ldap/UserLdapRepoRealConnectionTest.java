package com.researchspace.ldap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.researchspace.ldap.impl.UserLdapRepoImpl;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.DefaultTestContext;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * Tests autowired UserLdapRepo, which currently connects to test ldap server on
 * kudu.researchspace.com (as configured in deployment.properties and set by BaseConfig)
 */
@RunWith(ConditionalTestRunner.class)
@DefaultTestContext
public class UserLdapRepoRealConnectionTest extends AbstractJUnit4SpringContextTests {

  // binary value of this sid is saved in openldap on kudu, in userPKCS12 attribute
  private static final String LDAPUSER1_TEST_SID = "S-1-5-32-546";

  @Autowired private UserLdapRepoImpl userLdapRepo;

  @Autowired private IPropertyHolder iProperties;

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testRetrieveTestUserFromLdap() {

    User ldapUser1 = userLdapRepo.findUserByUsername("ldapUser1");
    assertNotNull(ldapUser1);
    assertEquals("User One", ldapUser1.getFullName());
    assertEquals("ldapUser1@researchspace.com", ldapUser1.getEmail());
    assertEquals(LDAPUSER1_TEST_SID, ldapUser1.getSid());
    assertEquals("cn=ldapUser1,dc=test,dc=howler,dc=researchspace,dc=com", ldapUser1.getToken());

    System.out.println("found user: " + ldapUser1);

    // found user with no dn
    User ldapUser2 = userLdapRepo.findUserByUsername("ldapUserEmptyDN");
    assertNotNull(ldapUser2);
    assertEquals("User Empty Description", ldapUser2.getFullName());
    assertEquals("ldapUserEmptyDescription@researchspace.com", ldapUser2.getEmail());
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testAuthenticateUser() {
    boolean isLdapAuthEnabled = iProperties.isLdapAuthenticationEnabled();
    ((PropertyHolder) iProperties).setLdapAuthenticationEnabled("true");

    try {
      // wrong username or password
      User unknownUser = userLdapRepo.authenticate("unknown", "password");
      assertNull(unknownUser);
      User wrongPassword = userLdapRepo.authenticate("user1a", "password");
      assertNull(wrongPassword);

      // correct username and password
      User correctLogin = userLdapRepo.authenticate("ldapUser1", "ldapUser1a");
      assertNotNull(correctLogin);
      assertEquals("ldapUser1", correctLogin.getUsername());
      assertEquals("ldapUser1@researchspace.com", correctLogin.getEmail());
    } finally {
      ((PropertyHolder) iProperties).setLdapAuthenticationEnabled(isLdapAuthEnabled + "");
    }
  }

  /*
   * Helper test, uncomment to run saving sid as a binary file that can be later imported to openldap
   */
  // @Test
  public void generateBinarySID() throws IOException {
    File sidFile = File.createTempFile("testBinarySid", ".dat");

    byte[] objectSid = LdapUtils.convertStringSidToBinary(LDAPUSER1_TEST_SID);
    DataOutputStream os = new DataOutputStream(new FileOutputStream(sidFile));
    os.write(objectSid);
    os.close();

    // verify the file contains correct sid bytes
    byte[] bytesFromFile = Files.readAllBytes(sidFile.toPath());
    String sidFromFile = LdapUtils.convertBinarySidToString(bytesFromFile);
    assertEquals(LDAPUSER1_TEST_SID, sidFromFile);

    //	System.out.println("test objectSID saved into: " + sidFile.toString());
  }
}

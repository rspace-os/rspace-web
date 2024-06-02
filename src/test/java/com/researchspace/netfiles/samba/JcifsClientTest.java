package com.researchspace.netfiles.samba;

import static org.junit.Assert.assertEquals;

import com.researchspace.testutils.RSpaceTestUtils;
import java.net.MalformedURLException;
import org.junit.Test;

public class JcifsClientTest {

  @Test
  public void clientCanBeSerialized() throws MalformedURLException {
    JcifsClient sc = new JcifsClient("test", "test", "test", "smb://test");
    sc.getConnectionTarget("test");

    RSpaceTestUtils.assertObjectSerializable(sc);
  }

  @Test
  public void sambaClientKnowsCorrectUsername() {
    String testUsername = "testUsername";
    JcifsClient sc = new JcifsClient(testUsername, "user", "domain", "smb://test");
    assertEquals("client should remember the username", testUsername, sc.getUsername());
  }

  @Test
  public void sambaClientAuthenticatesWithCorrectUsernameAndDomain() {

    String testUsername = "testUsername";
    String testDomain = "testDomain";
    JcifsClient sc = new JcifsClient(testUsername, "testPass", testDomain, "smb://test");

    assertEquals(
        "samba client should authenticate with provided the username",
        testUsername,
        sc.getAuthenticationPrincipal().getUsername());
    assertEquals(
        "samba client should authenticate with provided domain",
        testDomain,
        sc.getAuthenticationPrincipal().getDomain());
  }
}

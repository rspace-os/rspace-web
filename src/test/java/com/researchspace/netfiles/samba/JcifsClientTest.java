package com.researchspace.netfiles.samba;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.testutils.RSpaceTestUtils;
import java.net.MalformedURLException;
import org.junit.jupiter.api.Test;

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
    assertEquals(testUsername, sc.getUsername(), "client should remember the username");
  }

  @Test
  public void sambaClientAuthenticatesWithCorrectUsernameAndDomain() {

    String testUsername = "testUsername";
    String testDomain = "testDomain";
    JcifsClient sc = new JcifsClient(testUsername, "testPass", testDomain, "smb://test");

    assertEquals(
        testUsername,
        sc.getAuthenticationPrincipal().getUsername(),
        "samba client should authenticate with provided the username");
    assertEquals(
        testDomain,
        sc.getAuthenticationPrincipal().getDomain(),
        "samba client should authenticate with provided domain");
  }
}

package com.researchspace.netfiles.samba;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.testutils.RSpaceTestUtils;
import java.net.MalformedURLException;
import org.junit.Test;

public class SmbjClientTest {

  @Test
  public void clientCanBeSerialized() throws MalformedURLException {
    SmbjClient smbjClient =
        new SmbjClient(
            "testUsername", "testPassword", "testDomain", "smb://test", "testShare", false);
    RSpaceTestUtils.assertObjectSerializable(smbjClient);
  }

  @Test
  public void nullShareNameValueAllowed() throws MalformedURLException {
    SmbjClient smbjClient =
        new SmbjClient("testUsername", "testPassword", "testDomain", "smb://test", null, false);
    assertEquals("", smbjClient.getShareName());
  }

  @Test
  public void sambaClientAuthenticatesWithCorrectUsernameAndDomain() {
    SmbjClient smbjClient =
        new SmbjClient(
            "testUsername", "testPassword", "testDomain", "smb://test", "testShare", false);
    assertEquals(
        "testUsername",
        smbjClient.getAuthContext().getUsername(),
        "samba client should authenticate with provided the username");
    assertEquals(
        "testDomain",
        smbjClient.getAuthContext().getDomain(),
        "samba client should authenticate with provided domain");
  }

  @Test
  public void longServerUrlPathParsedCorrectly() {

    String manchesterURL = "smb://nasr.man.ac.uk/flsrss$/snapped/replicated/Rspace";
    String manchesterShareName = "/flsrss$";

    SmbjClient manchesterClient =
        new SmbjClient(
            "testUsername",
            "testPassword",
            "testDomain",
            manchesterURL,
            manchesterShareName,
            false);
    assertEquals("nasr.man.ac.uk", manchesterClient.getSambaHost());
    assertEquals("flsrss$", manchesterClient.getShareName());
    assertEquals("\\snapped\\replicated\\Rspace", manchesterClient.getAfterShareNamePath());
    assertEquals(
        "snapped\\replicated\\Rspace\\Nigel_Goddard_Test\\small.txt",
        manchesterClient.getRemotePathWithoutShareName("/Nigel_Goddard_Test/small.txt"));

    String pangolinURL = "smb://pangolin.researchspace.com";
    String pangolinShareName = "/samba-folder";

    SmbjClient pangolinClient = new SmbjClient("", "", "", pangolinURL, pangolinShareName, false);
    assertEquals("pangolin.researchspace.com", pangolinClient.getSambaHost());
    assertEquals("samba-folder", pangolinClient.getShareName());
    assertEquals("", pangolinClient.getAfterShareNamePath());
    assertEquals(
        "Fig4.jpg", pangolinClient.getRemotePathWithoutShareName("/samba-folder/Fig4.jpg"));
    assertEquals(
        "another\\Fig3.jpg",
        pangolinClient.getRemotePathWithoutShareName("/samba-folder/another/Fig3.jpg"));
  }
}

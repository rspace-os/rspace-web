package com.researchspace.netfiles.samba;

import static org.junit.Assert.assertEquals;

import com.researchspace.testutils.RSpaceTestUtils;
import java.net.MalformedURLException;
import org.junit.Test;

public class JcifsSmbjClientTest {

  @Test
  public void clientCanBeSerialized() throws MalformedURLException {

    JcifsSmbjClient sc = new JcifsSmbjClient("test", "test", "test", "smb://test", "/samba-folder");
    sc.getConnectionTarget("test");

    RSpaceTestUtils.assertObjectSerializable(sc);
  }

  @Test
  public void longServerUrlPathParsedCorrectly() {

    String manchesterURL = "smb://nasr.man.ac.uk/flsrss$/snapped/replicated/Rspace";
    String manchesterShareName = "/flsrss$";

    JcifsSmbjClient manchesterClient =
        new JcifsSmbjClient("", "", "", manchesterURL, manchesterShareName);
    assertEquals("nasr.man.ac.uk", manchesterClient.getSmbjClient().getSambaHost());
    assertEquals("flsrss$", manchesterClient.getSmbjClient().getShareName());
    assertEquals(
        "\\snapped\\replicated\\Rspace", manchesterClient.getSmbjClient().getAfterShareNamePath());
    assertEquals(
        "snapped\\replicated\\Rspace\\Nigel_Goddard_Test\\small.txt",
        manchesterClient
            .getSmbjClient()
            .getRemotePathWithoutShareName("/Nigel_Goddard_Test/small.txt"));

    String pangolinURL = "smb://pangolin.researchspace.com";
    String pangolinShareName = "/samba-folder";

    JcifsSmbjClient pangolinClient =
        new JcifsSmbjClient("", "", "", pangolinURL, pangolinShareName);
    assertEquals("pangolin.researchspace.com", pangolinClient.getSmbjClient().getSambaHost());
    assertEquals("samba-folder", pangolinClient.getSmbjClient().getShareName());
    assertEquals("", pangolinClient.getSmbjClient().getAfterShareNamePath());
    assertEquals(
        "Fig4.jpg",
        pangolinClient.getSmbjClient().getRemotePathWithoutShareName("/samba-folder/Fig4.jpg"));
    assertEquals(
        "another\\Fig3.jpg",
        pangolinClient
            .getSmbjClient()
            .getRemotePathWithoutShareName("/samba-folder/another/Fig3.jpg"));
  }
}

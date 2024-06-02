package com.researchspace.netfiles;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.UserKeyPair;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.netfiles.samba.JcifsClient;
import com.researchspace.netfiles.samba.SmbjClient;
import com.researchspace.netfiles.sftp.SftpClient;
import org.junit.Before;
import org.junit.Test;

public class NfsFactoryTest {

  private NfsFactory factory = new NfsFactory();

  private String testServerUrl = "sftp://test";
  private String testUsername = "test";
  private String testPassword = "test";
  private UserKeyPair testUserKeyPair;

  private NfsFileSystem testFileSystem;

  @Before
  public void setUp() {
    // dummy key pair
    testUserKeyPair = new UserKeyPair();
    testUserKeyPair.setUser(new User(testUsername));
    testUserKeyPair.setPublicKey("");
    testUserKeyPair.setPrivateKey("");

    testFileSystem = new NfsFileSystem();
  }

  @Test
  public void checkFactoryMethodsForPasswordAuthentication() throws Exception {

    testFileSystem.setUrl(testServerUrl);
    testFileSystem.setAuthType(NfsAuthenticationType.PASSWORD);

    testFileSystem.setClientType(NfsClientType.SAMBA);
    NfsClient sambaClient = factory.getNfsClient(testUsername, testPassword, testFileSystem);
    assertNotNull(sambaClient);
    assertTrue(
        "factory not returning samba client with samba option", sambaClient instanceof JcifsClient);

    testFileSystem.setClientType(NfsClientType.SFTP);
    NfsClient sftpClient = factory.getNfsClient(testUsername, testPassword, testFileSystem);
    assertNotNull(sftpClient);
    assertTrue(
        "factory not returning sftp client with sftp option", sftpClient instanceof SftpClient);

    testFileSystem.setClientType(null);
    assertExceptionThrown(
        () -> factory.getNfsClient(testUsername, testPassword, testFileSystem),
        IllegalStateException.class);
  }

  @Test
  public void checkFactoryMethodsForPubKeyAuthentication() throws Exception {

    testFileSystem.setUrl(testServerUrl);
    testFileSystem.setAuthType(NfsAuthenticationType.PUBKEY);

    // not supported for samba/smbj
    testFileSystem.setClientType(NfsClientType.SAMBA);
    assertExceptionThrown(
        () -> factory.getNfsClient(testUserKeyPair, testFileSystem),
        UnsupportedOperationException.class);
    testFileSystem.setClientType(NfsClientType.SMBJ);
    assertExceptionThrown(
        () -> factory.getNfsClient(testUserKeyPair, testFileSystem),
        UnsupportedOperationException.class);

    // sftp should require proper private key
    testFileSystem.setClientType(NfsClientType.SFTP);
    try {
      factory.getNfsClient(testUserKeyPair, testFileSystem);
      fail("should throw exception about invalid privatekey");
    } catch (IllegalArgumentException e) {
      assertTrue(
          "exception message cause should mention invalid privatekey",
          e.getCause().getMessage().contains("invalid privatekey"));
    }

    testFileSystem.setAuthType(null);
    assertExceptionThrown(
        () -> factory.getNfsClient(testUserKeyPair, testFileSystem), IllegalStateException.class);
  }

  @Test
  public void checkFactoryExpectsProperProperties() throws Exception {

    testFileSystem.setUrl("");
    testFileSystem.setClientType(NfsClientType.SAMBA);
    testFileSystem.setAuthType(NfsAuthenticationType.PASSWORD);
    assertExceptionThrown(
        () -> factory.getNfsClient(testUsername, testPassword, testFileSystem),
        IllegalStateException.class,
        containsString("url"));

    testFileSystem.setUrl(testServerUrl);
    testFileSystem.setClientType(null);
    testFileSystem.setAuthType(NfsAuthenticationType.PASSWORD);
    assertExceptionThrown(
        () -> factory.getNfsClient(testUsername, testPassword, testFileSystem),
        IllegalStateException.class,
        containsString("client"));

    testFileSystem.setUrl(testServerUrl);
    testFileSystem.setClientType(NfsClientType.SFTP);
    testFileSystem.setAuthType(null);
    assertExceptionThrown(
        () -> factory.getNfsClient(testUsername, testPassword, testFileSystem),
        IllegalStateException.class,
        containsString("auth"));
  }

  @Test
  public void creatingSmbjClient() {
    testFileSystem.setUrl("smb://test.url");
    testFileSystem.setClientType(NfsClientType.SMBJ);
    testFileSystem.setClientOptions("SAMBA_DOMAIN=WORKGROUP\nSAMBA_SHARE_NAME=testShare");
    testFileSystem.setAuthType(NfsAuthenticationType.PASSWORD);

    SmbjClient smbjClient =
        (SmbjClient) factory.getNfsClient(testUsername, testPassword, testFileSystem);
    assertNotNull(smbjClient);
    assertEquals("test.url", smbjClient.getSambaHost());
    assertEquals("testShare", smbjClient.getShareName());
  }
}

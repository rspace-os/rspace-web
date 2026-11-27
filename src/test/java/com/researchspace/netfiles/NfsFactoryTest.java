package com.researchspace.netfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.researchspace.model.User;
import com.researchspace.model.UserKeyPair;
import com.researchspace.model.netfiles.LeadingSlashes;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemOption;
import com.researchspace.netfiles.s3.S3NfsClient;
import com.researchspace.netfiles.samba.JcifsClient;
import com.researchspace.netfiles.samba.SmbjClient;
import com.researchspace.netfiles.sftp.SftpClient;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesFactory;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

public class NfsFactoryTest {

  private NfsFactory factory = new NfsFactory();

  private S3UtilitiesFactory s3UtilitiesFactoryMock = Mockito.mock(S3UtilitiesFactory.class);

  private String testServerUrl = "sftp://test";
  private String testUsername = "test";
  private String testPassword = "test";
  private UserKeyPair testUserKeyPair;

  private NfsFileSystem testFileSystem;

  @BeforeEach
  public void setUp() {
    factory.setS3UtilitiesFactory(s3UtilitiesFactoryMock);

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
        sambaClient instanceof JcifsClient, "factory not returning samba client with samba option");

    testFileSystem.setClientType(NfsClientType.SFTP);
    NfsClient sftpClient = factory.getNfsClient(testUsername, testPassword, testFileSystem);
    assertNotNull(sftpClient);
    assertTrue(
        sftpClient instanceof SftpClient, "factory not returning sftp client with sftp option");

    testFileSystem.setClientType(null);
    assertThrows(
        IllegalStateException.class,
        () -> factory.getNfsClient(testUsername, testPassword, testFileSystem));
  }

  @Test
  public void checkFactoryMethodsForPubKeyAuthentication() throws Exception {

    testFileSystem.setUrl(testServerUrl);
    testFileSystem.setAuthType(NfsAuthenticationType.PUBKEY);

    // not supported for samba/smbj
    testFileSystem.setClientType(NfsClientType.SAMBA);
    assertThrows(
        UnsupportedOperationException.class,
        () -> factory.getNfsClient(testUserKeyPair, testFileSystem));
    testFileSystem.setClientType(NfsClientType.SMBJ);
    assertThrows(
        UnsupportedOperationException.class,
        () -> factory.getNfsClient(testUserKeyPair, testFileSystem));

    // sftp should require proper private key
    testFileSystem.setClientType(NfsClientType.SFTP);
    try {
      factory.getNfsClient(testUserKeyPair, testFileSystem);
      fail("should throw exception about invalid privatekey");
    } catch (IllegalArgumentException e) {
      assertThat(e.getCause().getMessage())
          .as("exception message cause should mention invalid privatekey")
          .contains("invalid privatekey");
    }

    testFileSystem.setAuthType(null);
    assertThrows(
        IllegalStateException.class, () -> factory.getNfsClient(testUserKeyPair, testFileSystem));
  }

  @Test
  public void checkFactoryExpectsProperProperties() throws Exception {

    testFileSystem.setUrl("");
    testFileSystem.setClientType(NfsClientType.SAMBA);
    testFileSystem.setAuthType(NfsAuthenticationType.PASSWORD);
    var exception1 =
        assertThrows(
            IllegalStateException.class,
            () -> factory.getNfsClient(testUsername, testPassword, testFileSystem));
    assertThat(exception1.getMessage()).contains("url");

    testFileSystem.setUrl(testServerUrl);
    testFileSystem.setClientType(null);
    testFileSystem.setAuthType(NfsAuthenticationType.PASSWORD);
    var exception2 =
        assertThrows(
            IllegalStateException.class,
            () -> factory.getNfsClient(testUsername, testPassword, testFileSystem));
    assertThat(exception2.getMessage()).contains("client");

    testFileSystem.setUrl(testServerUrl);
    testFileSystem.setClientType(NfsClientType.SFTP);
    testFileSystem.setAuthType(null);
    var exception3 =
        assertThrows(
            IllegalStateException.class,
            () -> factory.getNfsClient(testUsername, testPassword, testFileSystem));
    assertThat(exception3.getMessage()).contains("auth");
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

  @Test
  public void creatingS3NfsClient() {
    testFileSystem.setClientType(NfsClientType.S3);
    testFileSystem.setAuthType(NfsAuthenticationType.NONE);
    testFileSystem.setClientOptions("S3_REGION=testRegion\nS3_BUCKET_NAME=testBucket");

    S3Utilities mockS3Utilities = Mockito.mock(S3Utilities.class);
    Mockito.when(s3UtilitiesFactoryMock.createS3UtilitiesForNfsConnector(testFileSystem))
        .thenReturn(mockS3Utilities);

    S3NfsClient s3NfsClient =
        (S3NfsClient) factory.getNfsClient(testUsername, null, testFileSystem);
    assertNotNull(s3NfsClient);
    assertEquals(testUsername, s3NfsClient.getUsername());
    Mockito.verify(s3UtilitiesFactoryMock).createS3UtilitiesForNfsConnector(testFileSystem);
  }

  @ParameterizedTest
  @CsvSource({
    "smb://host, report.txt, NONE ONE MORE",
    "smb://host/share, /report.txt, NONE ONE MORE",
    "smb://host/share/, //report.txt, NONE ONE MORE",
    "smb://host/share/project/, report.txt, NONE ONE MORE",
    // the folder after the share is glued to a path without a separator
    "smb://host/share/project, report.txt, NONE",
    "smb://host/share/project, /report.txt, ONE MORE",
    "smb://host/share/project, //report.txt, ONE MORE",
    // a first folder named like the share is read as the share, unless two slashes precede it
    "smb://host, /share/project/report.txt, NONE ONE",
    "smb://host/share, share/project/report.txt, NONE ONE",
    "smb://host/share/, //share/project/report.txt, MORE",
    "smb://host, //share/project/report.txt, MORE"
  })
  public void smbjMatchesTheSpellingsItOpensAsOneFile(
      String url, String path, String expectedSpellings) {
    testFileSystem.setClientType(NfsClientType.SMBJ);
    testFileSystem.setClientOption(NfsFileSystemOption.SAMBA_SHARE_NAME, "share");
    testFileSystem.setUrl(url);

    assertEquals(
        spellings(expectedSpellings), factory.spellingsOfTheSameFile(testFileSystem, path));
  }

  private static Set<LeadingSlashes> spellings(String names) {
    return Arrays.stream(names.split(" "))
        .map(LeadingSlashes::valueOf)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(LeadingSlashes.class)));
  }

  @Test
  public void jcifsOpensEverySpellingAsOneFileUnlessItHandsDownloadsToSmbj() {
    testFileSystem.setClientType(NfsClientType.SAMBA);
    testFileSystem.setUrl("smb://host/share/project");
    assertEquals(
        spellings("NONE ONE MORE"),
        factory.spellingsOfTheSameFile(testFileSystem, "share/report.txt"));

    ReflectionTestUtils.setField(factory, "smbjDownload", true);
    ReflectionTestUtils.setField(factory, "smbjShareName", "share");
    assertEquals(spellings("NONE"), factory.spellingsOfTheSameFile(testFileSystem, "report.txt"));
  }

  @ParameterizedTest
  @CsvSource({
    "report.txt, NONE",
    "/report.txt, ONE MORE",
    // a path starting with the home is read as it is, unless two slashes precede it
    "/tempZone/home/alice/report.txt, ONE",
    "//tempZone/home/alice/report.txt, MORE"
  })
  public void iRodsMatchesTheSpellingsItOpensAsOneObject(String path, String expectedSpellings) {
    testFileSystem.setClientType(NfsClientType.IRODS);
    testFileSystem.setUrl("irods.example.com");
    testFileSystem.setClientOption(NfsFileSystemOption.IRODS_HOME_DIR, "/tempZone/home/alice");

    assertEquals(
        spellings(expectedSpellings), factory.spellingsOfTheSameFile(testFileSystem, path));
  }

  @Test
  public void elsewhereAPathWithALeadingSlashIsReadApartFromOneWithout() {
    testFileSystem.setClientType(NfsClientType.SFTP);
    testFileSystem.setUrl("sftp://host");
    assertEquals(spellings("NONE"), factory.spellingsOfTheSameFile(testFileSystem, "report.txt"));
    assertEquals(
        spellings("ONE MORE"), factory.spellingsOfTheSameFile(testFileSystem, "//report.txt"));
  }
}

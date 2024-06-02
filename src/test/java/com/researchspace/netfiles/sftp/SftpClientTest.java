package com.researchspace.netfiles.sftp;

import static org.junit.Assert.assertEquals;

import com.researchspace.netfiles.NfsException;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SftpClientTest {

  private SftpClient sftpClient;

  private String testUsername = "testSftpUsername";

  @Before
  public void setUp() {
    sftpClient = new SftpClient(testUsername, "test", "sftp://test", "");
  }

  @After
  public void tearDown() {
    if (sftpClient != null) {
      sftpClient.closeSession();
    }
  }

  @Test
  public void sftpClientCanBeSerialized() {
    RSpaceTestUtils.assertObjectSerializable(sftpClient);
  }

  @Test
  public void testTargetPathIsProperlySanitised() {
    assertEquals(".", sftpClient.sanitiseLsPath(""));
    assertEquals("/", sftpClient.sanitiseLsPath("/"));
    assertEquals("/test", sftpClient.sanitiseLsPath("//test"));

    // this should be blocked too:
    // assertEquals("testFolder", sftpClient.sanitisePath("testFolder/.."));
  }

  @Test
  public void testUserFolderPathIsProperlyConcatenated() {
    assertEquals("testFile", sftpClient.getCanonicalPathToTarget("testFile", "."));
    assertEquals("test/testFile", sftpClient.getCanonicalPathToTarget("testFile", "test"));
    assertEquals(
        "test/test2/testFile", sftpClient.getCanonicalPathToTarget("testFile", "test/test2/"));
  }

  @Test
  public void testFileNameIsProperlyTakenFromPath() {
    assertEquals("testFile.txt", sftpClient.getFileNameFromFullPath("csce/testFile.txt"));
    assertEquals("testFile.txt", sftpClient.getFileNameFromFullPath("/csce/testFile.txt"));
  }

  @Test
  public void testUserLoggedIn() {
    sftpClient.isUserLoggedIn();
  }

  @Test
  public void sftpClientKnowsCorrectUsername() {
    assertEquals("client should know the username", testUsername, sftpClient.getUsername());
  }

  // @Test
  public void testConnectionTested() throws NfsException {
    sftpClient.tryConnectAndReadTarget(null);
  }

  // @Test
  public void testTreeCreated() throws IOException {
    sftpClient.createFileTree(null, null, null);
  }

  // @Test
  public void testFileDetailsForDownloadCorrect() throws IOException {
    sftpClient.queryNfsFileForDownload(null);
  }
}

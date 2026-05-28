package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileSystem;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Test;

public class FilestoreAclCheckerTest {

  private FilestoreAclChecker checker;

  @Before
  public void setUp() {
    checker = new FilestoreAclChecker();
    checker.setMessages(mock(MessageSourceUtils.class));
  }

  // --- parseList ---

  @Test
  public void parseList_nullOrEmpty_returnsEmpty() {
    assertTrue(FilestoreAclChecker.parseList(null).isEmpty());
    assertTrue(FilestoreAclChecker.parseList("").isEmpty());
    assertTrue(FilestoreAclChecker.parseList("   ").isEmpty());
  }

  @Test
  public void parseList_singleToken() {
    Set<String> tokens = FilestoreAclChecker.parseList("alice");
    assertEquals(1, tokens.size());
    assertTrue(tokens.contains("alice"));
  }

  @Test
  public void parseList_multipleTokensTrimsWhitespace() {
    Set<String> tokens = FilestoreAclChecker.parseList("  alice ,  bob  ");
    assertEquals(2, tokens.size());
    assertTrue(tokens.contains("alice"));
    assertTrue(tokens.contains("bob"));
  }

  @Test
  public void parseList_dropsEmptyTokens() {
    Set<String> tokens = FilestoreAclChecker.parseList("alice,,bob,");
    assertEquals(2, tokens.size());
    assertTrue(tokens.contains("alice"));
    assertTrue(tokens.contains("bob"));
  }

  @Test
  public void parseList_everyoneSentinel() {
    Set<String> tokens = FilestoreAclChecker.parseList("*");
    assertEquals(1, tokens.size());
    assertTrue(tokens.contains("*"));
  }

  // --- canRead / canWrite, authType=NONE ---

  @Test
  public void canRead_emptyWhitelists_deniesEveryone() {
    NfsFileSystem fs = s3FileSystem(null, null);
    assertFalse(checker.canRead(user("alice"), fs));
    assertFalse(checker.canRead(user("bob"), fs));
  }

  @Test
  public void canRead_everyoneSentinel_grantsAll() {
    NfsFileSystem fs = s3FileSystem("*", null);
    assertTrue(checker.canRead(user("alice"), fs));
    assertTrue(checker.canRead(user("anyone"), fs));
  }

  @Test
  public void canRead_namedList_grantsOnlyListedUsers() {
    NfsFileSystem fs = s3FileSystem("alice,bob", null);
    assertTrue(checker.canRead(user("alice"), fs));
    assertTrue(checker.canRead(user("bob"), fs));
    assertFalse(checker.canRead(user("carol"), fs));
  }

  @Test
  public void canRead_writeImpliesRead_namedWriter() {
    NfsFileSystem fs = s3FileSystem(null, "alice");
    assertTrue(checker.canRead(user("alice"), fs));
    assertFalse(checker.canRead(user("bob"), fs));
  }

  @Test
  public void canRead_writeImpliesRead_everyoneWrite() {
    NfsFileSystem fs = s3FileSystem(null, "*");
    assertTrue(checker.canRead(user("anyone"), fs));
  }

  @Test
  public void canRead_isCaseSensitive() {
    NfsFileSystem fs = s3FileSystem("Alice", null);
    assertTrue(checker.canRead(user("Alice"), fs));
    assertFalse(checker.canRead(user("alice"), fs));
  }

  @Test
  public void canWrite_emptyWriteList_deniesEveryoneEvenIfRead() {
    NfsFileSystem fs = s3FileSystem("*", null);
    assertFalse(checker.canWrite(user("alice"), fs));
  }

  @Test
  public void canWrite_everyoneSentinel_grantsAll() {
    NfsFileSystem fs = s3FileSystem(null, "*");
    assertTrue(checker.canWrite(user("alice"), fs));
  }

  @Test
  public void canWrite_namedList_grantsOnlyListedUsers() {
    NfsFileSystem fs = s3FileSystem(null, "alice");
    assertTrue(checker.canWrite(user("alice"), fs));
    assertFalse(checker.canWrite(user("bob"), fs));
  }

  // --- authType gate ---

  @Test
  public void canRead_nonNoneAuthType_shortCircuitsTrue_evenIfWhitelistsEmpty() {
    NfsFileSystem fs = perUserAuthFileSystem(NfsClientType.SAMBA);
    assertTrue(checker.canRead(user("alice"), fs));
  }

  @Test
  public void canWrite_nonNoneAuthType_shortCircuitsTrue_evenIfWhitelistsEmpty() {
    NfsFileSystem fs = perUserAuthFileSystem(NfsClientType.IRODS);
    assertTrue(checker.canWrite(user("alice"), fs));
  }

  @Test
  public void canRead_nullFilesystem_deniesWithoutNpe() {
    // a stale filestore binding might return null from getFileSystem(); treat as no access
    assertFalse(checker.canRead(user("alice"), null));
    assertFalse(checker.canWrite(user("alice"), null));
  }

  @Test
  public void canRead_nullAuthType_denies() {
    // a misconfigured row with no auth type should not bypass the ACL
    NfsFileSystem fs = new NfsFileSystem();
    fs.setClientType(NfsClientType.S3);
    fs.setAuthType(null);
    fs.setReadWhitelist("*");
    fs.setWriteWhitelist("*");
    assertFalse(checker.canRead(user("alice"), fs));
  }

  @Test
  public void canWrite_nullAuthType_denies() {
    NfsFileSystem fs = new NfsFileSystem();
    fs.setClientType(NfsClientType.S3);
    fs.setAuthType(null);
    fs.setWriteWhitelist("*");
    assertFalse(checker.canWrite(user("alice"), fs));
  }

  @Test
  public void canRead_authTypeNoneOnNonS3Backend_stillEnforced() {
    // hypothetical: Samba configured with authType=NONE should still be gated
    NfsFileSystem fs = new NfsFileSystem();
    fs.setClientType(NfsClientType.SAMBA);
    fs.setAuthType(NfsAuthenticationType.NONE);
    fs.setReadWhitelist("alice");
    assertTrue(checker.canRead(user("alice"), fs));
    assertFalse(checker.canRead(user("bob"), fs));
  }

  // --- assert variants ---

  @Test
  public void assertCanRead_authorized_doesNotThrow() {
    NfsFileSystem fs = s3FileSystem("alice", null);
    checker.assertCanRead(user("alice"), fs);
  }

  @Test
  public void assertCanRead_unauthorized_throws() {
    NfsFileSystem fs = s3FileSystem("alice", null);
    try {
      checker.assertCanRead(user("bob"), fs);
      fail("expected AuthorizationException");
    } catch (AuthorizationException expected) {
      // ok
    }
  }

  @Test
  public void assertCanWrite_unauthorized_throws() {
    NfsFileSystem fs = s3FileSystem(null, "alice");
    try {
      checker.assertCanWrite(user("bob"), fs);
      fail("expected AuthorizationException");
    } catch (AuthorizationException expected) {
      // ok
    }
  }

  // --- helpers ---

  private static User user(String username) {
    return new User(username);
  }

  private static NfsFileSystem s3FileSystem(String readWhitelist, String writeWhitelist) {
    NfsFileSystem fs = new NfsFileSystem();
    fs.setClientType(NfsClientType.S3);
    fs.setAuthType(NfsAuthenticationType.NONE);
    fs.setReadWhitelist(readWhitelist);
    fs.setWriteWhitelist(writeWhitelist);
    return fs;
  }

  private static NfsFileSystem perUserAuthFileSystem(NfsClientType clientType) {
    NfsFileSystem fs = new NfsFileSystem();
    fs.setClientType(clientType);
    fs.setAuthType(NfsAuthenticationType.PASSWORD);
    return fs;
  }
}

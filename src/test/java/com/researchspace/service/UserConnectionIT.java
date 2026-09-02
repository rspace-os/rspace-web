package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserConnectionIT extends RealTransactionSpringTestBase {

  private @Autowired UserConnectionManager uConnMgr;

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void replaceConnectionLeavesExactlyOneDecryptableRow() {
    User aUser = createInitAndLoginAnyUser();

    UserConnection original = TestFactory.createUserConnection(aUser.getUsername());
    String providerId = original.getId().getProviderId();
    uConnMgr.save(original);

    // reconnecting under the SAME provider user id must not trip the unique index: the bulk
    // delete has to reach the database before the insert is flushed
    UserConnection sameId = TestFactory.createUserConnection(aUser.getUsername());
    sameId.setAccessToken("replacement-token-same-id");
    uConnMgr.replaceConnection(sameId);

    UserConnection fetched =
        uConnMgr.findByUserNameProviderName(aUser.getUsername(), providerId).get();
    assertEquals("replacement-token-same-id", fetched.getAccessToken());

    // reconnecting under a DIFFERENT provider user id (a new OMERO username) must replace the
    // old row, not add a second one, or findByUserNameProviderName throws NonUniqueResult
    UserConnection differentId = TestFactory.createUserConnection(aUser.getUsername());
    differentId.getId().setProviderUserId("another-provider-user");
    differentId.setAccessToken("replacement-token-different-id");
    uConnMgr.replaceConnection(differentId);

    assertEquals(
        1, uConnMgr.findListByUserNameProviderName(aUser.getUsername(), providerId).size());
    // single-row lookup still works and the surviving row decrypts to the latest token
    fetched = uConnMgr.findByUserNameProviderName(aUser.getUsername(), providerId).get();
    assertEquals("replacement-token-different-id", fetched.getAccessToken());
    assertEquals("another-provider-user", fetched.getId().getProviderUserId());
  }

  @Test
  public void testEncryptionDecryptionOfTokens() {
    User aUser = createInitAndLoginAnyUser();

    UserConnection conn = TestFactory.createUserConnection(aUser.getUsername());
    String originalAccess = conn.getAccessToken();
    conn = uConnMgr.save(conn);
    // this is marked as encrypted once saved.
    assertTrue(conn.isEncrypted());
    assertTrue(conn.isTransientlyEncrypted());
    // and we can get the original token back...
    conn =
        uConnMgr
            .findByUserNameProviderName(aUser.getUsername(), conn.getId().getProviderId())
            .get();
    assertEquals(originalAccess, conn.getAccessToken());
    assertTrue(conn.isEncrypted());
    assertFalse(conn.isTransientlyEncrypted());

    // it's  encrypted/
    conn = uConnMgr.save(conn);
    assertTrue(conn.isEncrypted());
    assertTrue(conn.isTransientlyEncrypted());
    // and we can get the original token back...
    conn =
        uConnMgr
            .findByUserNameProviderName(aUser.getUsername(), conn.getId().getProviderId())
            .get();
    assertEquals(originalAccess, conn.getAccessToken());
    assertTrue(conn.isEncrypted());
    assertFalse(conn.isTransientlyEncrypted());
  }
}

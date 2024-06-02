package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserConnectionIT extends RealTransactionSpringTestBase {

  private @Autowired UserConnectionManager uConnMgr;

  @Before
  public void setup() throws Exception {
    super.setUp();
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

package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.researchspace.core.util.CryptoUtils;
import com.researchspace.model.User;
import com.researchspace.model.UserApiKey;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserApiKeyManagerTest extends SpringTransactionalTest {
  private static final String KEYSTRINGATLEAST16CHARS = "keystringatleast16chars";
  private static final String CLEAR_16CHARS_KEY = "keystring16chars";
  private static final String KEYSTRING2_ATLEAST16CHARS = "keystring2atleast16chars";
  private @Autowired UserApiKeyManager apiMgr;
  User anyUser;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    anyUser = TestFactory.createAnyUser("any");
    anyUser = userMgr.save(anyUser);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void basicOperations() {
    Date now = new Date();
    final int initialCount = apiMgr.getAll().size();
    UserApiKey key = new UserApiKey(anyUser, KEYSTRINGATLEAST16CHARS);
    assertNull(key.getId());

    key = apiMgr.save(key);
    assertNotNull(key.getId());
    assertNotNull(key.getCreated());
    assertFalse(key.getCreated().before(now));
    assertEquals(initialCount + 1, apiMgr.getAll().size());

    apiMgr.remove(key.getId());
    assertEquals(initialCount, apiMgr.getAll().size());
  }

  @Test
  public void findByKey() {
    String keyStr = CLEAR_16CHARS_KEY;
    UserApiKey apiKeySaved = new UserApiKey(anyUser, keyStr);
    apiKeySaved.setApiKey(CryptoUtils.encodeBCrypt(keyStr));
    apiMgr.save(apiKeySaved);
    assertEquals(anyUser, apiMgr.findUserByKey(keyStr).get());
  }

  @Test
  public void createKey() {
    UserApiKey userApiKey = apiMgr.createKeyForUser(anyUser);
    String keyStr1 = userApiKey.getApiKey();
    assertNotNull(userApiKey);
    assertEquals(32, userApiKey.getApiKey().length());
    assertEquals(anyUser, apiMgr.findUserByKey(keyStr1).get());

    // test can just create another, overwriting the original.
    userApiKey = apiMgr.createKeyForUser(anyUser);
    String keyStr2 = userApiKey.getApiKey();
    assertNotEquals(keyStr2, keyStr1);
    assertNull(apiMgr.findUserByKey(keyStr1).orElse(null));
    assertEquals(anyUser, apiMgr.findUserByKey(keyStr2).get());
  }

  @Test
  public void deleteKey() {
    assertEquals(0, apiMgr.revokeKeyForUser(anyUser));
    UserApiKey key = apiMgr.createKeyForUser(anyUser);
    assertEquals(1, apiMgr.revokeKeyForUser(anyUser));
  }

  @Test
  public void resetKey() {
    UserApiKey key = apiMgr.createKeyForUser(anyUser);
    key.setApiKey(KEYSTRING2_ATLEAST16CHARS);
    assertEquals(1, apiMgr.revokeKeyForUser(anyUser));
    key = apiMgr.save(key);
    assertEquals(KEYSTRING2_ATLEAST16CHARS, key.getApiKey());
  }
}

package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.researchspace.model.User;
import com.researchspace.model.UserKeyPair;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserKeyManagerTest extends SpringTransactionalTest {

  @Autowired private UserKeyManager userKeyManager;

  @Test
  public void testRegisterNewKeyForUser() {

    User testUser = createAndSaveUserIfNotExists("ukmTestUser");
    assertNotNull(testUser);

    UserKeyPair keyOfNewUser = userKeyManager.getUserKeyPair(testUser);
    assertNull("new user shouldn't have a key", keyOfNewUser);

    UserKeyPair createdKey = userKeyManager.createNewUserKeyPair(testUser);
    assertNotNull("created key shouldn't be null", createdKey);

    UserKeyPair retrievedKey = userKeyManager.getUserKeyPair(testUser);
    assertNotNull("key retrieved after creating shouldn't be null", retrievedKey);
  }

  @Test
  public void testUpdateKeyForUser() {

    User testUser = createAndSaveUserIfNotExists("ukmTestUser2");
    assertNotNull(testUser);

    UserKeyPair createdKey = userKeyManager.createNewUserKeyPair(testUser);
    assertNotNull("created key shouldn't be null", createdKey);
    Long createdId = createdKey.getId();
    String createdPrivKey = createdKey.getPrivateKey();
    String createdPubKey = createdKey.getPublicKey();

    UserKeyPair updatedKey = userKeyManager.createNewUserKeyPair(testUser);
    assertNotNull("updated key shouldn't be null", updatedKey);
    Long updatedId = updatedKey.getId();
    String updatedPrivKey = updatedKey.getPrivateKey();
    String updatedPubKey = updatedKey.getPublicKey();

    assertEquals("updated key should override first key", createdId, updatedId);
    assertNotEquals("updated key private part should differ", createdPrivKey, updatedPrivKey);
    assertNotEquals("updated key public part should differ", createdPubKey, updatedPubKey);

    UserKeyPair retrievedKey = userKeyManager.getUserKeyPair(testUser);
    assertNotNull("key retrieved after update shouldn't be null", retrievedKey);
    assertEquals("retrieved key should have same id as updated", updatedId, retrievedKey.getId());
    assertEquals(
        "retrieved key should have same priv key as updated",
        updatedPrivKey,
        retrievedKey.getPrivateKey());
    assertEquals(
        "retrieved key should have same pub key as updated",
        updatedPubKey,
        retrievedKey.getPublicKey());
  }
}

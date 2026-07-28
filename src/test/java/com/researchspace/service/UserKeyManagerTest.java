package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.model.User;
import com.researchspace.model.UserKeyPair;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserKeyManagerTest extends SpringTransactionalTest {

  @Autowired private UserKeyManager userKeyManager;

  @Test
  public void testRegisterNewKeyForUser() {

    User testUser = createAndSaveUserIfNotExists("ukmTestUser");
    assertNotNull(testUser);

    UserKeyPair keyOfNewUser = userKeyManager.getUserKeyPair(testUser);
    assertNull(keyOfNewUser, "new user shouldn't have a key");

    UserKeyPair createdKey = userKeyManager.createNewUserKeyPair(testUser);
    assertNotNull(createdKey, "created key shouldn't be null");

    UserKeyPair retrievedKey = userKeyManager.getUserKeyPair(testUser);
    assertNotNull(retrievedKey, "key retrieved after creating shouldn't be null");
  }

  @Test
  public void testUpdateKeyForUser() {

    User testUser = createAndSaveUserIfNotExists("ukmTestUser2");
    assertNotNull(testUser);

    UserKeyPair createdKey = userKeyManager.createNewUserKeyPair(testUser);
    assertNotNull(createdKey, "created key shouldn't be null");
    Long createdId = createdKey.getId();
    String createdPrivKey = createdKey.getPrivateKey();
    String createdPubKey = createdKey.getPublicKey();

    UserKeyPair updatedKey = userKeyManager.createNewUserKeyPair(testUser);
    assertNotNull(updatedKey, "updated key shouldn't be null");
    Long updatedId = updatedKey.getId();
    String updatedPrivKey = updatedKey.getPrivateKey();
    String updatedPubKey = updatedKey.getPublicKey();

    assertEquals(createdId, updatedId, "updated key should override first key");
    assertNotEquals(createdPrivKey, updatedPrivKey, "updated key private part should differ");
    assertNotEquals(createdPubKey, updatedPubKey, "updated key public part should differ");

    UserKeyPair retrievedKey = userKeyManager.getUserKeyPair(testUser);
    assertNotNull(retrievedKey, "key retrieved after update shouldn't be null");
    assertEquals(updatedId, retrievedKey.getId(), "retrieved key should have same id as updated");
    assertEquals(
        updatedPrivKey,
        retrievedKey.getPrivateKey(),
        "retrieved key should have same priv key as updated");
    assertEquals(
        updatedPubKey,
        retrievedKey.getPublicKey(),
        "retrieved key should have same pub key as updated");
  }
}

package com.researchspace.service.impl;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.researchspace.dao.UserKeyDao;
import com.researchspace.model.User;
import com.researchspace.model.UserKeyPair;
import com.researchspace.service.UserKeyManager;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service("userKeyManager")
public class UserKeyManagerImpl implements UserKeyManager {

  /** comment added at the end of public key */
  private static final String PUBKEY_COMMENT = "rspace";

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Value("${netfilestores.auth.pubKey.passphrase}")
  private String passphrase;

  @Autowired private UserKeyDao userKeyDao;

  @Override
  public UserKeyPair getUserKeyPair(User user) {
    return userKeyDao.getUserKeyPair(user);
  }

  @Override
  public UserKeyPair createNewUserKeyPair(User user) {
    UserKeyPair newKeyPair = generateNewKeyPair();
    newKeyPair.setUser(user);

    UserKeyPair currentKeyPair = getUserKeyPair(user);
    if (currentKeyPair != null) {
      currentKeyPair.setPrivateKey(newKeyPair.getPrivateKey());
      currentKeyPair.setPublicKey(newKeyPair.getPublicKey());
      return userKeyDao.save(currentKeyPair);
    }
    return userKeyDao.save(newKeyPair);
  }

  private UserKeyPair generateNewKeyPair() {

    JSch jschClient = new JSch();

    try {
      KeyPair keyPair = KeyPair.genKeyPair(jschClient, KeyPair.RSA);
      ByteArrayOutputStream privKeyBaos = new ByteArrayOutputStream();
      keyPair.writePrivateKey(privKeyBaos, passphrase.getBytes(StandardCharsets.UTF_8));

      ByteArrayOutputStream pubKeyBaos = new ByteArrayOutputStream();
      keyPair.writePublicKey(pubKeyBaos, PUBKEY_COMMENT);

      String privateKey = privKeyBaos.toString("UTF-8");
      String publicKey = pubKeyBaos.toString("UTF-8");

      UserKeyPair userKeyPair = new UserKeyPair();
      userKeyPair.setPrivateKey(privateKey);
      userKeyPair.setPublicKey(publicKey);

      log.info("new ssh key generated: " + userKeyPair.toString());

      return userKeyPair;

    } catch (JSchException | UnsupportedEncodingException e) {
      throw new IllegalArgumentException("problem with generating ssh key", e);
    }
  }

  @Override
  public void removeUserKeyPair(User user) {
    UserKeyPair userKeyPair = getUserKeyPair(user);
    if (userKeyPair != null) {
      userKeyDao.remove(userKeyPair.getId());
    }
  }
}

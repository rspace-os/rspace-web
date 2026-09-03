package com.researchspace.netfiles;

import com.researchspace.model.User;
import com.researchspace.model.UserKeyPair;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.service.UserKeyManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Component;

@Component
public class NfsPublicKeyAuthentication implements NfsAuthentication {

  @Autowired private NfsFactory nfsClientFactory;

  @Autowired private UserKeyManager userKeyManager;

  @Override
  public DefaultMessageSourceResolvable validateCredentials(
      String nfsusername, String nfspassword, User user) {
    UserKeyPair userKey = getKeyForUser(user);
    if (userKey == null) {
      return new DefaultMessageSourceResolvable("netFileStores.validation.noKey");
    }
    return null;
  }

  @Override
  public NfsClient login(
      String nfsusername, String nfspassword, NfsFileSystem fileSystem, User user) {

    UserKeyPair userKey = getKeyForUser(user);
    return nfsClientFactory.getNfsClient(userKey, fileSystem);
  }

  @Override
  public DefaultMessageSourceResolvable getMessageForAuthException(NfsAuthException auth) {
    return new DefaultMessageSourceResolvable("netFileStores.errors.authPublicKey");
  }

  private UserKeyPair getKeyForUser(User user) {
    return userKeyManager.getUserKeyPair(user);
  }
}

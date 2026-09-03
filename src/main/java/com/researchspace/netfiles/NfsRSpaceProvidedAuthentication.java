package com.researchspace.netfiles;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Component;

@Component
public class NfsRSpaceProvidedAuthentication implements NfsAuthentication {

  @Autowired private NfsFactory nfsClientFactory;

  @Override
  public DefaultMessageSourceResolvable validateCredentials(
      String nfsusername, String nfspassword, User user) {
    return null; // accept everyone
  }

  @Override
  public NfsClient login(
      String nfsusername, String nfspassword, NfsFileSystem fileSystem, User user) {
    return nfsClientFactory.getNfsClient(nfsusername, nfspassword, fileSystem);
  }

  @Override
  public DefaultMessageSourceResolvable getMessageForAuthException(NfsAuthException auth) {
    return new DefaultMessageSourceResolvable("netFileStores.errors.connection");
  }
}

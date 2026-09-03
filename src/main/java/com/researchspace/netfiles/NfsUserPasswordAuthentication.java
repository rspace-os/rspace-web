package com.researchspace.netfiles;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileSystem;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Component;

@Component
public class NfsUserPasswordAuthentication implements NfsAuthentication {

  @Autowired private NfsFactory nfsClientFactory;

  @Override
  public DefaultMessageSourceResolvable validateCredentials(
      String nfsusername, String nfspassword, User user) {
    if (nfsusername == null || nfsusername.trim().isEmpty()) {
      return new DefaultMessageSourceResolvable("netFileStores.validation.noUsername");
    }
    if (StringUtils.isEmpty(nfspassword)) {
      return new DefaultMessageSourceResolvable("netFileStores.validation.noPassword");
    }
    return null;
  }

  @Override
  public NfsClient login(
      String nfsusername, String nfspassword, NfsFileSystem fileSystem, User user) {
    return nfsClientFactory.getNfsClient(nfsusername, nfspassword, fileSystem);
  }

  @Override
  public DefaultMessageSourceResolvable getMessageForAuthException(NfsAuthException auth) {
    return new DefaultMessageSourceResolvable("workspace:export.fileStore.login.authProblem");
  }
}

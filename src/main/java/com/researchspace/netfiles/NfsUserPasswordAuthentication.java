package com.researchspace.netfiles;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileSystem;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NfsUserPasswordAuthentication implements NfsAuthentication {

  @Autowired private NfsFactory nfsClientFactory;

  @Override
  public String validateCredentials(String nfsusername, String nfspassword, User user) {
    if (nfsusername == null || nfsusername.trim().length() == 0) {
      return "net.filestores.validation.no.username";
    }
    if (StringUtils.isEmpty(nfspassword)) {
      return "net.filestores.validation.no.password";
    }
    return null;
  }

  @Override
  public NfsClient login(
      String nfsusername, String nfspassword, NfsFileSystem fileSystem, User user) {
    return nfsClientFactory.getNfsClient(nfsusername, nfspassword, fileSystem);
  }

  @Override
  public String getMessageCodeForAuthException(NfsAuthException auth) {
    return "net.filestores.error.auth.password";
  }
}

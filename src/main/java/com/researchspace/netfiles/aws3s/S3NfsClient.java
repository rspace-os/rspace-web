package com.researchspace.netfiles.aws3s;

import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsAbstractClient;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsException;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsTarget;
import java.io.IOException;
import java.net.MalformedURLException;

public class S3NfsClient extends NfsAbstractClient implements NfsClient {

  public S3NfsClient() {
    super("defaultS3ConnectionUser");
  }

  @Override
  public boolean isUserLoggedIn() {
    return false;
  }

  @Override
  public void tryConnectAndReadTarget(String target) throws NfsException, MalformedURLException {}

  @Override
  public NfsFileTreeNode createFileTree(
      String target, String nfsorder, NfsFileStore selectedUserFolder) throws IOException {
    return null;
  }

  @Override
  public NfsFileDetails queryForNfsFile(NfsTarget nfsTarget) {
    return null;
  }

  @Override
  public NfsFolderDetails queryForNfsFolder(NfsTarget nfsTarget) throws IOException {
    return null;
  }
}

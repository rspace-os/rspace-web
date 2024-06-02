package com.researchspace.netfiles.samba;

import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsTarget;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/** NfsClient based on jcifs library, but using smbj library for file download. */
@Slf4j
public class JcifsSmbjClient extends JcifsClient implements NfsClient {

  private static final long serialVersionUID = -5572582885943793238L;

  private SmbjClient smbjClient;

  public JcifsSmbjClient(
      String username, String password, String domain, String serverUrl, String shareName) {
    super(username, password, domain, serverUrl);
    log.info("JcifsSmbjClient initialising...");
    smbjClient = new SmbjClient(username, password, domain, serverUrl, shareName, false, false);
  }

  @Override
  public NfsFileDetails queryNfsFileForDownload(NfsTarget target) throws IOException {
    return smbjClient.queryNfsFileForDownload(target);
  }

  @Override
  public void releaseNfsFileAfterDownload(NfsFileDetails nfsFileDetails) {
    smbjClient.releaseNfsFileAfterDownload(nfsFileDetails);
  }

  @Override
  public void closeSession() {
    smbjClient.closeSession();
  }

  /*
   * ====================
   *     for tests
   * ====================
   */
  SmbjClient getSmbjClient() {
    return smbjClient;
  }
}

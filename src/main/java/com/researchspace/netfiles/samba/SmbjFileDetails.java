package com.researchspace.netfiles.samba;

import com.hierynomus.smbj.share.File;
import com.researchspace.netfiles.NfsFileDetails;

public class SmbjFileDetails extends NfsFileDetails {

  private File smbjFile;

  public SmbjFileDetails(String name, File file) {
    super(name);
    setRemoteInputStream(file.getInputStream());
    this.smbjFile = file;
  }

  protected File getSmbjFile() {
    return smbjFile;
  }
}

package com.researchspace.netfiles;

import java.io.InputStream;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NfsFileDetails extends NfsResourceDetails {

  /** input stream to remote file (on NFS server) */
  private InputStream remoteInputStream;

  public NfsFileDetails() {
    setType(TYPE_FILE);
  }

  public NfsFileDetails(String name) {
    this();
    setName(name);
  }
}

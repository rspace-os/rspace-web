package com.researchspace.netfiles;

import lombok.Data;

@Data
public class NfsTarget {

  private String path;
  private Long nfsId;

  public NfsTarget(String path, Long nfsId) {
    this.path = path;
    this.nfsId = nfsId;
  }

  public NfsTarget(String path) {
    this.path = path;
  }
}

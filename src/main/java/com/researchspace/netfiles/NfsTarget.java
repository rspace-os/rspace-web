package com.researchspace.netfiles;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NfsTarget {

  private String path;
  private Long nfsId;

  public NfsTarget(String path) {
    this.path = path;
  }
}

package com.researchspace.api.v1.model;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileSystem;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class NfsUserFileSystem {
  private User user;
  private NfsFileSystem nfsFileSystem;
}

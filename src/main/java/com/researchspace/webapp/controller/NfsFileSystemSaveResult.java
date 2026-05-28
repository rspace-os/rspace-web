package com.researchspace.webapp.controller;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload for the sysadmin save-filesystem endpoint. Carries the saved filesystem id and
 * any non-fatal warnings raised during validation (currently the list of whitelisted usernames that
 * do not match any RSpace user; the save still succeeds and the list is persisted as typed).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NfsFileSystemSaveResult implements Serializable {

  private static final long serialVersionUID = 1L;

  private Long fileSystemId;
  private List<String> unknownReadWhitelistUsernames;
  private List<String> unknownWriteWhitelistUsernames;
}

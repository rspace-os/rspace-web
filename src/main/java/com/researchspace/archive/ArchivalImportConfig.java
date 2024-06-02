package com.researchspace.archive;

import java.io.File;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Transient object hold configuration and resulting information */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class ArchivalImportConfig {
  private String zipPath;
  private String unzipPath;

  // as feedback
  private long zipSum = 0L;
  // initially not equal
  private long unzipSum = 1L;

  private String user;

  // unzip zip's file folder
  private File zipFolder;

  private ArchiveImportScope scope = ArchiveImportScope.IGNORE_USERS_AND_GROUPS;

  private Long targetFolderId;

  /**
   * A target folder ID which can be the import root. This must be a folder in the importer's
   * workspace; not a shared or gallery folder.
   *
   * @return
   */
  public Optional<Long> getTargetFolderId() {
    return Optional.ofNullable(targetFolderId);
  }
}

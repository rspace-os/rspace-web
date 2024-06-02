package com.researchspace.service.archive;

import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.model.User;

/** Defines post-export behaviour after an export file has been generated. */
@FunctionalInterface
public interface PostArchiveCompletion {

  /** No-op */
  public static final PostArchiveCompletion NULL = (a, b, c) -> {};

  /**
   * @param expCfg
   * @param user the exporter
   * @param result
   */
  void postArchiveCompletionOperations(IArchiveExportConfig expCfg, User user, ArchiveResult result)
      throws Exception;
}

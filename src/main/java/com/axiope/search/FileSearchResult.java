package com.axiope.search;

import lombok.Builder;
import lombok.Data;

/** Encapsulates metadata of a file retrieved by search hit. */
@Data
@Builder
public class FileSearchResult {
  /** File path returned from search implementation */
  private String filePath;

  private String fileName;
  private int score;
  private String explain;

  /** The relative path wrt the file store root. */
  private String rspaceRelativePath;
}

package com.researchspace.model.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;

/** POJO for nfs files export configuration */
@Data
@NoArgsConstructor
public class NfsExportConfig {

  private Boolean includeNfsFiles = false;
  private Integer maxFileSizeInMB;
  private String excludedFileExtensions;
}

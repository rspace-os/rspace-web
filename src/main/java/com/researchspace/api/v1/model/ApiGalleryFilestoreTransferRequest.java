package com.researchspace.api.v1.model;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/gallery/filestores/{filestoreId}/transfer}. The source
 * filestore id is in the URL path. Currently supports only S3↔S3 transfers (server-side {@code
 * CopyObject}). When {@code deleteSource} is true, the source object is deleted after a successful
 * copy (true move).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiGalleryFilestoreTransferRequest {

  @NotNull(message = "sourcePath is mandatory")
  @Size(min = 1, message = "sourcePath is mandatory")
  private String sourcePath;

  @NotNull(message = "destFilestoreId is mandatory")
  private Long destFilestoreId;

  @NotNull(message = "destPath is mandatory")
  @Size(min = 1, message = "destPath is mandatory")
  private String destPath;

  private boolean deleteSource;
}

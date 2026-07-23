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

  @NotNull(message = "{errors.gallery.filestore.validation.sourcePathRequired}")
  @Size(min = 1, message = "{errors.gallery.filestore.validation.sourcePathRequired}")
  private String sourcePath;

  @NotNull(message = "{errors.gallery.filestore.validation.destFilestoreIdRequired}")
  private Long destFilestoreId;

  @NotNull(message = "{errors.gallery.filestore.validation.destPathRequired}")
  @Size(min = 1, message = "{errors.gallery.filestore.validation.destPathRequired}")
  private String destPath;

  private boolean deleteSource;
}

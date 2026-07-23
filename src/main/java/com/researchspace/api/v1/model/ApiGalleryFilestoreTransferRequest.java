package com.researchspace.api.v1.model;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

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

  @NotEmpty(message = "{errors.gallery.filestore.validation.sourcePathRequired}")
  private String sourcePath;

  @NotNull(message = "{errors.gallery.filestore.validation.destFilestoreIdRequired}")
  private Long destFilestoreId;

  @NotEmpty(message = "{errors.gallery.filestore.validation.destPathRequired}")
  private String destPath;

  private boolean deleteSource;
}

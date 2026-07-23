package com.researchspace.api.v1.model;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/gallery/filestores/{filestoreId}/move}. Moves a file or
 * folder to another folder <em>within the same filestore</em> (server-side, S3 only). Paths are
 * relative to the filestore root. The moved item keeps its leaf name under {@code destPath}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiGalleryFilestoreMoveRequest {

  @NotNull(message = "{errors.gallery.filestore.validation.sourcePathRequired}")
  @Size(min = 1, message = "{errors.gallery.filestore.validation.sourcePathRequired}")
  private String sourcePath;

  @NotNull(message = "{errors.gallery.filestore.validation.destPathRequired}")
  private String destPath;
}

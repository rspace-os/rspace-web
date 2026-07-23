package com.researchspace.api.v1.model;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/gallery/filestores/{filestoreId}/folder}. Creates a new
 * subfolder named {@code name} under {@code path} (a filestore-relative path; {@code ""} or {@code
 * "/"} for the filestore root). S3 only.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiGalleryFilestoreFolderRequest {

  /** Parent folder, relative to the filestore root. Empty/"/" means the root. */
  @NotNull(message = "{errors.gallery.filestore.validation.pathRequired}")
  private String path;

  @NotNull(message = "{errors.gallery.filestore.validation.nameRequired}")
  @Size(min = 1, message = "{errors.gallery.filestore.validation.nameRequired}")
  private String name;
}

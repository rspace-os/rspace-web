package com.researchspace.api.v1.model;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

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

  @NotEmpty(message = "{errors.gallery.filestore.validation.nameRequired}")
  private String name;
}

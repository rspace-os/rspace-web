package com.researchspace.api.v1.model;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/gallery/filestores/{filestoreId}/delete}. Deletes the file
 * or empty folder at {@code path} (filestore-relative), subject to the creator/age gate. S3 only; a
 * non-empty folder is rejected.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiGalleryFilestoreDeleteRequest {

  @NotNull(message = "{errors.gallery.filestore.validation.pathRequired}")
  @Size(min = 1, message = "{errors.gallery.filestore.validation.pathRequired}")
  private String path;
}

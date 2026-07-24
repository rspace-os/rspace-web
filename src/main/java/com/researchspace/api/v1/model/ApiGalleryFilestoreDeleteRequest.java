package com.researchspace.api.v1.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Request body for {@code POST /api/v1/gallery/filestores/{filestoreId}/delete}. Deletes the file
 * or empty folder at {@code path} (filestore-relative), subject to the creator/age gate. S3 only; a
 * non-empty folder is rejected.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiGalleryFilestoreDeleteRequest {

  @NotEmpty(message = "{errors.gallery.filestore.validation.pathRequired}")
  private String path;
}

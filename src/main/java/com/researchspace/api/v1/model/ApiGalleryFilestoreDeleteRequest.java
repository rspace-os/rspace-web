package com.researchspace.api.v1.model;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/gallery/filestores/{filestoreId}/delete}. Deletes the file
 * or folder at {@code path} (filestore-relative), subject to the creator/age gate. S3 only. Folder
 * deletes are recursive and atomic.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiGalleryFilestoreDeleteRequest {

  @NotNull(message = "path is mandatory")
  @Size(min = 1, message = "path is mandatory")
  private String path;
}

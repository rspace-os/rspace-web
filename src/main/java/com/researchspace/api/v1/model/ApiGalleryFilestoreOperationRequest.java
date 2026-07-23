package com.researchspace.api.v1.model;

import com.researchspace.netfiles.ApiNfsCredentials;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/gallery/filestores/{filestoreId}/uploadFromGallery}. The
 * destination filestore id is in the URL path. The {@code credentials} field is optional — iRODS
 * backends require it; S3 backends ignore it. When {@code removeOriginalFromRspace} is true the
 * RSpace media records are deleted after a successful upload (a "move"); when false they are kept
 * (a "copy").
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiGalleryFilestoreOperationRequest {

  @NotNull(message = "{errors.gallery.filestore.validation.recordIdsRequired}")
  @Size(min = 1, message = "{errors.gallery.filestore.validation.recordIdsRequired}")
  private Set<Long> recordIds;

  private ApiNfsCredentials credentials;

  /** When true, delete the RSpace originals after upload (move); when false, keep them (copy). */
  private boolean removeOriginalFromRspace;

  /** Convenience constructor defaulting to a copy (originals kept in RSpace). */
  public ApiGalleryFilestoreOperationRequest(Set<Long> recordIds, ApiNfsCredentials credentials) {
    this(recordIds, credentials, false);
  }
}

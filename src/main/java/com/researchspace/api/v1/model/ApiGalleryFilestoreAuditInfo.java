package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.netfiles.FilestoreAuditMetadata;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for {@code GET /api/v1/gallery/filestores/{filestoreId}/metadata}: the RSpace
 * write-provenance of a single filestore item (who created it and when). Both fields are null when
 * the object carries no such metadata (e.g. non-S3 backends, or objects written outside RSpace).
 */
@Data
@NoArgsConstructor
public class ApiGalleryFilestoreAuditInfo {

  private String createdBy;

  @JsonProperty("createdAt")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long createdAtMillis;

  public ApiGalleryFilestoreAuditInfo(FilestoreAuditMetadata audit) {
    this.createdBy = audit.createdBy();
    this.createdAtMillis = audit.createdAt() == null ? null : audit.createdAt().toEpochMilli();
  }
}

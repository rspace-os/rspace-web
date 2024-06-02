package com.researchspace.api.v1.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import lombok.Data;

/** Result of an API export job */
@Data
public class ApiExportJobResult {
  /** Date after which archive might be deleted */
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long expiryDate;

  /** Size in bytes */
  private Long size;

  /** Checksum */
  private String checksum;

  /** Algorithm */
  private String algorithm;
}

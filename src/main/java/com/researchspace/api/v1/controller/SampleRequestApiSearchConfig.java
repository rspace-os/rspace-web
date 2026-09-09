package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.inventory.SampleRequestRole;
import com.researchspace.model.inventory.SampleRequestStatus;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Filters for the sample requests listing: which side of the request, status, and sample. */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class SampleRequestApiSearchConfig extends ApiSearchConfig {

  @Pattern(regexp = "REQUESTER|OWNER", message = "{errors.inventory.sampleRequest.role.invalid}")
  @JsonProperty("role")
  private String role;

  @Pattern(
      regexp = "PENDING|APPROVED|REJECTED|FULFILLED|CANCELLED",
      message = "{errors.inventory.sampleRequest.status.invalid}")
  @JsonProperty("status")
  private String status;

  @JsonProperty("sampleId")
  private Long sampleId;

  /** Defaults to the caller's own requests when unspecified. */
  public SampleRequestRole getRoleAsEnum() {
    return StringUtils.isBlank(role)
        ? SampleRequestRole.REQUESTER
        : SampleRequestRole.valueOf(role);
  }

  public SampleRequestStatus getStatusAsEnum() {
    return StringUtils.isBlank(status) ? null : SampleRequestStatus.valueOf(status);
  }

  @Override
  public MultiValueMap<String, String> toMap() {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    if (StringUtils.isNotBlank(role)) {
      params.add("role", role);
    }
    if (StringUtils.isNotBlank(status)) {
      params.add("status", status);
    }
    if (sampleId != null) {
      params.add("sampleId", sampleId.toString());
    }
    return params;
  }
}

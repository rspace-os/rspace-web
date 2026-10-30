package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.inventory.SampleRequestRole;
import com.researchspace.model.inventory.SampleRequestStatus;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
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

  private static final String STATUS_NAMES = "PENDING|APPROVED|REJECTED|FULFILLED|CANCELLED";

  @Pattern(regexp = "REQUESTER|OWNER", message = "{errors.inventory.sampleRequest.role.invalid}")
  @JsonProperty("role")
  private String role;

  /** Comma-separated list of statuses, e.g. "PENDING,APPROVED". */
  @Pattern(
      regexp = "(" + STATUS_NAMES + ")(,(" + STATUS_NAMES + "))*",
      message = "{errors.inventory.sampleRequest.status.invalid}")
  @JsonProperty("status")
  private String status;

  @JsonProperty("sampleId")
  private Long sampleId;

  /** Null when unspecified, meaning: match either role (requester or owner). */
  public SampleRequestRole getRoleAsEnum() {
    return StringUtils.isBlank(role) ? null : SampleRequestRole.valueOf(role);
  }

  /** Empty when no status filtering was requested. */
  public Set<SampleRequestStatus> getStatusesAsEnumSet() {
    if (StringUtils.isBlank(status)) {
      return Set.of();
    }
    return Arrays.stream(status.split(","))
        .map(SampleRequestStatus::valueOf)
        .collect(Collectors.toSet());
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

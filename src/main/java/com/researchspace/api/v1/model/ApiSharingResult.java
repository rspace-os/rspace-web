package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonPropertyOrder(value = {"shareInfos", "failedShares", "_links"})
public class ApiSharingResult extends LinkableApiObject {

  private List<ApiShareInfo> shareInfos;
  private List<Long> failedShares;

  public ApiSharingResult(List<ApiShareInfo> shared, List<Long> failed) {
    this.shareInfos = shared;
    this.failedShares = failed;
  }
}

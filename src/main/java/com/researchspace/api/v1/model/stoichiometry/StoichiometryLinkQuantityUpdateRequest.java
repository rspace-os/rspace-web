package com.researchspace.api.v1.model.stoichiometry;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import lombok.Data;

@Data
public class StoichiometryLinkQuantityUpdateRequest {
  private Long stoichiometryLinkId;
  private ApiQuantityInfo newQuantity;
}

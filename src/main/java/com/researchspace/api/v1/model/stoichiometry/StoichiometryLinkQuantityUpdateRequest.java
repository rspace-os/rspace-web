package com.researchspace.api.v1.model.stoichiometry;

import lombok.Data;

@Data
public class StoichiometryLinkQuantityUpdateRequest {
  private Long stoichiometryLinkId;
  private Double newQuantity;
}

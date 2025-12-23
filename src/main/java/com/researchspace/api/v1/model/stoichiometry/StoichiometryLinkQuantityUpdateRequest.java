package com.researchspace.api.v1.model.stoichiometry;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;

@Data
public class StoichiometryLinkQuantityUpdateRequest {
  private Long stoichiometryLinkId;
  private ApiQuantityInfo newQuantity;

  // to avoid lombok naming the getter isReducesStock
  @Getter(AccessLevel.NONE)
  private boolean reducesStock;

  public boolean reducesStock() {
    return reducesStock;
  }
}

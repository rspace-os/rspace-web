package com.researchspace.api.v1.model.stoichiometry;

import java.math.BigDecimal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StoichiometryInventoryLinkRequest {
  private String inventoryItemGlobalId;
  private Long stoichiometryMoleculeId;
  private BigDecimal quantity;
  private Integer unitId;

  // to avoid lombok naming this method isReducesStock
  @Getter(AccessLevel.NONE)
  private boolean reducesStock;


  public boolean reducesStock(){
    return reducesStock;
  }
}

package com.researchspace.api.v1.model.stoichiometry;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import lombok.Data;

@Data
public class StoichiometryInventoryLinkDTO {
  private Long id;
  private String inventoryItemGlobalId;
  private Long stoichiometryMoleculeId;
  private ApiQuantityInfo quantity;
  private boolean reducesStock;
}

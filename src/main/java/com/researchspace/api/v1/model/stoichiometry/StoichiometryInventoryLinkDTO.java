package com.researchspace.api.v1.model.stoichiometry;

import lombok.Data;

@Data
public class StoichiometryInventoryLinkDTO {
  private Long id;
  private String inventoryItemGlobalId;
  private Long stoichiometryMoleculeId;
  private Double quantityUsed;
}

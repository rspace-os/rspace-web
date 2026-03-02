package com.researchspace.api.v1.model.stoichiometry;

import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StoichiometryInventoryLinkDTO {
  private Long id;
  private String inventoryItemGlobalId;
  private boolean stockDeducted;

  public StoichiometryInventoryLinkDTO(StoichiometryInventoryLink entity) {
    this.id = entity.getId();
    this.inventoryItemGlobalId = entity.getInventoryRecord().getOid().getIdString();
    this.stockDeducted = entity.isStockDeducted();
  }
}

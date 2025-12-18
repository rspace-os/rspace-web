package com.researchspace.api.v1.model.stoichiometry;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StoichiometryInventoryLinkDTO {
  private Long id;
  private String inventoryItemGlobalId;
  private Long stoichiometryMoleculeId;
  private ApiQuantityInfo quantity;

  // to avoid lombok naming the getter isReducesStock
  @Getter(AccessLevel.NONE)
  private boolean reducesStock;

  public StoichiometryInventoryLinkDTO(StoichiometryInventoryLink entity) {
    this.id = entity.getId();
    this.inventoryItemGlobalId = entity.getInventoryRecord().getOid().getIdString();
    this.stoichiometryMoleculeId = entity.getStoichiometryMolecule().getId();
    this.quantity =
        entity.getQuantity().getNumericValue() == null
            ? null
            : new ApiQuantityInfo(entity.getQuantity());
    this.reducesStock = entity.getReducesStock();
  }

  public boolean reducesStock() {
    return reducesStock;
  }
}

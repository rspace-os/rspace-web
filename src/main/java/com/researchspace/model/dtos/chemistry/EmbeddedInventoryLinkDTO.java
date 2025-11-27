package com.researchspace.model.dtos.chemistry;

import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import lombok.Data;

@Data
public class EmbeddedInventoryLinkDTO {
  private Long linkId;
  private String inventoryItemGlobalId;
  private String unit;
  private Double quantityUsed;

  EmbeddedInventoryLinkDTO(StoichiometryInventoryLink inventoryLink) {
    this.linkId = inventoryLink.getId();
    this.inventoryItemGlobalId = inventoryLink.getConnectedRecordGlobalIdentifier();
    this.unit = inventoryLink.getUnit();
    this.quantityUsed = inventoryLink.getQuantityUsed();
  }

  static EmbeddedInventoryLinkDTO fromInventoryLink(StoichiometryInventoryLink inventoryLink) {
    if (inventoryLink == null) {
      return null;
    }
    return new EmbeddedInventoryLinkDTO(inventoryLink);
  }
}

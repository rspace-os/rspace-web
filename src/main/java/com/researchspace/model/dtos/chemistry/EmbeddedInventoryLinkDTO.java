package com.researchspace.model.dtos.chemistry;

import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class EmbeddedInventoryLinkDTO {
  private Long linkId;
  private String inventoryItemGlobalId;
  private BigDecimal quantity;
  private int unitId;

  EmbeddedInventoryLinkDTO(StoichiometryInventoryLink inventoryLink) {
    this.linkId = inventoryLink.getId();
    this.inventoryItemGlobalId = inventoryLink.getConnectedRecordGlobalIdentifier();
    this.quantity = inventoryLink.getQuantity().getNumericValue();
    this.unitId = inventoryLink.getQuantity().getUnitId();
  }

  public static EmbeddedInventoryLinkDTO fromInventoryLink(
      StoichiometryInventoryLink inventoryLink) {
    if (inventoryLink == null) {
      return null;
    }
    return new EmbeddedInventoryLinkDTO(inventoryLink);
  }
}

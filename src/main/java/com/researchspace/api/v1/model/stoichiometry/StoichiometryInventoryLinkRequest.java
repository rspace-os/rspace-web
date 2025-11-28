package com.researchspace.api.v1.model.stoichiometry;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StoichiometryInventoryLinkRequest {
  private String inventoryItemGlobalId;
  private Long stoichiometryMoleculeId;
  private BigDecimal quantityUsed;
  private Integer unitId;
}

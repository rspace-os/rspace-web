package com.researchspace.model.dtos.chemistry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoichiometryMoleculeUpdateDTO {
  private Long id;
  private Double coefficient;
  private Double mass;
  private Double moles;
  private Double expectedAmount;
  private Double actualAmount;
  private Double actualYield;
  private Boolean limitingReagent;
  private String notes;
}

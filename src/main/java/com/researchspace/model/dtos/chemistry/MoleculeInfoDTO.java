package com.researchspace.model.dtos.chemistry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoleculeInfoDTO {
  private int atomCount;
  private int bondCount;
  private int formalCharge;
  private double exactMass, mass;
  private String formula, name;
  private MoleculeRole role;
}

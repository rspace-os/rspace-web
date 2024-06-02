package com.researchspace.model.dtos.chemistry;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MoleculeInfoDTO {
  private int atomCount;
  private int bondCount;
  private int formalCharge;
  private double exactMass, mass;
  private String formula, name;
  private MoleculeRole role;
}

package com.researchspace.model.dtos.chemistry;

import com.researchspace.model.stoichiometry.MoleculeRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoichiometryMoleculeDTO {
  private Long id;
  private Long rsChemElementId;
  private MoleculeRole role;
  private String formula;
  private String name;
  private String smiles;
  private Double coefficient;
  private Double molecularWeight;
  private Double mass;
  private Double actualAmount;
  private Double actualYield;
  private Boolean limitingReagent;
  private String notes;
}

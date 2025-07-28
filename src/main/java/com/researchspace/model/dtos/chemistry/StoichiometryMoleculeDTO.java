package com.researchspace.model.dtos.chemistry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for stoichiometry information of a molecule in a chemical reaction. Contains
 * information about the molecule's properties and role in the reaction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoichiometryMoleculeDTO {
  private Long id;
  private Long moleculeId; // Foreign key to RSChemElement.id
  private MoleculeRole role;
  private String compound;
  private Double coefficient;
  private Double molecularMass;
  private Double absoluteMass;
  private Double volume;
  private Double usedExpectedAmount;
  private Double actualStoichiometry;
  private Double actualYield;
  private Double yieldPercentage;
  private String additionalMetadata;
}

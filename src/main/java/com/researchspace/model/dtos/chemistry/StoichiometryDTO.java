package com.researchspace.model.dtos.chemistry;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for stoichiometry information of a chemical reaction. Contains lists of
 * molecules categorized by their role in the reaction (reactants, products, agents).
 */
@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoichiometryDTO {
  private List<StoichiometryMoleculeDTO> molecules;
  private String formula;
  private boolean isReaction;
  private String additionalMetadata;
  private Long parentReactionId;

  public List<StoichiometryMoleculeDTO> getAgents() {
    return filterByRole(MoleculeRole.AGENT);
  }

  public List<StoichiometryMoleculeDTO> getReactants() {
    return filterByRole(MoleculeRole.REACTANT);
  }

  public List<StoichiometryMoleculeDTO> getProducts() {
    return filterByRole(MoleculeRole.PRODUCT);
  }

  private List<StoichiometryMoleculeDTO> filterByRole(MoleculeRole role) {
    if (molecules == null) {
      return Collections.emptyList();
    }
    return molecules.stream()
        .filter(mol -> role.equals(mol.getRole()))
        .collect(Collectors.toList());
  }
}

package com.researchspace.model.dtos.chemistry;

import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElementalAnalysisDTO {
  private List<MoleculeInfoDTO> moleculeInfo;
  private String formula;
  private boolean isReaction;

  public List<MoleculeInfoDTO> getAgents() {
    return filterByRole(MoleculeRole.AGENT);
  }

  public List<MoleculeInfoDTO> getReactants() {
    return filterByRole(MoleculeRole.REACTANT);
  }

  public List<MoleculeInfoDTO> getProducts() {
    return filterByRole(MoleculeRole.PRODUCT);
  }

  public List<MoleculeInfoDTO> getMolecules() {
    return filterByRole(MoleculeRole.MOLECULE);
  }

  private List<MoleculeInfoDTO> filterByRole(MoleculeRole role) {
    return moleculeInfo.stream()
        .filter(mol -> role.equals(mol.getRole()))
        .collect(Collectors.toList());
  }
}

package com.researchspace.model.dtos.chemistry;

import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class StoichiometryMapper {

  private StoichiometryMapper() {}

  public static StoichiometryDTO toDTO(Stoichiometry stoichiometry) {
    if (stoichiometry == null) {
      return null;
    }

    List<StoichiometryMoleculeDTO> moleculeDTOs = new ArrayList<>();
    if (stoichiometry.getMolecules() != null) {
      moleculeDTOs =
          stoichiometry.getMolecules().stream()
              .map(StoichiometryMapper::moleculeToDTO)
              .collect(Collectors.toList());
    }

    Long parentReactionId = null;
    if (stoichiometry.getParentReaction() != null) {
      parentReactionId = stoichiometry.getParentReaction().getId();
    }

    return StoichiometryDTO.builder()
        .id(stoichiometry.getId())
        .molecules(moleculeDTOs)
        .parentReactionId(parentReactionId)
        .build();
  }

  public static StoichiometryDTO fromAnalysisDTO(ElementalAnalysisDTO analysisDTO) {
    if (analysisDTO == null) {
      return null;
    }

    List<StoichiometryMoleculeDTO> moleculeDTOs = new ArrayList<>();
    if (analysisDTO.getMoleculeInfo() != null) {
      moleculeDTOs =
          analysisDTO.getMoleculeInfo().stream()
              .map(StoichiometryMapper::moleculeInfoToDTO)
              .collect(Collectors.toList());
    }

    return StoichiometryDTO.builder().molecules(moleculeDTOs).build();
  }

  public static StoichiometryMoleculeDTO moleculeToDTO(StoichiometryMolecule molecule) {
    if (molecule == null) {
      return null;
    }
    Long rsChemElementId =
        molecule.getRsChemElement() != null ? molecule.getRsChemElement().getId() : null;
    return StoichiometryMoleculeDTO.builder()
        .id(molecule.getId())
        .rsChemElementId(rsChemElementId)
        .role(molecule.getRole())
        .formula(molecule.getFormula())
        .name(molecule.getName())
        .smiles(molecule.getSmiles())
        .coefficient(molecule.getCoefficient())
        .molecularWeight(molecule.getMolecularWeight())
        .mass(molecule.getMass())
        .moles(molecule.getMoles())
        .expectedAmount(molecule.getExpectedAmount())
        .actualAmount(molecule.getActualAmount())
        .actualYield(molecule.getActualYield())
        .limitingReagent(molecule.getLimitingReagent())
        .notes(molecule.getNotes())
        .build();
  }

  public static StoichiometryMoleculeDTO moleculeInfoToDTO(MoleculeInfoDTO moleculeInfo) {
    if (moleculeInfo == null) {
      return null;
    }
    return StoichiometryMoleculeDTO.builder()
        .role(moleculeInfo.getRole())
        // In ElementalAnalysisDTO, name holds SMILES (as per existing logic)
        .smiles(moleculeInfo.getName())
        .molecularWeight(moleculeInfo.getMass())
        .build();
  }
}

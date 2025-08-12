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
        .smiles(moleculeInfo.getSmiles())
        .molecularWeight(moleculeInfo.getMass())
        .formula(moleculeInfo.getFormula())
        .build();
  }

  public static StoichiometryMoleculeUpdateDTO toUpdateDTO(StoichiometryMoleculeDTO dto) {
    if (dto == null) {
      return null;
    }
    return StoichiometryMoleculeUpdateDTO.builder()
        .id(dto.getId())
        .role(dto.getRole())
        .smiles(dto.getSmiles())
        .name(dto.getName())
        .formula(dto.getFormula())
        .molecularWeight(dto.getMolecularWeight())
        .coefficient(dto.getCoefficient())
        .mass(dto.getMass())
        .moles(dto.getMoles())
        .expectedAmount(dto.getExpectedAmount())
        .actualAmount(dto.getActualAmount())
        .actualYield(dto.getActualYield())
        .limitingReagent(dto.getLimitingReagent())
        .notes(dto.getNotes())
        .build();
  }

  public static List<StoichiometryMoleculeUpdateDTO> toUpdateDTOs(
      List<StoichiometryMoleculeDTO> dtos) {
    if (dtos == null) {
      return new ArrayList<>();
    }
    return dtos.stream().map(StoichiometryMapper::toUpdateDTO).collect(Collectors.toList());
  }
}

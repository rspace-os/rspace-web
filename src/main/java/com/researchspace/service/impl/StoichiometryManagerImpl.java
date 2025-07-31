package com.researchspace.service.impl;

import com.researchspace.dao.StoichiometryDao;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.Stoichiometry;
import com.researchspace.model.StoichiometryMolecule;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalSearcher;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.StoichiometryManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("stoichiometryManager")
@Transactional
public class StoichiometryManagerImpl extends GenericManagerImpl<Stoichiometry, Long>
    implements StoichiometryManager {

  private final StoichiometryDao stoichiometryDao;
  private final RSChemElementManager rsChemElementManager;
  private final ChemicalSearcher chemicalSearcher;

  @Autowired
  public StoichiometryManagerImpl(
      StoichiometryDao stoichiometryDao,
      RSChemElementManager rsChemElementManager,
      ChemicalSearcher chemicalSearcher) {
    super(stoichiometryDao);
    this.stoichiometryDao = stoichiometryDao;
    this.rsChemElementManager = rsChemElementManager;
    this.chemicalSearcher = chemicalSearcher;
  }

  @Override
  public Stoichiometry findByParentReactionId(Long parentReactionId) {
    return stoichiometryDao.findByParentReactionId(parentReactionId);
  }

  @Override
  public Stoichiometry createFromAnalysis(
      ElementalAnalysisDTO analysisDTO, RSChemElement parentReaction, User user)
      throws IOException {
    Stoichiometry stoichiometry = Stoichiometry.builder().parentReaction(parentReaction).build();

    stoichiometry = save(stoichiometry);

    if (analysisDTO.getMoleculeInfo() != null) {
      for (MoleculeInfoDTO moleculeInfo : analysisDTO.getMoleculeInfo()) {
        RSChemElement molecule =
            RSChemElement.builder().chemElements(moleculeInfo.getSmiles()).build();
        molecule = rsChemElementManager.save(molecule, user);

        String pubChemName = null;
        try {
          pubChemName =
              chemicalSearcher
                  .searchChemicals(ChemicalImportSearchType.SMILES, moleculeInfo.getSmiles())
                  .get(0)
                  .getName();
        } catch (ChemicalImportException e) {
          log.warn(
              "Unable to find chemical name for smiles: {}. {}",
              moleculeInfo.getSmiles(),
              e.getMessage());
        }
        StoichiometryMolecule stoichiometryMolecule =
            StoichiometryMolecule.builder()
                .stoichiometry(stoichiometry)
                .rsChemElement(molecule)
                .role(moleculeInfo.getRole())
                .smiles(moleculeInfo.getSmiles())
                .molecularWeight(moleculeInfo.getMass())
                .name(pubChemName)
                .formula(moleculeInfo.getFormula())
                .limitingReagent(false)
                .build();

        stoichiometry.addMolecule(stoichiometryMolecule);
      }
    }

    return save(stoichiometry);
  }

  @Override
  public Stoichiometry update(Long stoichiometryId, StoichiometryDTO stoichiometryDTO, User user) {
    Stoichiometry stoichiometry = get(stoichiometryId);
    if (stoichiometry == null) {
      throw new IllegalArgumentException("Stoichiometry not found with ID: " + stoichiometryId);
    }

    validateUpdatableFields(stoichiometryDTO, stoichiometry);

    stoichiometry.getMolecules().clear();

    if (stoichiometryDTO.getMolecules() != null) {
      for (StoichiometryMoleculeDTO moleculeDTO : stoichiometryDTO.getMolecules()) {
        RSChemElement molecule;
        if (moleculeDTO.getRsChemElementId() != null) {
          molecule = rsChemElementManager.get(moleculeDTO.getRsChemElementId(), user);
        } else {
          molecule = RSChemElement.builder().build();
          try {
            molecule = rsChemElementManager.save(molecule, user);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }

        StoichiometryMolecule updatedMolecule =
            StoichiometryMolecule.builder()
                .id(moleculeDTO.getId())
                .stoichiometry(stoichiometry)
                .rsChemElement(molecule)
                .role(moleculeDTO.getRole())
                .formula(moleculeDTO.getFormula())
                .name(moleculeDTO.getName())
                .smiles(moleculeDTO.getSmiles())
                .coefficient(moleculeDTO.getCoefficient())
                .molecularWeight(moleculeDTO.getMolecularWeight())
                .mass(moleculeDTO.getMass())
                .moles(moleculeDTO.getMoles())
                .expectedAmount(moleculeDTO.getExpectedAmount())
                .actualAmount(moleculeDTO.getActualAmount())
                .actualYield(moleculeDTO.getActualYield())
                .limitingReagent(moleculeDTO.getLimitingReagent())
                .notes(moleculeDTO.getNotes())
                .build();

        stoichiometry.addMolecule(updatedMolecule);
      }
    }

    return save(stoichiometry);
  }

  private static void validateUpdatableFields(
      StoichiometryDTO stoichiometryDTO, Stoichiometry stoichiometry) {
    Map<Long, StoichiometryMolecule> existingMolecules = new HashMap<>();
    if (stoichiometry.getMolecules() != null) {
      for (StoichiometryMolecule molecule : stoichiometry.getMolecules()) {
        if (molecule.getId() != null) {
          existingMolecules.put(molecule.getId(), molecule);
        }
      }
    }

    if (stoichiometryDTO.getMolecules() != null) {
      for (StoichiometryMoleculeDTO moleculeDTO : stoichiometryDTO.getMolecules()) {
        if (moleculeDTO.getId() != null && existingMolecules.containsKey(moleculeDTO.getId())) {
          StoichiometryMolecule existingMolecule = existingMolecules.get(moleculeDTO.getId());

          if (moleculeDTO.getRsChemElementId() != null
              && !Objects.equals(
                  moleculeDTO.getRsChemElementId(), existingMolecule.getRsChemElement().getId())) {
            throw new IllegalArgumentException("Cannot update rsChemElementId field");
          }

          if (!Objects.equals(moleculeDTO.getRole(), existingMolecule.getRole())) {
            throw new IllegalArgumentException("Cannot update role field");
          }

          if (!Objects.equals(moleculeDTO.getFormula(), existingMolecule.getFormula())) {
            throw new IllegalArgumentException("Cannot update formula field");
          }

          if (!Objects.equals(moleculeDTO.getName(), existingMolecule.getName())) {
            throw new IllegalArgumentException("Cannot update name field");
          }

          if (!Objects.equals(moleculeDTO.getSmiles(), existingMolecule.getSmiles())) {
            throw new IllegalArgumentException("Cannot update smiles field");
          }

          if (!Objects.equals(
              moleculeDTO.getMolecularWeight(), existingMolecule.getMolecularWeight())) {
            throw new IllegalArgumentException("Cannot update molecularWeight field");
          }
        }
      }
    }
  }

  @Override
  public StoichiometryDTO toDTO(Stoichiometry stoichiometry) {
    if (stoichiometry == null) {
      return null;
    }

    List<StoichiometryMoleculeDTO> moleculeDTOs = new ArrayList<>();
    if (stoichiometry.getMolecules() != null) {
      moleculeDTOs =
          stoichiometry.getMolecules().stream()
              .map(this::moleculeToDTO)
              .collect(Collectors.toList());
    }

    return StoichiometryDTO.builder()
        .molecules(moleculeDTOs)
        .parentReactionId(stoichiometry.getParentReaction().getId())
        .build();
  }

  @Override
  public StoichiometryDTO fromAnalysisDTO(ElementalAnalysisDTO analysisDTO) {
    if (analysisDTO == null) {
      return null;
    }

    List<StoichiometryMoleculeDTO> moleculeDTOs = new ArrayList<>();
    if (analysisDTO.getMoleculeInfo() != null) {
      moleculeDTOs =
          analysisDTO.getMoleculeInfo().stream()
              .map(this::moleculeInfoToDTO)
              .collect(Collectors.toList());
    }

    return StoichiometryDTO.builder().molecules(moleculeDTOs).build();
  }

  private StoichiometryMoleculeDTO moleculeToDTO(StoichiometryMolecule molecule) {
    return StoichiometryMoleculeDTO.builder()
        .id(molecule.getId())
        .rsChemElementId(molecule.getRsChemElement().getId())
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

  private StoichiometryMoleculeDTO moleculeInfoToDTO(MoleculeInfoDTO moleculeInfo) {
    return StoichiometryMoleculeDTO.builder()
        .role(moleculeInfo.getRole())
        .smiles(moleculeInfo.getName())
        .molecularWeight(moleculeInfo.getMass())
        .build();
  }
}

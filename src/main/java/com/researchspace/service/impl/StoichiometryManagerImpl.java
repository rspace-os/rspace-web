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
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeUpdateDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalSearcher;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.StoichiometryManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
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
  public Stoichiometry update(StoichiometryUpdateDTO stoichiometryUpdate, User user) {
    Stoichiometry stoichiometry = get(stoichiometryUpdate.getId());
    if (stoichiometry == null) {
      throw new IllegalArgumentException(
          "Stoichiometry not found with ID: " + stoichiometryUpdate.getId());
    }

    Map<Long, StoichiometryMolecule> existingMolecules = new HashMap<>();
    if (stoichiometry.getMolecules() != null) {
      for (StoichiometryMolecule molecule : stoichiometry.getMolecules()) {
        if (molecule.getId() != null) {
          existingMolecules.put(molecule.getId(), molecule);
        }
      }
    }

    stoichiometry.getMolecules().clear();

    if (stoichiometryUpdate.getMolecules() != null) {
      for (StoichiometryMoleculeUpdateDTO moleculeUpdateDTO : stoichiometryUpdate.getMolecules()) {
        if (moleculeUpdateDTO.getId() == null
            || !existingMolecules.containsKey(moleculeUpdateDTO.getId())) {
          throw new IllegalArgumentException(
              "Molecule ID is required and must exist in the stoichiometry");
        }

        StoichiometryMolecule existingMolecule = existingMolecules.get(moleculeUpdateDTO.getId());
        RSChemElement rsChemElement = existingMolecule.getRsChemElement();

        StoichiometryMolecule updatedMolecule =
            StoichiometryMolecule.builder()
                .id(moleculeUpdateDTO.getId())
                .stoichiometry(stoichiometry)
                .rsChemElement(rsChemElement)
                .role(existingMolecule.getRole())
                .formula(existingMolecule.getFormula())
                .name(existingMolecule.getName())
                .smiles(existingMolecule.getSmiles())
                .coefficient(moleculeUpdateDTO.getCoefficient())
                .molecularWeight(existingMolecule.getMolecularWeight())
                .mass(moleculeUpdateDTO.getMass())
                .moles(moleculeUpdateDTO.getMoles())
                .expectedAmount(moleculeUpdateDTO.getExpectedAmount())
                .actualAmount(moleculeUpdateDTO.getActualAmount())
                .actualYield(moleculeUpdateDTO.getActualYield())
                .limitingReagent(moleculeUpdateDTO.getLimitingReagent())
                .notes(moleculeUpdateDTO.getNotes())
                .build();

        stoichiometry.addMolecule(updatedMolecule);
      }
    }

    return save(stoichiometry);
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
        .id(stoichiometry.getId())
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

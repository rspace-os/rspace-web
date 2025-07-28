package com.researchspace.service.impl;

import com.researchspace.dao.StoichiometryDao;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.Stoichiometry;
import com.researchspace.model.StoichiometryMolecule;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.StoichiometryManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the StoichiometryManager interface. Provides methods for CRUD operations on
 * stoichiometry tables.
 */
@Service("stoichiometryManager")
@Transactional
public class StoichiometryManagerImpl extends GenericManagerImpl<Stoichiometry, Long>
    implements StoichiometryManager {

  private final StoichiometryDao stoichiometryDao;
  private final RSChemElementManager rsChemElementManager;

  /**
   * Constructor.
   *
   * @param stoichiometryDao the stoichiometry DAO
   * @param rsChemElementManager the RSChemElement manager
   */
  @Autowired
  public StoichiometryManagerImpl(
      StoichiometryDao stoichiometryDao, RSChemElementManager rsChemElementManager) {
    super(stoichiometryDao);
    this.stoichiometryDao = stoichiometryDao;
    this.rsChemElementManager = rsChemElementManager;
  }

  /** {@inheritDoc} */
  @Override
  public Stoichiometry findByParentReactionId(Long parentReactionId) {
    return stoichiometryDao.findByParentReactionId(parentReactionId);
  }

  /** {@inheritDoc} */
  @Override
  public Stoichiometry createFromAnalysis(
      ElementalAnalysisDTO analysisDTO, RSChemElement parentReaction, User user)
      throws IOException {
    Stoichiometry stoichiometry =
        Stoichiometry.builder()
            .parentReaction(parentReaction)
            .formula(analysisDTO.getFormula())
            .isReaction(analysisDTO.isReaction())
            .additionalMetadata(analysisDTO.getAdditionalMetadata())
            .build();

    // Save the stoichiometry to get an ID
    stoichiometry = save(stoichiometry);

    // Add molecules
    if (analysisDTO.getMoleculeInfo() != null) {
      for (MoleculeInfoDTO moleculeInfo : analysisDTO.getMoleculeInfo()) {
        // Create a new RSChemElement for the molecule
        RSChemElement molecule =
            RSChemElement.builder().chemElements(moleculeInfo.getFormula()).build();
        molecule = rsChemElementManager.save(molecule, user);

        // Create a new StoichiometryMolecule
        StoichiometryMolecule stoichiometryMolecule =
            StoichiometryMolecule.builder()
                .stoichiometry(stoichiometry)
                .molecule(molecule)
                .role(moleculeInfo.getRole())
                .compound(moleculeInfo.getName())
                .molecularMass(moleculeInfo.getMass())
                .build();

        // Add the molecule to the stoichiometry
        stoichiometry.addMolecule(stoichiometryMolecule);
      }
    }

    // Save the stoichiometry with molecules
    return save(stoichiometry);
  }

  /** {@inheritDoc} */
  @Override
  public Stoichiometry update(Long stoichiometryId, StoichiometryDTO stoichiometryDTO, User user) {
    Stoichiometry stoichiometry = get(stoichiometryId);
    if (stoichiometry == null) {
      throw new IllegalArgumentException("Stoichiometry not found with ID: " + stoichiometryId);
    }

    // Update basic properties
    stoichiometry.setFormula(stoichiometryDTO.getFormula());
    stoichiometry.setReaction(stoichiometryDTO.isReaction());
    stoichiometry.setAdditionalMetadata(stoichiometryDTO.getAdditionalMetadata());

    // Clear existing molecules
    stoichiometry.getMolecules().clear();

    // Add updated molecules
    if (stoichiometryDTO.getMolecules() != null) {
      for (StoichiometryMoleculeDTO moleculeDTO : stoichiometryDTO.getMolecules()) {
        // Get or create the RSChemElement for the molecule
        RSChemElement molecule;
        if (moleculeDTO.getMoleculeId() != null) {
          molecule = rsChemElementManager.get(moleculeDTO.getMoleculeId(), user);
        } else {
          molecule = RSChemElement.builder().build();
          try {
            molecule = rsChemElementManager.save(molecule, user);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }

        // Create a new StoichiometryMolecule
        StoichiometryMolecule stoichiometryMolecule =
            StoichiometryMolecule.builder()
                .stoichiometry(stoichiometry)
                .molecule(molecule)
                .role(moleculeDTO.getRole())
                .compound(moleculeDTO.getCompound())
                .coefficient(moleculeDTO.getCoefficient())
                .molecularMass(moleculeDTO.getMolecularMass())
                .absoluteMass(moleculeDTO.getAbsoluteMass())
                .volume(moleculeDTO.getVolume())
                .usedExpectedAmount(moleculeDTO.getUsedExpectedAmount())
                .actualStoichiometry(moleculeDTO.getActualStoichiometry())
                .actualYield(moleculeDTO.getActualYield())
                .yieldPercentage(moleculeDTO.getYieldPercentage())
                .additionalMetadata(moleculeDTO.getAdditionalMetadata())
                .build();

        // Add the molecule to the stoichiometry
        stoichiometry.addMolecule(stoichiometryMolecule);
      }
    }

    // Save the updated stoichiometry
    return save(stoichiometry);
  }

  /** {@inheritDoc} */
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
        .formula(stoichiometry.getFormula())
        .isReaction(stoichiometry.isReaction())
        .additionalMetadata(stoichiometry.getAdditionalMetadata())
        .parentReactionId(stoichiometry.getParentReaction().getId())
        .build();
  }

  /** {@inheritDoc} */
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

    return StoichiometryDTO.builder()
        .molecules(moleculeDTOs)
        .formula(analysisDTO.getFormula())
        .isReaction(analysisDTO.isReaction())
        .additionalMetadata(analysisDTO.getAdditionalMetadata())
        .build();
  }

  /**
   * Convert a StoichiometryMolecule entity to a StoichiometryMoleculeDTO.
   *
   * @param molecule the molecule entity to convert
   * @return the converted DTO
   */
  private StoichiometryMoleculeDTO moleculeToDTO(StoichiometryMolecule molecule) {
    return StoichiometryMoleculeDTO.builder()
        .id(molecule.getId())
        .moleculeId(molecule.getMolecule().getId())
        .role(molecule.getRole())
        .compound(molecule.getCompound())
        .coefficient(molecule.getCoefficient())
        .molecularMass(molecule.getMolecularMass())
        .absoluteMass(molecule.getAbsoluteMass())
        .volume(molecule.getVolume())
        .usedExpectedAmount(molecule.getUsedExpectedAmount())
        .actualStoichiometry(molecule.getActualStoichiometry())
        .actualYield(molecule.getActualYield())
        .yieldPercentage(molecule.getYieldPercentage())
        .additionalMetadata(molecule.getAdditionalMetadata())
        .build();
  }

  /**
   * Convert a MoleculeInfoDTO to a StoichiometryMoleculeDTO.
   *
   * @param moleculeInfo the molecule info DTO to convert
   * @return the converted DTO
   */
  private StoichiometryMoleculeDTO moleculeInfoToDTO(MoleculeInfoDTO moleculeInfo) {
    return StoichiometryMoleculeDTO.builder()
        .role(moleculeInfo.getRole())
        .compound(moleculeInfo.getName())
        .molecularMass(moleculeInfo.getMass())
        .build();
  }
}

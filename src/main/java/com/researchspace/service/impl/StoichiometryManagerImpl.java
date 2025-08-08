package com.researchspace.service.impl;

import com.researchspace.dao.StoichiometryDao;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeUpdateDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalSearcher;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.StoichiometryManager;
import com.researchspace.service.chemistry.StoichiometryException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
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
  public Optional<Stoichiometry> findByParentReactionId(Long parentReactionId) {
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
      throw new StoichiometryException(
          "Stoichiometry not found with ID: " + stoichiometryUpdate.getId());
    }

    List<StoichiometryMolecule> existingMolecules = stoichiometry.getMolecules();
    Map<Long, StoichiometryMolecule> idToMol = buildExistingMoleculesMap(existingMolecules);

    Set<Long> keepIds = new HashSet<>();

    if (stoichiometryUpdate.getMolecules() != null
        && !stoichiometryUpdate.getMolecules().isEmpty()) {
      for (StoichiometryMoleculeUpdateDTO u : stoichiometryUpdate.getMolecules()) {
        StoichiometryMolecule existing = idToMol.get(u.getId());
        if (existing == null) {
          throw new com.researchspace.service.chemistry.StoichiometryException(
              "Molecule ID " + u.getId() + " not found in existing stoichiometry molecules.");
        }

        existing.setCoefficient(u.getCoefficient());
        existing.setMass(u.getMass());
        existing.setMoles(u.getMoles());
        existing.setExpectedAmount(u.getExpectedAmount());
        existing.setActualAmount(u.getActualAmount());
        existing.setActualYield(u.getActualYield());
        existing.setLimitingReagent(u.getLimitingReagent());
        existing.setNotes(u.getNotes());

        keepIds.add(existing.getId());
      }
    }

    if (existingMolecules != null) {
      existingMolecules.removeIf(m -> m.getId() != null && !keepIds.contains(m.getId()));
    }

    return save(stoichiometry);
  }

  @NotNull
  private static Map<Long, StoichiometryMolecule> buildExistingMoleculesMap(
      List<StoichiometryMolecule> current) {
    Map<Long, StoichiometryMolecule> byId = new HashMap<>();
    if (current != null) {
      for (StoichiometryMolecule m : current) {
        if (m.getId() != null) {
          byId.put(m.getId(), m);
        }
      }
    }
    return byId;
  }
}

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
import com.researchspace.service.chemistry.ChemistryClient;
import com.researchspace.service.chemistry.StoichiometryException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private final ChemistryClient chemistryClient;

  @Autowired
  public StoichiometryManagerImpl(
      StoichiometryDao stoichiometryDao,
      RSChemElementManager rsChemElementManager,
      ChemicalSearcher chemicalSearcher,
      ChemistryClient chemistryClient) {
    super(stoichiometryDao);
    this.stoichiometryDao = stoichiometryDao;
    this.rsChemElementManager = rsChemElementManager;
    this.chemicalSearcher = chemicalSearcher;
    this.chemistryClient = chemistryClient;
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

    Set<Long> keepIds = processUpdates(stoichiometry, stoichiometryUpdate.getMolecules(), user);

    removeMoleculesNotInKeep(stoichiometry.getMolecules(), keepIds);

    return save(stoichiometry);
  }

  @Override
  public Stoichiometry copyForReaction(
      Long sourceParentReactionId, RSChemElement newParentReaction, User user) {
    Stoichiometry source =
        findByParentReactionId(sourceParentReactionId)
            .orElseThrow(
                () ->
                    new StoichiometryException(
                        "No stoichiometry found for reaction id " + sourceParentReactionId));

    Stoichiometry target = Stoichiometry.builder().parentReaction(newParentReaction).build();
    target = save(target);

    if (source.getMolecules() != null) {
      for (StoichiometryMolecule sourceMol : source.getMolecules()) {
        RSChemElement newMol;
        try {
          newMol = rsChemElementManager.save(
              RSChemElement.builder().chemElements(sourceMol.getSmiles()).build(), user);
        } catch (IOException e) {
          throw new StoichiometryException(
              "Problem saving molecule from SMILES during stoichiometry copy", e);
        }

        StoichiometryMolecule copy =
            StoichiometryMolecule.builder()
                .stoichiometry(target)
                .rsChemElement(newMol)
                .role(sourceMol.getRole())
                .smiles(sourceMol.getSmiles())
                .name(sourceMol.getName())
                .formula(sourceMol.getFormula())
                .molecularWeight(sourceMol.getMolecularWeight())
                .coefficient(sourceMol.getCoefficient())
                .mass(sourceMol.getMass())
                .moles(sourceMol.getMoles())
                .expectedAmount(sourceMol.getExpectedAmount())
                .actualAmount(sourceMol.getActualAmount())
                .actualYield(sourceMol.getActualYield())
                .limitingReagent(sourceMol.getLimitingReagent())
                .notes(sourceMol.getNotes())
                .build();

        target.addMolecule(copy);
      }
    }

    return save(target);
  }

  private Set<Long> processUpdates(
      Stoichiometry stoichiometry, List<StoichiometryMoleculeUpdateDTO> updates, User user) {
    Set<Long> keepIds = new HashSet<>();
    if (updates == null || updates.isEmpty()) {
      return keepIds;
    }

    Map<Long, StoichiometryMolecule> idToMol =
        buildExistingMoleculesMap(stoichiometry.getMolecules());

    for (StoichiometryMoleculeUpdateDTO u : updates) {
      if (u.getId() == null) {
        addNewMoleculeFromDto(stoichiometry, u, user);
        continue;
      }

      StoichiometryMolecule existing = idToMol.get(u.getId());
      if (existing == null) {
        throw new StoichiometryException(
            "Molecule ID " + u.getId() + " not found in existing stoichiometry molecules.");
      }

      applyFieldUpdates(existing, u);
      keepIds.add(existing.getId());
    }

    return keepIds;
  }

  private void addNewMoleculeFromDto(
      Stoichiometry stoichiometry, StoichiometryMoleculeUpdateDTO updateMol, User user) {
    if (updateMol.getSmiles() == null || updateMol.getSmiles().isBlank()) {
      throw new StoichiometryException("New molecule requires a SMILES string");
    }

    RSChemElement molecule = RSChemElement.builder().chemElements(updateMol.getSmiles()).build();
    try {
      molecule = rsChemElementManager.save(molecule, user);
    } catch (IOException e) {
      throw new StoichiometryException("Problem saving new molecule from SMILES", e);
    }

    StoichiometryMolecule newMol =
        StoichiometryMolecule.builder()
            .stoichiometry(stoichiometry)
            .rsChemElement(molecule)
            .role(updateMol.getRole())
            .smiles(updateMol.getSmiles())
            .name(updateMol.getName())
            .coefficient(updateMol.getCoefficient())
            .molecularWeight(updateMol.getMolecularWeight())
            .formula(updateMol.getFormula())
            .limitingReagent(updateMol.getLimitingReagent())
            .mass(updateMol.getMass())
            .moles(updateMol.getMoles())
            .expectedAmount(updateMol.getExpectedAmount())
            .actualAmount(updateMol.getActualAmount())
            .actualYield(updateMol.getActualYield())
            .notes(updateMol.getNotes())
            .build();

    stoichiometry.addMolecule(newMol);
  }

  private void applyFieldUpdates(StoichiometryMolecule existing, StoichiometryMoleculeUpdateDTO u) {
    existing.setCoefficient(u.getCoefficient());
    existing.setMass(u.getMass());
    existing.setMoles(u.getMoles());
    existing.setExpectedAmount(u.getExpectedAmount());
    existing.setActualAmount(u.getActualAmount());
    existing.setActualYield(u.getActualYield());
    existing.setLimitingReagent(u.getLimitingReagent());
    existing.setNotes(u.getNotes());
  }

  private void removeMoleculesNotInKeep(
      List<StoichiometryMolecule> existingMolecules, Set<Long> keepIds) {
    if (existingMolecules != null) {
      existingMolecules.removeIf(m -> m.getId() != null && !keepIds.contains(m.getId()));
    }
  }

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

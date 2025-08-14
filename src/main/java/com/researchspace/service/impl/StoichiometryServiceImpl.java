package com.researchspace.service.impl;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Record;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.StoichiometryManager;
import com.researchspace.service.StoichiometryService;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.chemistry.StoichiometryException;
import java.io.IOException;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StoichiometryServiceImpl implements StoichiometryService {

  private final ChemistryService chemistryService;
  private final StoichiometryManager stoichiometryManager;
  private final IPermissionUtils permissionUtils;
  private final ChemistryProvider chemistryProvider;
  private final RSChemElementManager rsChemElementManager;

  @Autowired
  public StoichiometryServiceImpl(
      ChemistryService chemistryService,
      StoichiometryManager stoichiometryManager,
      IPermissionUtils permissionUtils,
      ChemistryProvider chemistryProvider,
      RSChemElementManager rsChemElementManager) {
    this.chemistryService = chemistryService;
    this.stoichiometryManager = stoichiometryManager;
    this.permissionUtils = permissionUtils;
    this.chemistryProvider = chemistryProvider;
    this.rsChemElementManager = rsChemElementManager;
  }

  private boolean hasPermissions(Record record, User user, PermissionType permission) {
    if (record == null) {
      throw new NotFoundException("Record not found");
    }
    return permissionUtils.isPermitted((BaseRecord) record, permission, user);
  }

  @Override
  public Stoichiometry getByParentChemical(long chemId, Integer revision, User user) {
    Optional<Stoichiometry> stoichiometryOpt = stoichiometryManager.findByParentReactionId(chemId);
    if (stoichiometryOpt.isEmpty()) {
      String message =
          String.format(
              "No stoichiometry found for chemical with id %s and revision %s", chemId, revision);
      throw new NotFoundException(message);
    } else if (!hasPermissions(
        stoichiometryOpt.get().getParentReaction().getRecord(), user, PermissionType.READ)) {
      throw new AuthorizationException("User does not have permission to read stoichiometry");
    }
    return stoichiometryOpt.get();
  }

  @Override
  public Stoichiometry create(long chemId, Integer revision, User user) {
    RSChemElement chemical = chemistryService.getChemicalElementByRevision(chemId, revision, user);
    Record owningRecord = chemical != null ? chemical.getRecord() : null;
    if (owningRecord == null) {
      throw new NotFoundException("Record containing chemical with id " + chemId + " not found");
    }
    if (!permissionUtils.isPermitted((BaseRecord) owningRecord, PermissionType.WRITE, user)) {
      throw new AuthorizationException(
          "User not authorised to create stoichiometry for record with id: "
              + owningRecord.getId());
    }
    ;

    Optional<Stoichiometry> existing = stoichiometryManager.findByParentReactionId(chemId);
    if (existing.isPresent()) {
      Stoichiometry e = existing.get();
      throw new StoichiometryException(
          "Stoichiometry already exists for reaction chemId=" + chemId + ", stoichId=" + e.getId());
    }

    Optional<ElementalAnalysisDTO> analysis = chemistryProvider.getStoichiometry(chemical);
    try {
      if (analysis.isEmpty()) {
        throw new StoichiometryException(
            "Unable to generate stoichiometry: problem generating analysis for chemical with ID "
                + chemId);
      }
      return stoichiometryManager.createFromAnalysis(analysis.get(), chemical, user);
    } catch (IOException e) {
      throw new StoichiometryException(
          "Problem while creating new Stoichiometry: " + e.getMessage());
    }
  }

  @Override
  public Stoichiometry update(
      long stoichiometryId, StoichiometryUpdateDTO stoichiometryUpdateDTO, User user) {
    Stoichiometry stoichiometry = stoichiometryManager.get(stoichiometryId);
    Record owningRecord = stoichiometry.getParentReaction().getRecord();
    if (owningRecord == null) {
      throw new NotFoundException(
          "Record containing stoichiometry with id " + stoichiometryId + " not found");
    }
    if (!hasPermissions(owningRecord, user, PermissionType.WRITE)) {
      throw new AuthorizationException("User does not have permission to update stoichiometry");
    }

    return stoichiometryManager.update(stoichiometryUpdateDTO, user);
  }

  @Override
  public void delete(long stoichiometryId, User user) {
    Stoichiometry stoichiometry = stoichiometryManager.get(stoichiometryId);
    Record owningRecord = stoichiometry.getParentReaction().getRecord();
    if (!hasPermissions(owningRecord, user, PermissionType.WRITE)) {
      throw new AuthorizationException("User does not have permission to delete stoichiometry");
    }
    try {
      stoichiometryManager.remove(stoichiometryId);
    } catch (Exception e) {
      throw new NotFoundException("Error deleting stoichiometry with id " + stoichiometryId);
    }
  }

  @Override
  public com.researchspace.model.stoichiometry.StoichiometryMolecule getMoleculeInfo(
      String smiles) {
    if (smiles == null || smiles.isBlank()) {
      throw new javax.ws.rs.NotFoundException("Couldn't retrieve info for provided structure");
    }
    Optional<ElementalAnalysisDTO> analysis = rsChemElementManager.getInfo(smiles);
    if (analysis.isPresent()
        && analysis.get().getMoleculeInfo() != null
        && !analysis.get().getMoleculeInfo().isEmpty()) {
      com.researchspace.model.dtos.chemistry.MoleculeInfoDTO molInfo =
          analysis.get().getMoleculeInfo().get(0);
      // Build a transient StoichiometryMolecule with available fields; do not persist here
      return com.researchspace.model.stoichiometry.StoichiometryMolecule.builder()
          .role(molInfo.getRole())
          .smiles(molInfo.getSmiles())
          .molecularWeight(molInfo.getMass())
          .formula(molInfo.getFormula())
          .limitingReagent(false)
          .build();
    }
    throw new javax.ws.rs.NotFoundException("Couldn't retrieve info for provided structure");
  }
}

package com.researchspace.service.impl;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMapper;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Record;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.StoichiometryManager;
import com.researchspace.service.StoichiometryService;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.chemistry.StoichiometryException;
import java.io.IOException;
import java.util.Optional;
import javax.transaction.Transactional;
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
  private final RecordManager recordManager;

  @Autowired
  public StoichiometryServiceImpl(
      ChemistryService chemistryService,
      StoichiometryManager stoichiometryManager,
      IPermissionUtils permissionUtils,
      ChemistryProvider chemistryProvider,
      RSChemElementManager rsChemElementManager,
      RecordManager recordManager) {
    this.chemistryService = chemistryService;
    this.stoichiometryManager = stoichiometryManager;
    this.permissionUtils = permissionUtils;
    this.chemistryProvider = chemistryProvider;
    this.rsChemElementManager = rsChemElementManager;
    this.recordManager = recordManager;
  }

  private boolean hasPermissions(Record record, User user, PermissionType permission) {
    if (record == null) {
      throw new NotFoundException("Record not found");
    }
    return permissionUtils.isPermitted((BaseRecord) record, permission, user);
  }

  @Override
  @Transactional
  public StoichiometryDTO getById(long stoichiometryId, Long revision, User user) {
    AuditedEntity<Stoichiometry> stoichiometryRevision =
        stoichiometryManager.getRevision(stoichiometryId, revision, user);
    Stoichiometry stoichiometry = stoichiometryRevision.getEntity();
    Record owningRecord = stoichiometry.getRecord();
    if (!hasPermissions(owningRecord, user, PermissionType.READ)) {
      throw new AuthorizationException(
          "User does not have read permissions on document containing stoichiometry");
    }
    return StoichiometryMapper.toDTO(
        stoichiometry, stoichiometryRevision.getRevision().longValue());
  }

  @Override
  public Stoichiometry createFromReaction(long recordId, long chemId, User user) {
    RSChemElement chemical = chemistryService.getChemicalElementByRevision(chemId, null, user);
    Record owningRecord = recordManager.get(recordId);
    if (owningRecord == null) {
      throw new NotFoundException("Record with id " + recordId + " not found");
    }
    if (!permissionUtils.isPermitted((BaseRecord) owningRecord, PermissionType.WRITE, user)) {
      throw new AuthorizationException(
          "User does not have write permissions on document containing stoichiometry");
    }

    Optional<Stoichiometry> existing = stoichiometryManager.findByParentReactionId(chemId);
    if (existing.isPresent()) {
      Stoichiometry e = existing.get();
      throw new StoichiometryException(
          "Stoichiometry already exists for reaction chemId=" + chemId + ", stoichId=" + e.getId());
    }

    try {
      Optional<ElementalAnalysisDTO> analysis = chemistryProvider.getStoichiometry(chemical);
      if (analysis.isEmpty()) {
        throw new StoichiometryException(
            "Unable to generate stoichiometry for chemId="
                + chemId
                + ": chemistry provider returned no analysis");
      }
      return stoichiometryManager.createFromAnalysis(analysis.get(), chemical, owningRecord, user);
    } catch (IOException e) {
      throw new StoichiometryException(
          "Problem while creating new Stoichiometry: " + e.getMessage());
    }
  }

  @Override
  public Stoichiometry createEmpty(long recordId, User user) {
    Record record = recordManager.get(recordId);
    if (record == null) {
      throw new NotFoundException("Record with id " + recordId + " not found");
    }
    if (!permissionUtils.isPermitted((BaseRecord) record, PermissionType.WRITE, user)) {
      throw new AuthorizationException("User does not have write permissions on record");
    }
    return stoichiometryManager.createEmpty(record, user);
  }

  @Override
  public Stoichiometry update(
      long stoichiometryId, StoichiometryUpdateDTO stoichiometryUpdateDTO, User user) {
    Stoichiometry stoichiometry = stoichiometryManager.get(stoichiometryId);
    if (stoichiometry == null) {
      throw new NotFoundException("Stoichiometry with id " + stoichiometryId + " not found");
    }
    Record owningRecord = stoichiometry.getRecord();
    if (owningRecord == null) {
      throw new NotFoundException(
          "Record containing stoichiometry with id " + stoichiometryId + " not found");
    }
    if (!hasPermissions(owningRecord, user, PermissionType.WRITE)) {
      throw new AuthorizationException(
          "User does not have write permissions on document containing stoichiometry");
    }

    return stoichiometryManager.update(stoichiometryUpdateDTO, user);
  }

  @Override
  public void delete(long stoichiometryId, User user) {
    Stoichiometry stoichiometry = stoichiometryManager.get(stoichiometryId);
    if (stoichiometry == null) {
      throw new NotFoundException("Stoichiometry with id " + stoichiometryId + " not found");
    }
    Record owningRecord = stoichiometry.getRecord();
    if (!hasPermissions(owningRecord, user, PermissionType.WRITE)) {
      throw new AuthorizationException(
          "User does not have write permissions on document containing stoichiometry");
    }
    try {
      stoichiometryManager.remove(stoichiometryId);
    } catch (Exception e) {
      throw new StoichiometryException(
          "Error deleting stoichiometry with id " + stoichiometryId, e);
    }
  }

  @Override
  public StoichiometryMolecule getMoleculeInfo(String smiles) {
    if (smiles == null || smiles.isBlank()) {
      throw new StoichiometryException("Couldn't retrieve molecule info for provided structure");
    }
    Optional<ElementalAnalysisDTO> analysis = rsChemElementManager.getInfo(smiles);
    if (analysisExists(analysis)) {
      MoleculeInfoDTO molInfo = analysis.get().getMoleculeInfo().get(0);
      return StoichiometryMolecule.builder()
          .role(molInfo.getRole())
          .smiles(molInfo.getSmiles())
          .molecularWeight(molInfo.getMass())
          .formula(molInfo.getFormula())
          .limitingReagent(false)
          .build();
    }
    throw new NotFoundException("Couldn't retrieve molecule info for provided structure");
  }

  private static boolean analysisExists(Optional<ElementalAnalysisDTO> analysis) {
    return analysis.isPresent()
        && analysis.get().getMoleculeInfo() != null
        && !analysis.get().getMoleculeInfo().isEmpty();
  }
}

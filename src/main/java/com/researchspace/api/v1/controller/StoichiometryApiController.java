package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.StoichiometryApi;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMapper;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.service.AuditManager;
import com.researchspace.service.StoichiometryService;
import org.springframework.beans.factory.annotation.Autowired;

@ApiController
public class StoichiometryApiController extends BaseApiController implements StoichiometryApi {

  private final StoichiometryService stoichiometryService;
  private final AuditManager auditManager;

  @Autowired
  public StoichiometryApiController(
      StoichiometryService stoichiometryService, AuditManager auditManager) {
    this.stoichiometryService = stoichiometryService;
    this.auditManager = auditManager;
  }

  @Override
  public StoichiometryMoleculeDTO getMoleculeInfo(ChemicalDTO chemicalDTO) {
    return StoichiometryMapper.moleculeToDTO(
        stoichiometryService.getMoleculeInfo(chemicalDTO.getChemical()));
  }

  @Override
  public StoichiometryDTO getStoichiometryById(long stoichiometryId, Long revision, User user) {
    return stoichiometryService.getById(stoichiometryId, revision, user);
  }

  @Override
  public StoichiometryDTO saveStoichiometry(long chemId, User user) {
    Stoichiometry stoichiometry = stoichiometryService.create(chemId, user);
    // Get latest revision number after save
    AuditedEntity<Stoichiometry> latestRevision =
        auditManager.getNewestRevisionForEntity(Stoichiometry.class, stoichiometry.getId());
    Long revisionNumber = latestRevision != null ? latestRevision.getRevision().longValue() : null;
    return StoichiometryMapper.toDTO(stoichiometry, revisionNumber);
  }

  @Override
  public StoichiometryDTO updateStoichiometry(
      long stoichiometryId, StoichiometryUpdateDTO stoichiometryUpdateDTO, User user) {
    Stoichiometry stoichiometry =
        stoichiometryService.update(stoichiometryId, stoichiometryUpdateDTO, user);
    // Get latest revision number after updated
    AuditedEntity<Stoichiometry> latestRevision =
        auditManager.getNewestRevisionForEntity(Stoichiometry.class, stoichiometry.getId());
    Long revisionNumber = latestRevision != null ? latestRevision.getRevision().longValue() : null;
    return StoichiometryMapper.toDTO(stoichiometry, revisionNumber);
  }

  @Override
  public Boolean deleteStoichiometry(long stoichiometryId, User user) {
    stoichiometryService.delete(stoichiometryId, user);
    return Boolean.TRUE;
  }
}

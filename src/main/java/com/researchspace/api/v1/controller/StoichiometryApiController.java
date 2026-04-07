package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.StoichiometryApi;
import com.researchspace.api.v1.model.stoichiometry.StockDeductionRequest;
import com.researchspace.api.v1.model.stoichiometry.StockDeductionResult;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMapper;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.service.AuditManager;
import com.researchspace.service.StoichiometryInventoryLinkManager;
import com.researchspace.service.StoichiometryService;
import com.researchspace.service.chemistry.StoichiometryException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@ApiController
public class StoichiometryApiController extends BaseApiController implements StoichiometryApi {

  private final StoichiometryService stoichiometryService;
  private final AuditManager auditManager;
  private final StoichiometryInventoryLinkManager stoichiometryInventoryLinkManager;

  @Autowired
  public StoichiometryApiController(
      StoichiometryService stoichiometryService,
      AuditManager auditManager,
      StoichiometryInventoryLinkManager stoichiometryInventoryLinkManager) {
    this.stoichiometryService = stoichiometryService;
    this.auditManager = auditManager;
    this.stoichiometryInventoryLinkManager = stoichiometryInventoryLinkManager;
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
  public StoichiometryDTO createStoichiometry(Long recordId, Long chemId, User user) {
    if (recordId == null) {
      throw new StoichiometryException("recordId must be provided");
    }

    Stoichiometry stoichiometry;
    if (chemId != null) {
      stoichiometry = stoichiometryService.createFromReaction(recordId, chemId, user);
    } else {
      stoichiometry = stoichiometryService.createEmpty(recordId, user);
    }
    // Get the latest revision number after save
    return StoichiometryMapper.toDTO(stoichiometry, getLatestRevisionNumber(stoichiometry.getId()));
  }

  @Override
  public StoichiometryDTO updateStoichiometry(
      long stoichiometryId, StoichiometryUpdateDTO stoichiometryUpdateDTO, User user) {
    Stoichiometry stoichiometry =
        stoichiometryService.update(stoichiometryId, stoichiometryUpdateDTO, user);
    // Get latest revision number after updated
    return StoichiometryMapper.toDTO(stoichiometry, getLatestRevisionNumber(stoichiometry.getId()));
  }

  @Override
  public Boolean deleteStoichiometry(long stoichiometryId, User user) {
    stoichiometryService.delete(stoichiometryId, user);
    return Boolean.TRUE;
  }

  @Override
  public StockDeductionResult deductStock(StockDeductionRequest request, User user) {
    long stoichiometryId = request.getStoichiometryId();
    List<Long> linkIds = request.getLinkIds();
    StockDeductionResult result =
        stoichiometryInventoryLinkManager.deductStock(stoichiometryId, linkIds, user);
    result.setRevisionNumber(getLatestRevisionNumber(stoichiometryId));
    return result;
  }

  private Long getLatestRevisionNumber(long stoichiometryId) {
    AuditedEntity<Stoichiometry> latestRevision =
        auditManager.getNewestRevisionForEntity(Stoichiometry.class, stoichiometryId);
    return latestRevision != null ? latestRevision.getRevision().longValue() : null;
  }
}

package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.StoichiometryApi;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMapper;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Record;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.StoichiometryManager;
import com.researchspace.service.chemistry.StoichiometryException;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@ApiController
public class StoichiometryApiController extends BaseApiController implements StoichiometryApi {

  @Autowired private ChemistryService chemistryService;
  @Autowired private StoichiometryManager stoichiometryManager;
  @Autowired private IPermissionUtils permissionUtils;

  @Override
  public StoichiometryMoleculeDTO getMoleculeInfo(ChemicalDTO chemicalDTO) {
    Optional<ElementalAnalysisDTO> analysis =
        chemistryService.getMoleculeInfo(chemicalDTO.getChemical());
    if (analysis.isPresent()) {
      ElementalAnalysisDTO dto = analysis.get();
      MoleculeInfoDTO molInfo = null;
      if (dto.getMoleculeInfo() != null && !dto.getMoleculeInfo().isEmpty()) {
        molInfo = dto.getMoleculeInfo().get(0);
      }
      if (molInfo != null) {
        return StoichiometryMapper.moleculeInfoToDTO(molInfo);
      }
      log.info(
          "Molecule analysis present but no molecule entries for smiles: {}",
          chemicalDTO.getChemical());
      throw new NotFoundException("Couldn't retrieve info for provided structure");
    } else {
      log.info("Couldn't retrieve molecule info for smiles: {}", chemicalDTO.getChemical());
      throw new NotFoundException("Couldn't retrieve info for provided structure");
    }
  }

  @Override
  public StoichiometryDTO getStoichiometry(long chemId, Integer revision, User user) {
    if (!userCanEditDocContainingStoichiometry(user, chemId)) {
      throw new StoichiometryException("User does not have permission to retrieve stoichiometry");
    }
    Optional<Stoichiometry> stoichiometry =
        chemistryService.getStoichiometry(chemId, revision, user);
    if (stoichiometry.isEmpty()) {
      String message =
          String.format(
              "No stoichiometry found for chemical with id %s and revision %s", chemId, revision);
      log.info(message);
      throw new NotFoundException(message);
    }
    return StoichiometryMapper.toDTO(stoichiometry.get());
  }

  @Override
  public StoichiometryDTO saveStoichiometry(long chemId, Integer revision, User user) {
    if (!userCanEditDocContainingStoichiometry(user, chemId)) {
      throw new StoichiometryException("User does not have permission to save stoichiometry");
    }
    try {
      Stoichiometry stoichiometry = chemistryService.createStoichiometry(chemId, revision, user);
      return StoichiometryMapper.toDTO(stoichiometry);
    } catch (StoichiometryException e) {
      String message =
          String.format(
              "Problem creating stoichiometry for chemId: %s. %s", chemId, e.getMessage());
      log.error(message, e);
      throw new IllegalArgumentException(message, e);
    }
  }

  @Override
  public StoichiometryDTO updateStoichiometry(
      long stoichiometryId, StoichiometryUpdateDTO stoichiometryUpdateDTO, User user) {
    if (!userCanEditDocContainingStoichiometry(user, stoichiometryId)) {
      throw new StoichiometryException("User does not have permission to update stoichiometry");
    }
    try {
      Stoichiometry stoichiometry =
          chemistryService.updateStoichiometry(stoichiometryUpdateDTO, user);
      return StoichiometryMapper.toDTO(stoichiometry);
    } catch (StoichiometryException e) {
      String message = e.getMessage();
      log.error("Stoichiometry error updating id {}: {}", stoichiometryId, message);
      throw new IllegalArgumentException("Error updating stoichiometry: " + message, e);
    }
  }

  @Override
  public Boolean deleteStoichiometry(long stoichiometryId, User user) {
    if (!userCanEditDocContainingStoichiometry(user, stoichiometryId)) {
      throw new StoichiometryException("User does not have permission to delete stoichiometry");
    }
    boolean success = chemistryService.deleteStoichiometry(stoichiometryId, user);
    if (success) {
      return Boolean.TRUE;
    } else {
      throw new NotFoundException("Error deleting stoichiometry with id " + stoichiometryId);
    }
  }

  private boolean userCanEditDocContainingStoichiometry(User user, long stoichiometryId) {
    Stoichiometry stoich = stoichiometryManager.get(stoichiometryId);
    if (stoich == null || stoich.getParentReaction() == null) {
      throw new NotFoundException("Stoichiometry not found");
    }
    Record owningRecord = stoich.getParentReaction().getRecord();
    if (owningRecord == null) {
      throw new NotFoundException("Record not found");
    }
    return permissionUtils.isPermitted(owningRecord, PermissionType.WRITE, user);
  }
}

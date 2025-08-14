package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.StoichiometryApi;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMapper;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.service.StoichiometryService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApiController
public class StoichiometryApiController extends BaseApiController implements StoichiometryApi {

  private final StoichiometryService stoichiometryService;

  public StoichiometryApiController(StoichiometryService stoichiometryService) {
    this.stoichiometryService = stoichiometryService;
  }

  @Override
  public StoichiometryMoleculeDTO getMoleculeInfo(ChemicalDTO chemicalDTO) {
    return StoichiometryMapper.moleculeToDTO(
        stoichiometryService.getMoleculeInfo(chemicalDTO.getChemical()));
  }

  @Override
  public StoichiometryDTO getStoichiometry(long chemId, Integer revision, User user) {
    Stoichiometry stoichiometry = stoichiometryService.getByParentChemical(chemId, revision, user);
    return StoichiometryMapper.toDTO(stoichiometry);
  }

  @Override
  public StoichiometryDTO saveStoichiometry(long chemId, Integer revision, User user) {
    Stoichiometry stoichiometry = stoichiometryService.create(chemId, revision, user);
    return StoichiometryMapper.toDTO(stoichiometry);
  }

  @Override
  public StoichiometryDTO updateStoichiometry(
      long stoichiometryId, StoichiometryUpdateDTO stoichiometryUpdateDTO, User user) {
    Stoichiometry stoichiometry =
        stoichiometryService.update(stoichiometryId, stoichiometryUpdateDTO, user);
    return StoichiometryMapper.toDTO(stoichiometry);
  }

  @Override
  public Boolean deleteStoichiometry(long stoichiometryId, User user) {
    stoichiometryService.delete(stoichiometryId, user);
    return Boolean.TRUE;
  }
}

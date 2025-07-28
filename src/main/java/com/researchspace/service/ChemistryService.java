package com.researchspace.service;

import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.Stoichiometry;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemConversionInputDto;
import com.researchspace.model.dtos.chemistry.ChemElementDataDto;
import com.researchspace.model.dtos.chemistry.ChemElementImageUpdateDto;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ChemicalImageDTO;
import com.researchspace.model.dtos.chemistry.ConvertedStructureDto;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.service.impl.RSChemService.ChemicalSearchResults;
import com.researchspace.webapp.controller.RSChemController.ChemEditorInputDto;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface ChemistryService {

  RSChemElement saveChemicalElement(ChemicalDataDTO chemicalData, User user) throws IOException;

  ChemElementDataDto createChemicalElement(ChemElementDataDto chemicalData, User user)
      throws IOException;

  void saveChemicalImage(ChemicalImageDTO chemicalImage, User user) throws IOException;

  ChemicalSearchResults searchChemicals(
      String chemQuery, String searchType, int pageNumber, int pageSize, User user);

  ConvertedStructureDto convert(ChemConversionInputDto input);

  ChemElementDataDto getChemicalsForFile(Long chemistryFileId, User user);

  ChemElementDataDto getUpdatableChemicals(Long chemistryFileId, User user);

  List<RSChemElement> updateChemicalElementImages(ChemElementImageUpdateDto dto, User user)
      throws IOException;

  List<RSChemElement> getChemicalElementsForFile(Long chemistryFileId, User user);

  List<String> getSupportedFileTypes();

  RSChemElement getChemicalElementByRevision(Long chemId, Integer revision, User user);

  ChemEditorInputDto getChemicalEditorInput(long chemId, Integer revision, User user);

  Optional<ElementalAnalysisDTO> getElementalAnalysis(long chemId, Integer revision, User user);

  /**
   * Get stoichiometry information for a chemical element.
   *
   * @param chemId the ID of the chemical element
   * @param revision the revision of the chemical element, or null for the latest revision
   * @param user the user requesting the information
   * @return an Optional containing the stoichiometry information, or empty if none could be found
   */
  Optional<ElementalAnalysisDTO> getStoichiometry(long chemId, Integer revision, User user);

  /**
   * Get stoichiometry information for a chemical element and save it to the database.
   *
   * @param chemId the ID of the chemical element
   * @param revision the revision of the chemical element, or null for the latest revision
   * @param user the user requesting the information
   * @return the saved Stoichiometry entity, or null if no stoichiometry information could be found
   */
  Stoichiometry getStoichiometryAndSave(long chemId, Integer revision, User user);

  /**
   * Update stoichiometry information in the database.
   *
   * @param stoichiometryId the ID of the stoichiometry to update
   * @param stoichiometryDTO the updated stoichiometry information
   * @param user the user updating the information
   * @return the updated Stoichiometry entity
   */
  Stoichiometry updateStoichiometry(
      long stoichiometryId, StoichiometryDTO stoichiometryDTO, User user);

  String getChemicalFileContents(long chemId, Integer revision, User subject);

  List<RSChemElement> getAllChemicalsByFormat(ChemElementsFormat format);
}

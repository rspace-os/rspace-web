package com.researchspace.service;

import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemConversionInputDto;
import com.researchspace.model.dtos.chemistry.ChemElementDataDto;
import com.researchspace.model.dtos.chemistry.ChemElementImageUpdateDto;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ChemicalImageDTO;
import com.researchspace.model.dtos.chemistry.ConvertedStructureDto;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.stoichiometry.Stoichiometry;
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

  Optional<Stoichiometry> getStoichiometry(long chemId, Integer revision, User user);

  Stoichiometry createStoichiometry(long chemId, Integer revision, User user);

  Stoichiometry updateStoichiometry(StoichiometryUpdateDTO stoichiometryUpdateDTO, User user);

  boolean deleteStoichiometry(long stoichiometryId, User user);

  String getChemicalFileContents(long chemId, Integer revision, User subject);

  List<RSChemElement> getAllChemicalsByFormat(ChemElementsFormat format);
}

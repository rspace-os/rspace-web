package com.researchspace.service.impl;

import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.ChemSearchedItem;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.Stoichiometry;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemConversionInputDto;
import com.researchspace.model.dtos.chemistry.ChemElementDataDto;
import com.researchspace.model.dtos.chemistry.ChemElementImageUpdateDto;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalExportType;
import com.researchspace.model.dtos.chemistry.ChemicalImageDTO;
import com.researchspace.model.dtos.chemistry.ConvertedStructureDto;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.record.Breadcrumb;
import com.researchspace.model.record.BreadcrumbGenerator;
import com.researchspace.model.record.Folder;
import com.researchspace.service.AuditManager;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.EcatChemistryFileManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.StoichiometryManager;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.webapp.controller.RSChemController.ChemEditorInputDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class RSChemService implements ChemistryService {

  private final RSChemElementManager rsChemElementManager;

  private final FolderManager folderManager;

  private final BreadcrumbGenerator breadcrumbGenerator;

  private final ChemistryProvider chemistryProvider;

  private final EcatChemistryFileManager chemistryFileManager;

  private final AuditManager auditManager;

  private final StoichiometryManager stoichiometryManager;

  @Autowired
  public RSChemService(
      RSChemElementManager rsChemElementManager,
      @Qualifier("folderManagerImpl") FolderManager folderManager,
      BreadcrumbGenerator breadcrumbGenerator,
      ChemistryProvider chemistryProvider,
      EcatChemistryFileManager chemistryFileManager,
      AuditManager auditManager,
      StoichiometryManager stoichiometryManager) {
    this.rsChemElementManager = rsChemElementManager;
    this.folderManager = folderManager;
    this.breadcrumbGenerator = breadcrumbGenerator;
    this.chemistryProvider = chemistryProvider;
    this.chemistryFileManager = chemistryFileManager;
    this.auditManager = auditManager;
    this.stoichiometryManager = stoichiometryManager;
  }

  @Override
  public ChemElementDataDto createChemicalElement(ChemElementDataDto chemicalElement, User user)
      throws IOException {
    RSChemElement rsChemElement = rsChemElementManager.createChemElement(chemicalElement, user);
    chemicalElement.setRsChemElementId(rsChemElement.getId());
    return chemicalElement;
  }

  @Override
  public RSChemElement saveChemicalElement(ChemicalDataDTO chemicalData, User user)
      throws IOException {
    RSChemElement rsChemElement = rsChemElementManager.saveChemElement(chemicalData, user);
    rsChemElement.setDataImage(null);
    return rsChemElement;
  }

  @Override
  public void saveChemicalImage(ChemicalImageDTO chemicalImage, User user) throws IOException {
    rsChemElementManager.saveChemImage(chemicalImage, user);
  }

  @Override
  public ChemicalSearchResults searchChemicals(
      String chemQuery, String searchType, int pageNumber, int pageSize, User user) {

    int start = (pageNumber * pageSize);
    /* let's limit processed results to one full page after current page, and one more hit */
    int searchResultLimit = start + (2 * pageSize) + 1;
    List<ChemSearchedItem> searchResults =
        rsChemElementManager.search(chemQuery, searchType, searchResultLimit, user);
    List<ChemSearchedItem> pagedRecords = new ArrayList<>();
    int end = Math.min(searchResults.size() - 1, (pageNumber * pageSize) + pageSize - 1);

    for (int i = start; i <= end; i++) {
      pagedRecords.add(searchResults.get(i));
    }

    Map<Long, Breadcrumb> searchResultToBreadcrumb = new HashMap<>();
    Folder rootRecord = folderManager.getRootFolderForUser(user);
    for (ChemSearchedItem chemSearchedItem : pagedRecords) {
      Breadcrumb breadcrumb =
          breadcrumbGenerator.generateBreadcrumbToHome(
              chemSearchedItem.getRecord(), rootRecord, null);
      searchResultToBreadcrumb.put(chemSearchedItem.getRecordId(), breadcrumb);
    }

    Integer totalHitCount = null;
    Integer totalPageCount = null;
    /* if number of found results is at limit or more, the results calculation was probably cut off;
    don't populate total size and page count */
    if (searchResults.size() < searchResultLimit) {
      totalHitCount = searchResults.size();
      totalPageCount = (int) Math.ceil(((double) totalHitCount) / ((double) pageSize));
    }

    return new ChemicalSearchResults(
        pagedRecords, start, end, searchResultToBreadcrumb, totalHitCount, totalPageCount);
  }

  @Override
  public ConvertedStructureDto convert(ChemConversionInputDto input) {
    String converted =
        chemistryProvider.convertToDefaultFormat(input.getStructure(), input.getInputFormat());
    return ConvertedStructureDto.builder()
        .structure(converted)
        .format(input.getParameters())
        .build();
  }

  @Override
  public ChemElementDataDto getChemicalsForFile(Long chemistryFileId, User user) {
    EcatChemistryFile chemistryFile = chemistryFileManager.get(chemistryFileId, user);
    return ChemElementDataDto.builder()
        .ecatChemFileId(chemistryFile.getId())
        .fileName(chemistryFile.getName())
        .chemString(
            chemistryProvider.convertToDefaultFormat(
                chemistryFile.getChemString(), chemistryFile.getExtension()))
        .chemElementsFormat(chemistryProvider.defaultFormat().getLabel())
        .build();
  }

  @Override
  public ChemElementDataDto getUpdatableChemicals(Long chemistryFileId, User user) {
    EcatChemistryFile chemistryFile = chemistryFileManager.get(chemistryFileId, user);
    List<RSChemElement> chemicalElements =
        rsChemElementManager.getRSChemElementsLinkedToFile(chemistryFileId, user);
    return ChemElementDataDto.builder()
        .ecatChemFileId(chemistryFile.getId())
        .fileName(chemistryFile.getName())
        .chemString(chemistryFile.getChemString())
        .chemElementsFormat(chemistryProvider.defaultFormat().getLabel())
        .rsChemElements(chemicalElements)
        .build();
  }

  @Override
  public List<RSChemElement> updateChemicalElementImages(ChemElementImageUpdateDto dto, User user)
      throws IOException {
    List<RSChemElement> chemElementsToUpdate =
        rsChemElementManager.getRSChemElementsLinkedToFile(dto.getEcatChemFileId(), user);
    EcatChemistryFile chemistryFile = chemistryFileManager.get(dto.getEcatChemFileId(), user);
    return rsChemElementManager.updateChemImages(
        chemistryFile,
        chemElementsToUpdate,
        new ChemicalExportFormat(ChemicalExportType.PNG, dto.getHeight(), dto.getWidth(), null),
        user);
  }

  @Override
  public List<RSChemElement> getChemicalElementsForFile(Long chemistryFileId, User user) {
    return rsChemElementManager.getRSChemElementsLinkedToFile(chemistryFileId, user);
  }

  @Override
  public List<String> getSupportedFileTypes() {
    return rsChemElementManager.getSupportedTypes();
  }

  @Override
  public RSChemElement getChemicalElementByRevision(Long chemId, Integer revision, User user) {
    if (revision == null) {
      return rsChemElementManager.get(chemId, user);
    } else {
      return auditManager.getObjectForRevision(RSChemElement.class, chemId, revision).getEntity();
    }
  }

  @Override
  public ChemEditorInputDto getChemicalEditorInput(long chemId, Integer revision, User user) {
    RSChemElement chemical = getChemicalElementByRevision(chemId, revision, user);
    if (chemical == null) {
      return null;
    }
    return new ChemEditorInputDto(
        chemical.getId(),
        chemical.getChemElements(),
        chemical.getChemElementsFormat(),
        chemical.getEcatChemFileId());
  }

  @Override
  public Optional<ElementalAnalysisDTO> getElementalAnalysis(
      long chemId, Integer revision, User user) {
    RSChemElement chemical = getChemicalElementByRevision(chemId, revision, user);
    if (chemical == null) {
      return null;
    }
    Optional<ElementalAnalysisDTO> analysis = rsChemElementManager.getInfo(chemical);
    if (analysis.isPresent()) {
      ElementalAnalysisDTO elementalAnalysis = analysis.get();
      if (elementalAnalysis.isReaction()) {
        elementalAnalysis.setAdditionalMetadata(chemical.getMetadata());
      } else {
        for (MoleculeInfoDTO molecule : elementalAnalysis.getMolecules()) {
          molecule.setAdditionalMetadata(chemical.getMetadata());
        }
      }
    }
    return analysis;
  }

  @Override
  public String getChemicalFileContents(long chemId, Integer revision, User subject) {
    RSChemElement chemical = getChemicalElementByRevision(chemId, revision, subject);
    EcatChemistryFile file = chemistryFileManager.get(chemical.getEcatChemFileId(), subject);
    if (file != null) {
      return file.getChemString();
    }
    return "";
  }

  @Override
  public List<RSChemElement> getAllChemicalsByFormat(ChemElementsFormat format) {
    return rsChemElementManager.getAllChemElementsByFormat(format);
  }

  @Override
  public Optional<Stoichiometry> getStoichiometry(long chemId, Integer revision, User user) {
    RSChemElement chemical = getChemicalElementByRevision(chemId, revision, user);
    if (chemical == null) {
      return null;
    }
    Optional<ElementalAnalysisDTO> analysis = chemistryProvider.getStoichiometry(chemical);
    if (analysis.isPresent()) {
      ElementalAnalysisDTO stoichiometryAnalysis = analysis.get();
      if (stoichiometryAnalysis.isReaction()) {
        stoichiometryAnalysis.setAdditionalMetadata(chemical.getMetadata());
      } else {
        for (MoleculeInfoDTO molecule : stoichiometryAnalysis.getMoleculeInfo()) {
          molecule.setAdditionalMetadata(chemical.getMetadata());
        }
      }
    }
    try {
      return Optional.ofNullable(
          stoichiometryManager.createFromAnalysis(analysis.get(), chemical, user));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Stoichiometry getStoichiometryAndSave(long chemId, Integer revision, User user) {
    RSChemElement chemical = getChemicalElementByRevision(chemId, revision, user);
    if (chemical == null) {
      return null;
    }

    Stoichiometry existingStoichiometry = stoichiometryManager.findByParentReactionId(chemId);
    if (existingStoichiometry != null) {
      return existingStoichiometry;
    }

    Optional<ElementalAnalysisDTO> analysis = chemistryProvider.getStoichiometry(chemical);
    if (analysis.isEmpty()) {
      return null;
    }

    try {
      return stoichiometryManager.createFromAnalysis(analysis.get(), chemical, user);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create stoichiometry", e);
    }
  }

  @Override
  public Stoichiometry updateStoichiometry(
      long stoichiometryId, StoichiometryDTO stoichiometryDTO, User user) {
    return stoichiometryManager.update(stoichiometryId, stoichiometryDTO, user);
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ChemicalSearchResults {
    List<ChemSearchedItem> pagedRecords = new ArrayList<>();
    int startHit;
    int endHit;
    Map<Long, Breadcrumb> breadcrumbMap;
    Integer totalHitCount;
    Integer totalPageCount;
  }
}

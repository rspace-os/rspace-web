package com.researchspace.service.impl;

import com.researchspace.model.ChemSearchedItem;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.RSChemElement;
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
import com.researchspace.model.record.Breadcrumb;
import com.researchspace.model.record.BreadcrumbGenerator;
import com.researchspace.model.record.Folder;
import com.researchspace.service.AuditManager;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.EcatChemistryFileManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RSChemElementManager;
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

  @Autowired
  public RSChemService(
      RSChemElementManager rsChemElementManager,
      @Qualifier("folderManagerImpl") FolderManager folderManager,
      BreadcrumbGenerator breadcrumbGenerator,
      ChemistryProvider chemistryProvider,
      EcatChemistryFileManager chemistryFileManager,
      AuditManager auditManager) {
    this.rsChemElementManager = rsChemElementManager;
    this.folderManager = folderManager;
    this.breadcrumbGenerator = breadcrumbGenerator;
    this.chemistryProvider = chemistryProvider;
    this.chemistryFileManager = chemistryFileManager;
    this.auditManager = auditManager;
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
    List<ChemSearchedItem> searchResults = rsChemElementManager.search(chemQuery, searchType, user);
    List<ChemSearchedItem> pagedRecords = new ArrayList<>();
    int start = (pageNumber * pageSize);
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

    int totalHitCount = searchResults.size();

    int totalPageCount = (int) Math.ceil(((double) totalHitCount) / ((double) pageSize));
    return new ChemicalSearchResults(
        pagedRecords, start, end, searchResultToBreadcrumb, totalHitCount, totalPageCount);
  }

  @Override
  public ConvertedStructureDto convert(ChemConversionInputDto input) {
    String converted = chemistryProvider.convert(input.getStructure());
    return ConvertedStructureDto.builder()
        .structure(converted)
        .format(input.getParameters())
        .build();
  }

  @Override
  public ChemElementDataDto getChemicalsForFile(Long chemistryFileId, User user) {
    EcatChemistryFile chemistryFile = chemistryFileManager.get(chemistryFileId, user);
    return chemistryProvider.getChemicalsForFile(chemistryFile);
  }

  @Override
  public ChemElementDataDto getUpdatableChemicals(Long chemistryFileId, User user) {
    EcatChemistryFile chemistryFile = chemistryFileManager.get(chemistryFileId, user);
    List<RSChemElement> chemicalElements =
        rsChemElementManager.getRSChemElementsLinkedToFile(chemistryFileId, user);
    return chemistryProvider.getUpdatableChemicals(chemistryFile, chemicalElements);
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
        chemical.getId(), chemical.getChemElements(), chemical.getChemElementsFormat());
  }

  @Override
  public Optional<ElementalAnalysisDTO> getElementalAnalysis(
      long chemId, Integer revision, User user) {
    RSChemElement chemical = getChemicalElementByRevision(chemId, revision, user);
    return rsChemElementManager.getInfo(chemical);
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ChemicalSearchResults {
    List<ChemSearchedItem> pagedRecords;
    int startHit;
    int endHit;
    Map<Long, Breadcrumb> breadcrumbMap;
    int totalHitCount;
    int totalPageCount;
  }
}

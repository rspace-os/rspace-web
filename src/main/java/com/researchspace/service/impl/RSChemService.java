package com.researchspace.service.impl;

import com.researchspace.model.ChemElementsFormat;
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
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.record.Breadcrumb;
import com.researchspace.model.record.BreadcrumbGenerator;
import com.researchspace.model.record.Folder;
import com.researchspace.service.AuditManager;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.EcatChemistryFileManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.inventory.InventoryStructureHit;
import com.researchspace.service.inventory.InventoryStructureSearchManager;
import com.researchspace.webapp.controller.RSChemController.ChemEditorInputDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
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

  private final InventoryStructureSearchManager inventoryStructureSearchManager;

  private final SystemPropertyPermissionManager systemPropertyPermissionManager;

  @Autowired
  public RSChemService(
      RSChemElementManager rsChemElementManager,
      @Qualifier("folderManagerImpl") FolderManager folderManager,
      BreadcrumbGenerator breadcrumbGenerator,
      @Qualifier("chemistryProvider") ChemistryProvider chemistryProvider,
      EcatChemistryFileManager chemistryFileManager,
      AuditManager auditManager,
      InventoryStructureSearchManager inventoryStructureSearchManager,
      SystemPropertyPermissionManager systemPropertyPermissionManager) {
    this.rsChemElementManager = rsChemElementManager;
    this.folderManager = folderManager;
    this.breadcrumbGenerator = breadcrumbGenerator;
    this.chemistryProvider = chemistryProvider;
    this.chemistryFileManager = chemistryFileManager;
    this.auditManager = auditManager;
    this.inventoryStructureSearchManager = inventoryStructureSearchManager;
    this.systemPropertyPermissionManager = systemPropertyPermissionManager;
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
    List<Long> chemIds = chemistryProvider.search(chemQuery, searchType).getChemicalHits();
    List<ChemSearchedItem> searchResults =
        rsChemElementManager.searchChemIds(chemIds, searchResultLimit, user);
    /* below the limit, every document and Gallery hit is known, so Inventory hits can follow them;
    at the limit, this page and the next are all document and Gallery hits */
    boolean elnTotalKnown = searchResults.size() < searchResultLimit;
    List<InventoryStructureHit> inventoryHits =
        elnTotalKnown && !chemIds.isEmpty() && isInventoryIncluded(user)
            ? inventoryStructureSearchManager.findReadableStructureHits(chemIds, user)
            : List.of();
    int totalKnownHits = searchResults.size() + inventoryHits.size();
    int end = Math.min(totalKnownHits - 1, start + pageSize - 1);

    List<ChemSearchedItem> pagedRecords = new ArrayList<>();
    List<InventoryStructureHit> pagedInventoryHits = new ArrayList<>();
    for (int i = start; i <= end; i++) {
      if (i < searchResults.size()) {
        pagedRecords.add(searchResults.get(i));
      } else {
        pagedInventoryHits.add(inventoryHits.get(i - searchResults.size()));
      }
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
    if (elnTotalKnown) {
      totalHitCount = totalKnownHits;
      totalPageCount = (int) Math.ceil(((double) totalHitCount) / ((double) pageSize));
    }

    return new ChemicalSearchResults(
        pagedRecords,
        pagedInventoryHits,
        start,
        end,
        searchResultToBreadcrumb,
        totalHitCount,
        totalPageCount);
  }

  private boolean isInventoryIncluded(User user) {
    return systemPropertyPermissionManager.isPropertyAllowed(
            user, SystemPropertyName.INVENTORY_AVAILABLE)
        && inventoryStructureSearchManager.isStructureSearchAvailable(user);
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
    Optional<ElementalAnalysisDTO> analysis =
        rsChemElementManager.getInfo(chemical.getChemElements());
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

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ChemicalSearchResults {
    List<ChemSearchedItem> pagedRecords = new ArrayList<>();

    /** Inventory hits on this page, listed after {@link #pagedRecords}. */
    List<InventoryStructureHit> pagedInventoryHits = new ArrayList<>();

    int startHit;
    int endHit;
    Map<Long, Breadcrumb> breadcrumbMap;
    Integer totalHitCount;
    Integer totalPageCount;
  }
}

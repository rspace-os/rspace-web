package com.researchspace.chemistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.ChemSearchedItem;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.chemistry.ChemConversionInputDto;
import com.researchspace.model.dtos.chemistry.ChemElementDataDto;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ChemicalSearchResultsDTO;
import com.researchspace.model.dtos.chemistry.ConvertedStructureDto;
import com.researchspace.model.inventory.InventoryRecord.InventoryRecordType;
import com.researchspace.model.record.BreadcrumbGenerator;
import com.researchspace.service.AuditManager;
import com.researchspace.service.EcatChemistryFileManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.impl.RSChemService;
import com.researchspace.service.impl.RSChemService.ChemicalSearchResults;
import com.researchspace.service.inventory.InventoryStructureHit;
import com.researchspace.service.inventory.InventoryStructureSearchManager;
import com.researchspace.testutils.TestFactory;
import com.researchspace.webapp.controller.RSChemController.ChemEditorInputDto;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ChemistryServiceTest {

  @Mock RSChemElementManager chemElementManager;

  @Mock FolderManager folderManager;

  @Mock BreadcrumbGenerator breadcrumbGenerator;

  @Mock ChemistryProvider chemistryProvider;

  @Mock AuditManager auditManager;

  @Mock EcatChemistryFileManager fileManager;

  @Mock InventoryStructureSearchManager inventoryStructureSearchManager;

  @Mock SystemPropertyPermissionManager systemPropertyPermissionManager;

  @InjectMocks RSChemService chemistryService;

  private static final List<Long> CHEM_IDS = List.of(11L, 12L, 13L);

  User user;

  @BeforeEach
  public void setup() {
    user = TestFactory.createAnyUser("someUser");
  }

  @Test
  public void createChemicalSetsId() throws Exception {
    ChemElementDataDto toBeSaved = ChemElementDataDto.builder().build();
    RSChemElement saved = RSChemElement.builder().id(123L).build();
    when(chemElementManager.createChemElement(toBeSaved, user)).thenReturn(saved);

    ChemElementDataDto savedDto = chemistryService.createChemicalElement(toBeSaved, user);
    Assertions.assertEquals(saved.getId(), savedDto.getRsChemElementId());
  }

  @Test
  public void saveChemicalElementNullsImageOnReturnObject() throws Exception {
    ChemicalDataDTO toBeSaved = ChemicalDataDTO.builder().imageBase64("SomeImage").build();
    RSChemElement saved =
        RSChemElement.builder().dataImage("SomeImage".getBytes(StandardCharsets.UTF_8)).build();
    when(chemElementManager.saveChemElement(toBeSaved, user)).thenReturn(saved);

    RSChemElement actual = chemistryService.saveChemicalElement(toBeSaved, user);
    assertNull(actual.getDataImage());
  }

  @Test
  public void searchChemicals() {
    String chemQuery = "CC";
    String searchType = "substructure";

    // chem manager finding 10 results for any search
    List<ChemSearchedItem> searchedItems = createSearchItems(10);
    stubChemistryServiceHits(chemQuery, searchType);
    when(chemElementManager.searchChemIds(eq(CHEM_IDS), anyInt(), eq(user)))
        .thenReturn(searchedItems);

    // let's test different pagination & result limiting

    // 1st page when splitting for pages of 5
    ChemicalSearchResults actual =
        chemistryService.searchChemicals(chemQuery, searchType, 0, 5, user);
    assertEquals(0, actual.getStartHit());
    assertEquals(4, actual.getEndHit());
    assertEquals(10, actual.getTotalHitCount());
    assertEquals(2, actual.getTotalPageCount());
    assertThat(actual.getPagedRecords()).hasSize(5);
    assertThat(searchedItems).containsAll(actual.getPagedRecords());

    // 1st page when splitting for pages of 4
    actual = chemistryService.searchChemicals(chemQuery, searchType, 0, 4, user);
    assertEquals(0, actual.getStartHit());
    assertEquals(3, actual.getEndHit());
    assertNull(actual.getTotalHitCount()); // total calculations were cut off
    assertNull(actual.getTotalPageCount()); // total calculations were cut off
    assertThat(actual.getPagedRecords()).hasSize(4);

    // 2nd page when splitting for pages of 4
    actual = chemistryService.searchChemicals(chemQuery, searchType, 1, 4, user);
    assertEquals(4, actual.getStartHit());
    assertEquals(7, actual.getEndHit());
    assertEquals(10, actual.getTotalHitCount()); // no cutoff at page-before-last
    assertEquals(3, actual.getTotalPageCount()); // no cutoff at page-before-last
    assertThat(actual.getPagedRecords()).hasSize(4);

    // 3rd page when splitting for pages of 4
    actual = chemistryService.searchChemicals(chemQuery, searchType, 2, 4, user);
    assertEquals(8, actual.getStartHit());
    assertEquals(9, actual.getEndHit());
    assertEquals(10, actual.getTotalHitCount()); // no cutoff at last page
    assertEquals(3, actual.getTotalPageCount()); // no cutoff at last page
    assertThat(actual.getPagedRecords()).hasSize(2);
  }

  @Test
  public void convertedStructureCreated() {
    String inputStructure = "CC";
    String converted = "CC-converted";
    String params = "some-params";
    String inputFormat = "some-format";
    when(chemistryProvider.convertToDefaultFormat(inputStructure, inputFormat))
        .thenReturn(converted);

    ChemConversionInputDto conversionInput = new ChemConversionInputDto();
    conversionInput.setStructure(inputStructure);
    conversionInput.setInputFormat(inputFormat);
    conversionInput.setParameters(params);
    ConvertedStructureDto convertedStructure = chemistryService.convert(conversionInput);

    assertEquals(converted, convertedStructure.getStructure());
    assertEquals(params, convertedStructure.getFormat());
  }

  @Test
  public void getChemicalElementDefaultsToLatestRevision() {
    chemistryService.getChemicalElementByRevision(123L, null, user);
    verify(chemElementManager, times(1)).get(123L, user);
    verifyNoInteractions(auditManager);
  }

  @Test
  public void getChemicalElementGetsPreviousRevision() {
    RSChemElement auditedChemical = new RSChemElement();
    AuditedEntity<RSChemElement> auditedEntity = new AuditedEntity<>();
    auditedEntity.setEntity(auditedChemical);
    when(auditManager.getObjectForRevision(RSChemElement.class, 123L, 1)).thenReturn(auditedEntity);
    chemistryService.getChemicalElementByRevision(123L, 1, user);
    verify(auditManager, times(1)).getObjectForRevision(RSChemElement.class, 123L, 1);
    verifyNoInteractions(chemElementManager);
  }

  @Test
  public void getChemicalEditorInputReturnsDto() {
    long chemicalId = 123L;
    RSChemElement chemElement = new RSChemElement();
    chemElement.setId(chemicalId);
    when(chemElementManager.get(chemicalId, user)).thenReturn(chemElement);

    ChemEditorInputDto editorInput =
        chemistryService.getChemicalEditorInput(chemicalId, null, user);

    assertEquals(chemicalId, editorInput.getChemId());
  }

  @Test
  public void getChemicalEditorInputReturnsNull() {
    long chemicalId = 123L;
    when(chemElementManager.get(chemicalId, user)).thenReturn(null);

    ChemEditorInputDto editorInput =
        chemistryService.getChemicalEditorInput(chemicalId, null, user);

    assertNull(editorInput);
  }

  @Test
  public void whenChemistryFileExists_thenReturnFileContents() {
    String fileContents = "some-contents";
    long chemId = 123L;
    long fileId = 456L;
    Integer revision = null;

    RSChemElement chemElement = new RSChemElement();
    chemElement.setId(chemId);
    chemElement.setEcatChemFileId(fileId);
    when(chemElementManager.get(chemId, user)).thenReturn(chemElement);

    EcatChemistryFile file = new EcatChemistryFile();
    file.setChemString(fileContents);
    when(fileManager.get(fileId, user)).thenReturn(file);
    String actual = chemistryService.getChemicalFileContents(chemId, revision, user);
    assertEquals("some-contents", actual);
  }

  @Test
  public void whenChemistryFileDoesNotExist_thenReturnEmptyString() {
    String fileContents = "some-contents";
    long chemId = 123L;
    long fileId = 456L;
    Integer revision = null;

    RSChemElement chemElement = new RSChemElement();
    chemElement.setId(chemId);
    chemElement.setEcatChemFileId(fileId);
    when(chemElementManager.get(chemId, user)).thenReturn(chemElement);

    EcatChemistryFile file = new EcatChemistryFile();
    file.setChemString(fileContents);
    when(fileManager.get(fileId, user)).thenReturn(null);
    String actual = chemistryService.getChemicalFileContents(chemId, revision, user);
    assertEquals("", actual);
  }

  @Test
  public void inventoryHitsAreListedAfterDocumentAndGalleryHits() {
    List<ChemSearchedItem> elnHits = createSearchItems(3);
    List<InventoryStructureHit> inventoryHits = createInventoryHits(3);
    stubChemistryServiceHits("CC", "SUBSTRUCTURE");
    when(chemElementManager.searchChemIds(eq(CHEM_IDS), anyInt(), eq(user))).thenReturn(elnHits);
    stubInventoryAvailable();
    when(inventoryStructureSearchManager.findReadableStructureHits(CHEM_IDS, user))
        .thenReturn(inventoryHits);

    ChemicalSearchResults firstPage =
        chemistryService.searchChemicals("CC", "SUBSTRUCTURE", 0, 2, user);
    assertThat(firstPage.getPagedRecords()).containsExactlyElementsOf(elnHits.subList(0, 2));
    assertThat(firstPage.getPagedInventoryHits()).isEmpty();
    assertEquals(6, firstPage.getTotalHitCount());
    assertEquals(3, firstPage.getTotalPageCount());

    ChemicalSearchResults boundaryPage =
        chemistryService.searchChemicals("CC", "SUBSTRUCTURE", 1, 2, user);
    assertThat(boundaryPage.getPagedRecords()).containsExactly(elnHits.get(2));
    assertThat(boundaryPage.getPagedInventoryHits()).containsExactly(inventoryHits.get(0));
    assertEquals(2, boundaryPage.getStartHit());
    assertEquals(3, boundaryPage.getEndHit());

    ChemicalSearchResults inventoryPage =
        chemistryService.searchChemicals("CC", "SUBSTRUCTURE", 2, 2, user);
    assertThat(inventoryPage.getPagedRecords()).isEmpty();
    assertThat(inventoryPage.getPagedInventoryHits())
        .containsExactlyElementsOf(inventoryHits.subList(1, 3));
    assertEquals(5, inventoryPage.getEndHit());

    // one chemistry service query per page serves both searches
    verify(chemistryProvider, times(3)).search("CC", "SUBSTRUCTURE");
  }

  @Test
  public void inventoryIsNotSearchedWhileMoreDocumentAndGalleryHitsFollow() {
    stubChemistryServiceHits("CC", "SUBSTRUCTURE");
    // 5 hits reach the limit for the first page of 2 (one page ahead plus one)
    when(chemElementManager.searchChemIds(eq(CHEM_IDS), anyInt(), eq(user)))
        .thenReturn(createSearchItems(5));

    ChemicalSearchResults actual =
        chemistryService.searchChemicals("CC", "SUBSTRUCTURE", 0, 2, user);

    assertThat(actual.getPagedRecords()).hasSize(2);
    assertNull(actual.getTotalHitCount());
    verifyNoInteractions(inventoryStructureSearchManager, systemPropertyPermissionManager);
  }

  @Test
  public void inventoryIsNotSearchedWhenInventoryIsUnavailable() {
    stubChemistryServiceHits("CC", "SUBSTRUCTURE");
    when(chemElementManager.searchChemIds(eq(CHEM_IDS), anyInt(), eq(user)))
        .thenReturn(createSearchItems(1));
    when(systemPropertyPermissionManager.isPropertyAllowed(
            user, SystemPropertyName.INVENTORY_AVAILABLE))
        .thenReturn(false);

    ChemicalSearchResults actual =
        chemistryService.searchChemicals("CC", "SUBSTRUCTURE", 0, 2, user);

    assertEquals(1, actual.getTotalHitCount());
    verifyNoInteractions(inventoryStructureSearchManager);
  }

  @Test
  public void inventoryIsNotSearchedWhenStructureSearchIsUnavailable() {
    stubChemistryServiceHits("CC", "SUBSTRUCTURE");
    when(chemElementManager.searchChemIds(eq(CHEM_IDS), anyInt(), eq(user)))
        .thenReturn(createSearchItems(1));
    when(systemPropertyPermissionManager.isPropertyAllowed(
            user, SystemPropertyName.INVENTORY_AVAILABLE))
        .thenReturn(true);
    when(inventoryStructureSearchManager.isStructureSearchAvailable(user)).thenReturn(false);

    ChemicalSearchResults actual =
        chemistryService.searchChemicals("CC", "SUBSTRUCTURE", 0, 2, user);

    assertEquals(1, actual.getTotalHitCount());
    verify(inventoryStructureSearchManager, never()).findReadableStructureHits(CHEM_IDS, user);
  }

  private void stubChemistryServiceHits(String chemQuery, String searchType) {
    when(chemistryProvider.search(chemQuery, searchType))
        .thenReturn(new ChemicalSearchResultsDTO(CHEM_IDS, CHEM_IDS.size()));
  }

  private void stubInventoryAvailable() {
    when(systemPropertyPermissionManager.isPropertyAllowed(
            user, SystemPropertyName.INVENTORY_AVAILABLE))
        .thenReturn(true);
    when(inventoryStructureSearchManager.isStructureSearchAvailable(user)).thenReturn(true);
  }

  private List<InventoryStructureHit> createInventoryHits(int count) {
    List<InventoryStructureHit> hits = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      hits.add(
          new InventoryStructureHit(
              "SA" + i, "sample " + i, InventoryRecordType.SAMPLE, "Owner", new Date()));
    }
    return hits;
  }

  private List<ChemSearchedItem> createSearchItems(int resultsSize) {
    List<ChemSearchedItem> searchResults = new ArrayList<>();
    for (int i = 0; i < resultsSize; i++) {
      ChemSearchedItem searchResult = new ChemSearchedItem();
      searchResult.setChemId((long) i);
      searchResults.add(searchResult);
    }
    return searchResults;
  }
}

package com.researchspace.chemistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.ChemSearchedItem;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.chemistry.ChemConversionInputDto;
import com.researchspace.model.dtos.chemistry.ChemElementDataDto;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ConvertedStructureDto;
import com.researchspace.model.record.BreadcrumbGenerator;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.AuditManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.impl.RSChemService;
import com.researchspace.service.impl.RSChemService.ChemicalSearchResults;
import com.researchspace.webapp.controller.RSChemController.ChemEditorInputDto;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

  @InjectMocks RSChemService chemistryService;

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
    List<ChemSearchedItem> searchedItems = createSearchItems();
    when(chemElementManager.search(chemQuery, searchType, user)).thenReturn(searchedItems);

    ChemicalSearchResults actual =
        chemistryService.searchChemicals(chemQuery, searchType, 0, 5, user);

    assertEquals(0, actual.getStartHit());
    assertEquals(4, actual.getEndHit());
    assertEquals(10, actual.getTotalHitCount());
    assertEquals(2, actual.getTotalPageCount());
    assertEquals(5, actual.getPagedRecords().size());
    assertTrue(searchedItems.containsAll(actual.getPagedRecords()));
  }

  @Test
  public void convertedStructureCreated() {
    String inputStructure = "CC";
    String converted = "CC-converted";
    String params = "some-params";
    when(chemistryProvider.convert(inputStructure)).thenReturn(converted);

    ChemConversionInputDto conversionInput = new ChemConversionInputDto();
    conversionInput.setStructure(inputStructure);
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

  private List<ChemSearchedItem> createSearchItems() {
    int resultsSize = 10;
    List<ChemSearchedItem> searchResults = new ArrayList<>();
    for (int i = 0; i < resultsSize; i++) {
      ChemSearchedItem searchResult = new ChemSearchedItem();
      searchResult.setChemId((long) i);
      searchResults.add(searchResult);
    }
    return searchResults;
  }
}

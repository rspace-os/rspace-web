package com.researchspace.chemistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import com.researchspace.model.dtos.chemistry.ConvertedStructureDto;
import com.researchspace.model.record.BreadcrumbGenerator;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.service.AuditManager;
import com.researchspace.service.EcatChemistryFileManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.StoichiometryManager;
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

  @Mock EcatChemistryFileManager fileManager;

  @Mock StoichiometryManager stoichiometryManager;

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

    // chem manager finding 10 results for any search
    List<ChemSearchedItem> searchedItems = createSearchItems();
    when(chemElementManager.search(eq(chemQuery), eq(searchType), anyInt(), eq(user)))
        .thenReturn(searchedItems);

    // let's test different pagination & result limiting

    // 1st page when splitting for pages of 5
    ChemicalSearchResults actual =
        chemistryService.searchChemicals(chemQuery, searchType, 0, 5, user);
    assertEquals(0, actual.getStartHit());
    assertEquals(4, actual.getEndHit());
    assertEquals(10, actual.getTotalHitCount());
    assertEquals(2, actual.getTotalPageCount());
    assertEquals(5, actual.getPagedRecords().size());
    assertTrue(searchedItems.containsAll(actual.getPagedRecords()));

    // 1st page when splitting for pages of 4
    actual = chemistryService.searchChemicals(chemQuery, searchType, 0, 4, user);
    assertEquals(0, actual.getStartHit());
    assertEquals(3, actual.getEndHit());
    assertNull(actual.getTotalHitCount()); // total calculations were cut off
    assertNull(actual.getTotalPageCount()); // total calculations were cut off
    assertEquals(4, actual.getPagedRecords().size());

    // 2nd page when splitting for pages of 4
    actual = chemistryService.searchChemicals(chemQuery, searchType, 1, 4, user);
    assertEquals(4, actual.getStartHit());
    assertEquals(7, actual.getEndHit());
    assertEquals(10, actual.getTotalHitCount()); // no cutoff at page-before-last
    assertEquals(3, actual.getTotalPageCount()); // no cutoff at page-before-last
    assertEquals(4, actual.getPagedRecords().size());

    // 3rd page when splitting for pages of 4
    actual = chemistryService.searchChemicals(chemQuery, searchType, 2, 4, user);
    assertEquals(8, actual.getStartHit());
    assertEquals(9, actual.getEndHit());
    assertEquals(10, actual.getTotalHitCount()); // no cutoff at last page
    assertEquals(3, actual.getTotalPageCount()); // no cutoff at last page
    assertEquals(2, actual.getPagedRecords().size());
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

  @Test
  public void whenDeleteStoichiometry_thenReturnTrue() {
    long stoichiometryId = 1L;
    long parentReactionId = 2L;

    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(stoichiometryId);
    RSChemElement parentReaction = new RSChemElement();
    parentReaction.setId(parentReactionId);
    stoichiometry.setParentReaction(parentReaction);

    RSChemElement chemical = new RSChemElement();
    chemical.setId(parentReactionId);
    Record parentRecord = TestFactory.createAnyRecord(user);
    chemical.setRecord(parentRecord);

    when(stoichiometryManager.get(stoichiometryId)).thenReturn(stoichiometry);

    when(chemElementManager.get(parentReactionId, user)).thenReturn(chemical);

    boolean result = chemistryService.deleteStoichiometry(stoichiometryId, user);

    verify(stoichiometryManager).remove(stoichiometryId);

    assertTrue(result);
  }
}

package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.StoichiometryDao;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMapper;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeUpdateDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.service.chemistry.StoichiometryException;
import com.researchspace.service.impl.StoichiometryManagerImpl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;

@RunWith(MockitoJUnitRunner.class)
public class StoichiometryManagerTest {

  @Mock private StoichiometryDao stoichiometryDao;
  @Mock private RSChemElementManager rsChemElementManager;
  @Mock private ChemicalSearcher chemicalSearcher;

  @InjectMocks private StoichiometryManagerImpl stoichiometryManager;
  private User user;

  @Before
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("testUser");
  }

  @Test
  public void whenFindByParentReactionId_thenReturnMatchingStoichiometry() {
    Long parentReactionId = 1L;
    Stoichiometry expectedStoichiometry = createStoichiometry(1L, parentReactionId);
    when(stoichiometryDao.findByParentReactionId(parentReactionId))
        .thenReturn(Optional.of((expectedStoichiometry)));

    Optional<Stoichiometry> result = stoichiometryManager.findByParentReactionId(parentReactionId);

    assertEquals(expectedStoichiometry, result.get());
  }

  @Test
  public void whenFindByParentReactionId_withNoResult_thenReturnEmpty() {
    Long parentReactionId = 999L;
    when(stoichiometryDao.findByParentReactionId(parentReactionId)).thenReturn(Optional.empty());

    Optional<Stoichiometry> result = stoichiometryManager.findByParentReactionId(parentReactionId);

    assertEquals(false, result.isPresent());
    verify(stoichiometryDao).findByParentReactionId(parentReactionId);
  }

  @Test
  public void whenCreateFromAnalysis_thenReturnStoichiometryWithMolecules()
      throws IOException, ChemicalImportException {
    Long parentReactionId = 1L;
    RSChemElement parentReaction = createRSChemElement(parentReactionId);
    ElementalAnalysisDTO analysisDTO = createElementalAnalysisDTO();

    when(rsChemElementManager.save(any(RSChemElement.class), any(User.class)))
        .thenAnswer(
            invocation -> {
              RSChemElement element = invocation.getArgument(0);
              element.setId(2L);
              return element;
            });

    List<ChemicalImportSearchResult> searchResults = new ArrayList<>();
    ChemicalImportSearchResult searchResult =
        ChemicalImportSearchResult.builder().name("Ethanol").smiles("CCO").formula("C2H6O").build();
    searchResults.add(searchResult);
    when(chemicalSearcher.searchChemicals(any(ChemicalImportSearchType.class), anyString()))
        .thenReturn(searchResults);

    when(stoichiometryDao.save(any(Stoichiometry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Stoichiometry result =
        stoichiometryManager.createFromAnalysis(analysisDTO, parentReaction, user);

    assertNotNull(result);
    // ID assignment is handled by the persistence layer; with a mocked DAO we do not assert on ID
    assertEquals(parentReaction, result.getParentReaction());
    assertEquals(1, result.getMolecules().size());

    verify(rsChemElementManager).save(any(RSChemElement.class), any(User.class));
    verify(chemicalSearcher).searchChemicals(any(ChemicalImportSearchType.class), anyString());
    verify(stoichiometryDao, times(2)).save(any(Stoichiometry.class));
  }

  @Test
  public void
      whenCreateFromAnalysisWithChemicalSearcherException_thenReturnStoichiometryWithNullName()
          throws IOException, ChemicalImportException {
    Long parentReactionId = 1L;
    RSChemElement parentReaction = createRSChemElement(parentReactionId);
    ElementalAnalysisDTO analysisDTO = createElementalAnalysisDTO();

    when(rsChemElementManager.save(any(RSChemElement.class), any(User.class)))
        .thenAnswer(
            invocation -> {
              RSChemElement element = invocation.getArgument(0);
              element.setId(2L);
              return element;
            });

    when(chemicalSearcher.searchChemicals(any(ChemicalImportSearchType.class), anyString()))
        .thenThrow(
            new ChemicalImportException("Error searching chemicals", HttpStatus.BAD_REQUEST));

    when(stoichiometryDao.save(any(Stoichiometry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Stoichiometry result =
        stoichiometryManager.createFromAnalysis(analysisDTO, parentReaction, user);

    assertNotNull(result);
    // ID assignment is handled by the persistence layer; with a mocked DAO we do not assert on ID
    assertEquals(parentReaction, result.getParentReaction());
    assertEquals(1, result.getMolecules().size());

    StoichiometryMolecule molecule = result.getMolecules().get(0);
    assertNull(molecule.getName());

    verify(rsChemElementManager).save(any(RSChemElement.class), any(User.class));
    verify(chemicalSearcher).searchChemicals(any(ChemicalImportSearchType.class), anyString());
    verify(stoichiometryDao, times(2)).save(any(Stoichiometry.class));
  }

  @Test
  public void whenUpdate_thenReturnUpdatedStoichiometry() {
    Long stoichiometryId = 1L;
    Stoichiometry existingStoichiometry = createStoichiometry(stoichiometryId, 1L);
    StoichiometryUpdateDTO stoichiometryUpdateDTO = createStoichiometryUpdateDTO(stoichiometryId);

    when(stoichiometryDao.get(stoichiometryId)).thenReturn(existingStoichiometry);
    when(stoichiometryDao.save(any(Stoichiometry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Stoichiometry result = stoichiometryManager.update(stoichiometryUpdateDTO, user);

    assertNotNull(result);
    assertEquals(stoichiometryId, result.getId());
    assertEquals(1, result.getMolecules().size());

    verify(stoichiometryDao).get(stoichiometryId);
    verify(stoichiometryDao).save(any(Stoichiometry.class));
  }

  @Test
  public void whenUpdateExistingMolecule_thenReturnUpdatedStoichiometry() throws IOException {
    Long stoichiometryId = 1L;
    Stoichiometry existingStoichiometry = createStoichiometry(stoichiometryId, 1L);

    StoichiometryUpdateDTO stoichiometryUpdateDTO = new StoichiometryUpdateDTO();
    stoichiometryUpdateDTO.setId(stoichiometryId);
    StoichiometryMoleculeUpdateDTO moleculeUpdateDTO = new StoichiometryMoleculeUpdateDTO();
    moleculeUpdateDTO.setId(2L); // Use existing molecule ID
    moleculeUpdateDTO.setCoefficient(2.0); // Update coefficient
    moleculeUpdateDTO.setMass(92.14); // Update mass
    moleculeUpdateDTO.setMoles(2.0); // Update moles
    moleculeUpdateDTO.setExpectedAmount(200.0); // Update expected amount
    moleculeUpdateDTO.setActualAmount(190.0); // Update actual amount
    moleculeUpdateDTO.setActualYield(95.0); // Update actual yield
    moleculeUpdateDTO.setLimitingReagent(true); // Update limiting reagent
    moleculeUpdateDTO.setNotes("Updated notes"); // Update notes
    stoichiometryUpdateDTO.setMolecules(Collections.singletonList(moleculeUpdateDTO));

    when(stoichiometryDao.get(stoichiometryId)).thenReturn(existingStoichiometry);
    when(stoichiometryDao.save(any(Stoichiometry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Stoichiometry result = stoichiometryManager.update(stoichiometryUpdateDTO, user);

    assertNotNull(result);
    assertEquals(stoichiometryId, result.getId());
    assertEquals(1, result.getMolecules().size());

    StoichiometryMolecule updatedMolecule = result.getMolecules().get(0);
    assertEquals(2L, updatedMolecule.getId().longValue());
    assertEquals(2.0, updatedMolecule.getCoefficient(), 0.001);
    assertEquals(92.14, updatedMolecule.getMass(), 0.001);
    assertEquals(2.0, updatedMolecule.getMoles(), 0.001);
    assertEquals(200.0, updatedMolecule.getExpectedAmount(), 0.001);
    assertEquals(190.0, updatedMolecule.getActualAmount(), 0.001);
    assertEquals(95.0, updatedMolecule.getActualYield(), 0.001);
    assertEquals(true, updatedMolecule.getLimitingReagent());
    assertEquals("Updated notes", updatedMolecule.getNotes());

    verify(stoichiometryDao).get(stoichiometryId);
    verify(stoichiometryDao).save(any(Stoichiometry.class));
  }

  @Test
  public void whenUpdateWithNonExistentStoichiometry_thenThrowIllegalArgumentException() {
    Long stoichiometryId = 1L;
    StoichiometryUpdateDTO stoichiometryUpdateDTO = createStoichiometryUpdateDTO(stoichiometryId);

    when(stoichiometryDao.get(stoichiometryId)).thenReturn(null);

    Exception exception =
        assertThrows(
            StoichiometryException.class,
            () -> stoichiometryManager.update(stoichiometryUpdateDTO, user));

    assertTrue(
        exception
            .getMessage()
            .contains(String.format("Stoichiometry not found with ID: %s", stoichiometryId)));
  }

  @Test
  public void whenUpdateWithNonExistentMoleculeId_thenThrowException() {
    Long stoichiometryId = 1L;
    Long nonExistentMoleculeId = 999L;
    Stoichiometry existingStoichiometry = createStoichiometry(stoichiometryId, 1L);

    StoichiometryUpdateDTO stoichiometryUpdateDTO = new StoichiometryUpdateDTO();
    stoichiometryUpdateDTO.setId(stoichiometryId);
    StoichiometryMoleculeUpdateDTO moleculeUpdateDTO = new StoichiometryMoleculeUpdateDTO();
    moleculeUpdateDTO.setId(nonExistentMoleculeId);
    moleculeUpdateDTO.setCoefficient(2.0);
    moleculeUpdateDTO.setMass(92.14);
    stoichiometryUpdateDTO.setMolecules(Collections.singletonList(moleculeUpdateDTO));

    when(stoichiometryDao.get(stoichiometryId)).thenReturn(existingStoichiometry);

    Exception exception =
        assertThrows(
            StoichiometryException.class,
            () -> stoichiometryManager.update(stoichiometryUpdateDTO, user));

    assertTrue(
        exception
            .getMessage()
            .contains(
                String.format(
                    "Molecule ID %s not found in existing stoichiometry molecules.",
                    nonExistentMoleculeId)));
  }

  @Test
  public void whenToDTO_thenReturnStoichiometryDTOWithAllMolecules() {
    Stoichiometry stoichiometry = createStoichiometryWithMolecules(1L, 2L);

    StoichiometryDTO result = StoichiometryMapper.toDTO(stoichiometry);

    assertNotNull(result);
    assertEquals(Long.valueOf(2L), result.getParentReactionId());
    assertEquals(3, result.getMolecules().size());

    StoichiometryMoleculeDTO reactant =
        result.getMolecules().stream()
            .filter(m -> m.getRole().equals(MoleculeRole.REACTANT))
            .findFirst()
            .get();
    assertEquals("C2H6O", reactant.getFormula());
    assertEquals("Ethanol", reactant.getName());

    StoichiometryMoleculeDTO product =
        result.getMolecules().stream()
            .filter(m -> m.getRole().equals(MoleculeRole.PRODUCT))
            .findFirst()
            .get();
    assertEquals("C2H4O", product.getFormula());
    assertEquals("Acetaldehyde", product.getName());
  }

  @Test
  public void whenToDTOWithNullStoichiometry_thenReturnNull() {
    StoichiometryDTO result = StoichiometryMapper.toDTO(null);

    assertNull(result);
  }

  @Test
  public void whenFromAnalysisDTO_thenReturnStoichiometryDTOWithMolecules() {
    ElementalAnalysisDTO analysisDTO = createElementalAnalysisDTO();

    StoichiometryDTO result = StoichiometryMapper.fromAnalysisDTO(analysisDTO);

    assertNotNull(result);
    assertEquals(1, result.getMolecules().size());

    StoichiometryMoleculeDTO molecule = result.getMolecules().get(0);
    assertEquals(MoleculeRole.REACTANT, molecule.getRole());
    assertEquals(46.07, molecule.getMolecularWeight(), 0.001);
  }

  @Test
  public void whenFromAnalysisDTOWithNullInput_thenReturnNull() {
    StoichiometryDTO result = StoichiometryMapper.fromAnalysisDTO(null);

    assertNull(result);
  }

  private Stoichiometry createStoichiometry(Long id, Long parentReactionId) {
    RSChemElement parentReaction = createRSChemElement(parentReactionId);
    Stoichiometry stoichiometry =
        Stoichiometry.builder()
            .id(id)
            .parentReaction(parentReaction)
            .molecules(new ArrayList<>())
            .build();

    StoichiometryMolecule molecule =
        StoichiometryMolecule.builder()
            .id(2L)
            .stoichiometry(stoichiometry)
            .rsChemElement(createRSChemElement(3L))
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .molecularWeight(46.07)
            .build();
    stoichiometry.addMolecule(molecule);

    return stoichiometry;
  }

  private Stoichiometry createStoichiometryWithMolecules(Long id, Long parentReactionId) {
    Stoichiometry stoichiometry = createStoichiometry(id, parentReactionId);

    StoichiometryMolecule reactant =
        StoichiometryMolecule.builder()
            .id(3L)
            .stoichiometry(stoichiometry)
            .rsChemElement(createRSChemElement(4L))
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .molecularWeight(46.07)
            .build();
    stoichiometry.addMolecule(reactant);

    StoichiometryMolecule product =
        StoichiometryMolecule.builder()
            .id(5L)
            .stoichiometry(stoichiometry)
            .rsChemElement(createRSChemElement(6L))
            .role(MoleculeRole.PRODUCT)
            .formula("C2H4O")
            .name("Acetaldehyde")
            .smiles("CC=O")
            .molecularWeight(44.05)
            .build();
    stoichiometry.addMolecule(product);

    return stoichiometry;
  }

  private RSChemElement createRSChemElement(Long id) {
    return RSChemElement.builder().id(id).chemElements("CCO").build();
  }

  private ElementalAnalysisDTO createElementalAnalysisDTO() {
    MoleculeInfoDTO molecule =
        MoleculeInfoDTO.builder()
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .mass(46.07)
            .build();

    return ElementalAnalysisDTO.builder()
        .moleculeInfo(Collections.singletonList(molecule))
        .formula("C2H6O")
        .isReaction(false)
        .build();
  }

  private StoichiometryDTO createStoichiometryDTO(Long parentReactionId) {
    StoichiometryMoleculeDTO molecule =
        StoichiometryMoleculeDTO.builder()
            .id(2L)
            .rsChemElementId(3L)
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .molecularWeight(46.07)
            .build();

    return StoichiometryDTO.builder()
        .molecules(Collections.singletonList(molecule))
        .parentReactionId(parentReactionId)
        .build();
  }

  private StoichiometryUpdateDTO createStoichiometryUpdateDTO(Long id) {
    StoichiometryMoleculeUpdateDTO molecule =
        StoichiometryMoleculeUpdateDTO.builder()
            .id(2L)
            .coefficient(1.0)
            .mass(46.07)
            .moles(1.0)
            .expectedAmount(100.0)
            .actualAmount(95.0)
            .actualYield(95.0)
            .limitingReagent(true)
            .notes("Test notes")
            .build();

    return StoichiometryUpdateDTO.builder()
        .id(id)
        .molecules(Collections.singletonList(molecule))
        .build();
  }
}

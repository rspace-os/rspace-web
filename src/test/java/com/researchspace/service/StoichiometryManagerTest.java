package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.StoichiometryDao;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.Stoichiometry;
import com.researchspace.model.StoichiometryMolecule;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.dtos.chemistry.MoleculeRole;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.impl.StoichiometryManagerImpl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.http.HttpStatus;

public class StoichiometryManagerTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();

  @Mock private StoichiometryDao stoichiometryDao;
  @Mock private RSChemElementManager rsChemElementManager;
  @Mock private ChemicalSearcher chemicalSearcher;

  private StoichiometryManagerImpl stoichiometryManager;
  private User user;

  @Before
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("testUser");
    stoichiometryManager =
        new StoichiometryManagerImpl(stoichiometryDao, rsChemElementManager, chemicalSearcher);
  }

  @Test
  public void whenFindByParentReactionId_thenReturnMatchingStoichiometry() {
    Long parentReactionId = 1L;
    Stoichiometry expectedStoichiometry = createStoichiometry(1L, parentReactionId);
    when(stoichiometryDao.findByParentReactionId(parentReactionId))
        .thenReturn(expectedStoichiometry);

    Stoichiometry result = stoichiometryManager.findByParentReactionId(parentReactionId);

    assertEquals(expectedStoichiometry, result);
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
        .thenAnswer(
            invocation -> {
              Stoichiometry stoichiometry = invocation.getArgument(0);
              if (stoichiometry.getId() == null) {
                stoichiometry.setId(3L);
              }
              return stoichiometry;
            });

    Stoichiometry result =
        stoichiometryManager.createFromAnalysis(analysisDTO, parentReaction, user);

    assertNotNull(result);
    assertEquals(Long.valueOf(3L), result.getId());
    assertEquals(parentReaction, result.getParentReaction());
    assertEquals(1, result.getMolecules().size());

    verify(rsChemElementManager).save(any(RSChemElement.class), any(User.class));
    verify(chemicalSearcher).searchChemicals(any(ChemicalImportSearchType.class), anyString());
    verify(stoichiometryDao, times(2))
        .save(any(Stoichiometry.class)); // Called twice: initial save and final save
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
        .thenAnswer(
            invocation -> {
              Stoichiometry stoichiometry = invocation.getArgument(0);
              if (stoichiometry.getId() == null) {
                stoichiometry.setId(3L);
              }
              return stoichiometry;
            });

    Stoichiometry result =
        stoichiometryManager.createFromAnalysis(analysisDTO, parentReaction, user);

    assertNotNull(result);
    assertEquals(Long.valueOf(3L), result.getId());
    assertEquals(parentReaction, result.getParentReaction());
    assertEquals(1, result.getMolecules().size());

    StoichiometryMolecule molecule = result.getMolecules().get(0);
    assertNull(molecule.getName()); // as chemical searcher failed, name should not be set

    verify(rsChemElementManager).save(any(RSChemElement.class), any(User.class));
    verify(chemicalSearcher).searchChemicals(any(ChemicalImportSearchType.class), anyString());
    verify(stoichiometryDao, times(2))
        .save(any(Stoichiometry.class)); // Called twice: initial save and final save
  }

  @Test
  public void whenUpdate_thenReturnUpdatedStoichiometry() {
    Long stoichiometryId = 1L;
    Stoichiometry existingStoichiometry = createStoichiometry(stoichiometryId, 1L);
    StoichiometryDTO stoichiometryDTO = createStoichiometryDTO(1L);

    when(stoichiometryDao.get(stoichiometryId)).thenReturn(existingStoichiometry);
    when(rsChemElementManager.get(any(Long.class), any(User.class)))
        .thenReturn(createRSChemElement(2L));
    when(stoichiometryDao.save(any(Stoichiometry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Stoichiometry result = stoichiometryManager.update(stoichiometryId, stoichiometryDTO, user);

    assertNotNull(result);
    assertEquals(stoichiometryId, result.getId());
    assertEquals(1, result.getMolecules().size());

    verify(stoichiometryDao).get(stoichiometryId);
    verify(rsChemElementManager).get(any(Long.class), any(User.class));
    verify(stoichiometryDao).save(any(Stoichiometry.class));
  }

  @Test
  public void whenUpdateWithNewMolecule_thenCreateNewRSChemElementAndReturnUpdatedStoichiometry()
      throws IOException {
    Long stoichiometryId = 1L;
    Stoichiometry existingStoichiometry = createStoichiometry(stoichiometryId, 1L);

    StoichiometryDTO stoichiometryDTO = new StoichiometryDTO();
    stoichiometryDTO.setParentReactionId(1L);
    StoichiometryMoleculeDTO moleculeDTO = new StoichiometryMoleculeDTO();
    moleculeDTO.setRole(MoleculeRole.REACTANT);
    moleculeDTO.setFormula("C2H6O");
    moleculeDTO.setName("Ethanol");
    stoichiometryDTO.setMolecules(Collections.singletonList(moleculeDTO));

    when(stoichiometryDao.get(stoichiometryId)).thenReturn(existingStoichiometry);
    when(rsChemElementManager.save(any(RSChemElement.class), any(User.class)))
        .thenAnswer(
            invocation -> {
              RSChemElement element = invocation.getArgument(0);
              element.setId(3L);
              return element;
            });
    when(stoichiometryDao.save(any(Stoichiometry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Stoichiometry result = stoichiometryManager.update(stoichiometryId, stoichiometryDTO, user);

    assertNotNull(result);
    assertEquals(stoichiometryId, result.getId());
    assertEquals(1, result.getMolecules().size());

    verify(stoichiometryDao).get(stoichiometryId);
    verify(rsChemElementManager).save(any(RSChemElement.class), any(User.class));
    verify(stoichiometryDao).save(any(Stoichiometry.class));
  }

  @Test
  public void whenUpdateWithNonExistentStoichiometry_thenThrowIllegalArgumentException() {
    Long stoichiometryId = 1L;
    StoichiometryDTO stoichiometryDTO = createStoichiometryDTO(1L);

    when(stoichiometryDao.get(stoichiometryId)).thenReturn(null);

    assertThrows(
        IllegalArgumentException.class,
        () -> stoichiometryManager.update(stoichiometryId, stoichiometryDTO, user));

    verify(stoichiometryDao).get(stoichiometryId);
    verify(stoichiometryDao, never()).save(any(Stoichiometry.class));
  }

  @Test
  public void whenUpdateWithImmutableFieldChange_thenThrowIllegalArgumentException() {
    Long stoichiometryId = 1L;
    Stoichiometry existingStoichiometry = createStoichiometry(stoichiometryId, 1L);

    StoichiometryMolecule existingMolecule =
        StoichiometryMolecule.builder()
            .id(2L)
            .stoichiometry(existingStoichiometry)
            .rsChemElement(createRSChemElement(3L))
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .molecularWeight(46.07)
            .build();
    existingStoichiometry.addMolecule(existingMolecule);

    StoichiometryDTO stoichiometryDTO = new StoichiometryDTO();
    stoichiometryDTO.setParentReactionId(1L);
    StoichiometryMoleculeDTO moleculeDTO = new StoichiometryMoleculeDTO();
    moleculeDTO.setId(2L);
    moleculeDTO.setRsChemElementId(4L);
    moleculeDTO.setRole(MoleculeRole.REACTANT);
    moleculeDTO.setFormula("C2H6O");
    moleculeDTO.setName("Ethanol");
    stoichiometryDTO.setMolecules(Collections.singletonList(moleculeDTO));

    when(stoichiometryDao.get(stoichiometryId)).thenReturn(existingStoichiometry);

    assertThrows(
        IllegalArgumentException.class,
        () -> stoichiometryManager.update(stoichiometryId, stoichiometryDTO, user));

    verify(stoichiometryDao).get(stoichiometryId);
    verify(stoichiometryDao, never()).save(any(Stoichiometry.class));
  }

  @Test
  public void whenToDTO_thenReturnStoichiometryDTOWithAllMolecules() {
    Stoichiometry stoichiometry = createStoichiometryWithMolecules(1L, 2L);

    StoichiometryDTO result = stoichiometryManager.toDTO(stoichiometry);

    assertNotNull(result);
    assertEquals(Long.valueOf(2L), result.getParentReactionId());
    assertEquals(2, result.getMolecules().size());

    StoichiometryMoleculeDTO reactant = result.getReactants().get(0);
    assertEquals(MoleculeRole.REACTANT, reactant.getRole());
    assertEquals("C2H6O", reactant.getFormula());
    assertEquals("Ethanol", reactant.getName());

    StoichiometryMoleculeDTO product = result.getProducts().get(0);
    assertEquals(MoleculeRole.PRODUCT, product.getRole());
    assertEquals("C2H4O", product.getFormula());
    assertEquals("Acetaldehyde", product.getName());
  }

  @Test
  public void whenToDTOWithNullStoichiometry_thenReturnNull() {
    StoichiometryDTO result = stoichiometryManager.toDTO(null);

    assertNull(result);
  }

  @Test
  public void whenFromAnalysisDTO_thenReturnStoichiometryDTOWithMolecules() {
    ElementalAnalysisDTO analysisDTO = createElementalAnalysisDTO();

    StoichiometryDTO result = stoichiometryManager.fromAnalysisDTO(analysisDTO);

    assertNotNull(result);
    assertEquals(1, result.getMolecules().size());

    StoichiometryMoleculeDTO molecule = result.getMolecules().get(0);
    assertEquals(MoleculeRole.REACTANT, molecule.getRole());
    assertEquals(46.07, molecule.getMolecularWeight(), 0.001);
  }

  @Test
  public void whenFromAnalysisDTOWithNullInput_thenReturnNull() {
    StoichiometryDTO result = stoichiometryManager.fromAnalysisDTO(null);

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
}

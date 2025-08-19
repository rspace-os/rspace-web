package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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

  @Captor
  private ArgumentCaptor<Stoichiometry> stoichiometryCaptor;

  private User user;

  @Before
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("testUser");
    when(stoichiometryDao.save(stoichiometryCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(0, Stoichiometry.class));
  }

  @Test
  public void whenFindByParentReactionId_thenReturnsFromDao() {
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

    assertFalse(result.isPresent());
  }

  @Test
  public void whenCreateFromAnalysis_thenReturnStoichiometryWithMolecules()
      throws IOException, ChemicalImportException {
    Long parentReactionId = 1L;
    RSChemElement parentReaction = createRSChemElement(parentReactionId);
    ElementalAnalysisDTO analysisDTO = createElementalAnalysisDTO();
    String name = "Ethanol";
    String smiles = "CCO";
    String formula = "C2H6O";

    List<ChemicalImportSearchResult> searchResults = new ArrayList<>();
    ChemicalImportSearchResult searchResult =
        ChemicalImportSearchResult.builder().name(name).smiles(smiles).formula(formula).build();
    searchResults.add(searchResult);

    when(chemicalSearcher.searchChemicals(any(ChemicalImportSearchType.class), anyString()))
        .thenReturn(searchResults);
    stoichiometryManager.createFromAnalysis(analysisDTO, parentReaction, user);

    verify(stoichiometryDao, times(2)).save(any(Stoichiometry.class));
    List<Stoichiometry> savedStoichiometries = stoichiometryCaptor.getAllValues();

    Stoichiometry finalSave = savedStoichiometries.get(1);
    assertEquals(parentReaction, finalSave.getParentReaction());
    assertEquals(1, finalSave.getMolecules().size());
    StoichiometryMolecule molecule = finalSave.getMolecules().get(0);
    assertEquals(name, molecule.getName());
    assertEquals(smiles, molecule.getSmiles());
    assertEquals(formula, molecule.getFormula());
  }

  @Test
  public void
      whenCreateFromAnalysisWithChemicalSearcherException_thenReturnStoichiometryWithNullName()
          throws IOException, ChemicalImportException {
    Long parentReactionId = 1L;
    RSChemElement parentReaction = createRSChemElement(parentReactionId);
    ElementalAnalysisDTO analysisDTO = createElementalAnalysisDTO();

    when(chemicalSearcher.searchChemicals(any(ChemicalImportSearchType.class), anyString()))
        .thenThrow(
            new ChemicalImportException("Error searching chemicals", HttpStatus.BAD_REQUEST));

    Stoichiometry result =
        stoichiometryManager.createFromAnalysis(analysisDTO, parentReaction, user);

    verify(stoichiometryDao, times(2)).save(any(Stoichiometry.class));
    List<Stoichiometry> savedStoichiometries = stoichiometryCaptor.getAllValues();

    Stoichiometry finalSave = savedStoichiometries.get(1);
    assertEquals(parentReaction, finalSave.getParentReaction());
    assertEquals(1, finalSave.getMolecules().size());
    StoichiometryMolecule molecule = finalSave.getMolecules().get(0);
    assertNull(molecule.getName());

    assertEquals(finalSave, result);
  }

  @Test
  public void whenUpdateWithNonExistentStoichiometry_thenThrowException() {
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
  public void whenUpdateMoleculeWithValidData_thenUpdatesSuccessfully() {
    Long stoichiometryId = 1L;
    Stoichiometry existingStoichiometry = createStoichiometryWith2Molecules(stoichiometryId, 1L);
    Long existingMoleculeId = existingStoichiometry.getMolecules().get(0).getId();

    StoichiometryUpdateDTO stoichiometryUpdateDTO = new StoichiometryUpdateDTO();
    stoichiometryUpdateDTO.setId(stoichiometryId);

    StoichiometryMoleculeUpdateDTO moleculeUpdateDTO = new StoichiometryMoleculeUpdateDTO();
    moleculeUpdateDTO.setId(existingMoleculeId);
    moleculeUpdateDTO.setCoefficient(2.5);
    moleculeUpdateDTO.setMass(100.0);
    moleculeUpdateDTO.setRole(MoleculeRole.PRODUCT);
    moleculeUpdateDTO.setNotes("Updated notes");
    stoichiometryUpdateDTO.setMolecules(Collections.singletonList(moleculeUpdateDTO));

    when(stoichiometryDao.get(stoichiometryId)).thenReturn(existingStoichiometry);

    Stoichiometry result = stoichiometryManager.update(stoichiometryUpdateDTO, user);

    verify(stoichiometryDao, times(1)).save(any(Stoichiometry.class));

    Stoichiometry savedStoichiometry = stoichiometryCaptor.getValue();
    assertEquals(1, savedStoichiometry.getMolecules().size());
    StoichiometryMolecule updatedMolecule = savedStoichiometry.getMolecules().get(0);
    assertEquals(2.5, updatedMolecule.getCoefficient(), 0.001);
    assertEquals(100.0, updatedMolecule.getMass(), 0.001);
    assertEquals(MoleculeRole.PRODUCT, updatedMolecule.getRole());
    assertEquals("Updated notes", updatedMolecule.getNotes());

    assertEquals(savedStoichiometry, result);
  }

  @Test
  public void whenUpdate_withNewMolecules_thenAddsThemCorrectly() throws Exception {
    Long stoichiometryId = 1L;
    Stoichiometry existingStoichiometry = createStoichiometry(stoichiometryId, 1L);
    Long existingMoleculeId = existingStoichiometry.getMolecules().get(0).getId();
    RSChemElement newMolecule = createRSChemElement(99L);

    StoichiometryUpdateDTO stoichiometryUpdateDTO = new StoichiometryUpdateDTO();
    stoichiometryUpdateDTO.setId(stoichiometryId);

    List<StoichiometryMoleculeUpdateDTO> updates = new ArrayList<>();

    // Keep existing molecule
    StoichiometryMoleculeUpdateDTO existingMoleculeUpdate = new StoichiometryMoleculeUpdateDTO();
    existingMoleculeUpdate.setId(existingMoleculeId);
    updates.add(existingMoleculeUpdate);

    // Add new molecule
    StoichiometryMoleculeUpdateDTO newMoleculeUpdateDTO = new StoichiometryMoleculeUpdateDTO();
    newMoleculeUpdateDTO.setId(null);
    newMoleculeUpdateDTO.setSmiles("C6H6");
    newMoleculeUpdateDTO.setName("Benzene");
    newMoleculeUpdateDTO.setFormula("C6H6");
    newMoleculeUpdateDTO.setRole(MoleculeRole.REACTANT);
    newMoleculeUpdateDTO.setCoefficient(1.0);
    newMoleculeUpdateDTO.setMolecularWeight(78.11);
    updates.add(newMoleculeUpdateDTO);

    stoichiometryUpdateDTO.setMolecules(updates);

    when(stoichiometryDao.get(stoichiometryId)).thenReturn(existingStoichiometry);
    when(rsChemElementManager.save(any(RSChemElement.class), any(User.class)))
        .thenReturn(newMolecule);

    Stoichiometry result = stoichiometryManager.update(stoichiometryUpdateDTO, user);

    verify(stoichiometryDao, times(1)).save(any(Stoichiometry.class));

    Stoichiometry savedStoichiometry = stoichiometryCaptor.getValue();
    assertEquals(2, savedStoichiometry.getMolecules().size());

    StoichiometryMolecule addedMolecule =
        result.getMolecules().stream()
            .filter(m -> "Benzene".equals(m.getName()))
            .findFirst()
            .orElse(null);
    assertEquals("C6H6", addedMolecule.getSmiles());
    assertEquals(MoleculeRole.REACTANT, addedMolecule.getRole());
  }

  @Test
  public void whenUpdate_withMultipleOperations_thenHandlesAllCorrectly() throws Exception {
    Long stoichiometryId = 1L;
    Stoichiometry existingStoichiometry = createStoichiometryWith2Molecules(stoichiometryId, 1L);
    Long existingMoleculeId = existingStoichiometry.getMolecules().get(0).getId();
    RSChemElement newMolecule = createRSChemElement(99L);

    StoichiometryUpdateDTO stoichiometryUpdateDTO = new StoichiometryUpdateDTO();
    stoichiometryUpdateDTO.setId(stoichiometryId);

    List<StoichiometryMoleculeUpdateDTO> updates = new ArrayList<>();

    // Update existing molecule
    StoichiometryMoleculeUpdateDTO existingMoleculeUpdate = new StoichiometryMoleculeUpdateDTO();
    existingMoleculeUpdate.setId(existingMoleculeId);
    existingMoleculeUpdate.setCoefficient(3.0);
    existingMoleculeUpdate.setMass(150.0);
    updates.add(existingMoleculeUpdate);

    // Add new molecule
    StoichiometryMoleculeUpdateDTO newMoleculeUpdate = new StoichiometryMoleculeUpdateDTO();
    newMoleculeUpdate.setId(null);
    newMoleculeUpdate.setSmiles("CH4");
    newMoleculeUpdate.setName("Methane");
    newMoleculeUpdate.setRole(MoleculeRole.AGENT);
    updates.add(newMoleculeUpdate);

    stoichiometryUpdateDTO.setMolecules(updates);

    when(stoichiometryDao.get(stoichiometryId)).thenReturn(existingStoichiometry);
    when(rsChemElementManager.save(any(RSChemElement.class), any(User.class)))
        .thenReturn(newMolecule);

    Stoichiometry result = stoichiometryManager.update(stoichiometryUpdateDTO, user);

    verify(stoichiometryDao, times(1)).save(any(Stoichiometry.class));

    Stoichiometry savedStoichiometry = stoichiometryCaptor.getValue();
    assertEquals(2, savedStoichiometry.getMolecules().size());

    StoichiometryMolecule updatedMolecule =
        result.getMolecules().stream()
            .filter(m -> existingMoleculeId.equals(m.getId()))
            .findFirst()
            .orElse(null);
    assertEquals(3.0, updatedMolecule.getCoefficient(), 0.001);
    assertEquals(150.0, updatedMolecule.getMass(), 0.001);

    StoichiometryMolecule newMol =
        result.getMolecules().stream()
            .filter(m -> "Methane".equals(m.getName()))
            .findFirst()
            .orElse(null);
    assertEquals(MoleculeRole.AGENT, newMol.getRole());
  }

  @Test
  public void whenCopyForReaction_withValidSource_thenCreatesCopyWithNewParent() throws Exception {
    Long sourceParentReactionId = 1L;
    Long targetParentReactionId = 2L;
    Stoichiometry sourceStoichiometry =
        createStoichiometryWith2Molecules(1L, sourceParentReactionId);
    RSChemElement targetParentReaction = createRSChemElement(targetParentReactionId);
    RSChemElement newMolecule = createRSChemElement(99L);

    when(stoichiometryDao.findByParentReactionId(sourceParentReactionId))
        .thenReturn(Optional.of(sourceStoichiometry));

    when(rsChemElementManager.save(any(RSChemElement.class), any(User.class)))
        .thenReturn(newMolecule);

    stoichiometryManager.copyForReaction(sourceParentReactionId, targetParentReaction, user);

    verify(stoichiometryDao, times(2)).save(any(Stoichiometry.class));
    List<Stoichiometry> savedStoichiometries = stoichiometryCaptor.getAllValues();

    Stoichiometry finalSave = savedStoichiometries.get(1);
    assertEquals(targetParentReaction, finalSave.getParentReaction());
    assertEquals(3, finalSave.getMolecules().size());
  }

  @Test
  public void whenCopyForReaction_withNonExistentSource_thenThrowsException() {
    Long sourceParentReactionId = 999L;
    RSChemElement targetParentReaction = createRSChemElement(2L);

    when(stoichiometryDao.findByParentReactionId(sourceParentReactionId))
        .thenReturn(Optional.empty());

    Exception exception =
        assertThrows(
            StoichiometryException.class,
            () ->
                stoichiometryManager.copyForReaction(
                    sourceParentReactionId, targetParentReaction, user));

    assertTrue(
        exception
            .getMessage()
            .contains("No stoichiometry found for reaction id " + sourceParentReactionId));
  }

  @Test
  public void whenCopyForReaction_withIOException_thenThrowsStoichiometryException()
      throws Exception {
    Long sourceParentReactionId = 1L;
    Long targetParentReactionId = 2L;
    Stoichiometry sourceStoichiometry =
        createStoichiometryWith2Molecules(1L, sourceParentReactionId);
    RSChemElement targetParentReaction = createRSChemElement(targetParentReactionId);

    when(stoichiometryDao.findByParentReactionId(sourceParentReactionId))
        .thenReturn(Optional.of(sourceStoichiometry));

    when(rsChemElementManager.save(any(RSChemElement.class), any(User.class)))
        .thenThrow(new IOException("Failed to save molecule"));

    Exception exception =
        assertThrows(
            StoichiometryException.class,
            () ->
                stoichiometryManager.copyForReaction(
                    sourceParentReactionId, targetParentReaction, user));

    assertTrue(
        exception
            .getMessage()
            .contains("Problem saving molecule from SMILES during stoichiometry copy"));
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

  private Stoichiometry createStoichiometryWith2Molecules(Long id, Long parentReactionId) {
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

  private StoichiometryUpdateDTO createStoichiometryUpdateDTO(Long id) {
    StoichiometryMoleculeUpdateDTO molecule =
        StoichiometryMoleculeUpdateDTO.builder()
            .id(2L)
            .coefficient(1.0)
            .mass(46.07)
            .actualAmount(100.0)
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

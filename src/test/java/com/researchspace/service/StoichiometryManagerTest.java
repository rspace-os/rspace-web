package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    when(stoichiometryDao.save(any(Stoichiometry.class)))
        .thenReturn(Stoichiometry.builder().id(1L).parentReaction(parentReaction).build());

    Stoichiometry result =
        stoichiometryManager.createFromAnalysis(analysisDTO, parentReaction, user);

    assertEquals(parentReaction, result.getParentReaction());
    assertEquals(1, result.getMolecules().size());
    StoichiometryMolecule molecule = result.getMolecules().get(0);
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

    when(stoichiometryDao.save(any(Stoichiometry.class)))
        .thenReturn(Stoichiometry.builder().id(1L).parentReaction(parentReaction).build());

    Stoichiometry result =
        stoichiometryManager.createFromAnalysis(analysisDTO, parentReaction, user);

    assertNotNull(result);
    assertEquals(parentReaction, result.getParentReaction());
    assertEquals(1, result.getMolecules().size());

    StoichiometryMolecule molecule = result.getMolecules().get(0);
    assertNull(molecule.getName());
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

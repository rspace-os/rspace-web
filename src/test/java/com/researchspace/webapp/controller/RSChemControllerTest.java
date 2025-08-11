package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.IControllerInputValidator;
import com.researchspace.model.dtos.chemistry.ChemConversionInputDto;
import com.researchspace.model.dtos.chemistry.ChemicalSearchRequestDTO;
import com.researchspace.model.dtos.chemistry.ConvertedStructureDto;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeUpdateDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.chemistry.StoichiometryException;
import com.researchspace.service.impl.RSChemService.ChemicalSearchResults;
import com.researchspace.webapp.controller.RSChemController.ChemEditorInputDto;
import com.researchspace.webapp.controller.RSChemController.ChemSearchResultsPage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

public class RSChemControllerTest {

  @Rule public MockitoRule mockery = MockitoJUnit.rule();
  String chemElementMolString = "";
  RSChemElement chem = null;
  String imageInBase64 = "";
  @Mock private UserManager userMgr;
  @Mock private RSChemElementManager chemMgr;
  @Mock private FolderManager folderManager;
  @Mock private IControllerInputValidator validator;
  @Mock private ChemistryService chemicalService;
  private @InjectMocks RSChemController rsChemController;
  private Principal mockPrincipal;
  private User user;

  @Before
  public void setUp() throws IOException {
    user = TestFactory.createAnyUser("user");
    mockPrincipal = () -> user.getUsername();
    InputStream molInput = getClass().getResourceAsStream("/TestResources/Amfetamine.mol");

    chemElementMolString = IOUtils.toString(molInput, StandardCharsets.UTF_8);
    molInput.close();
    chem = TestFactory.createChemElement(1L, 2L);

    imageInBase64 =
        "base64String,AADDEEDDASDGFDFGFDGDFHTRFHFHHHREFGDDFERERRTYETEAFDKSFDKSKLREOFK4KK4KFKDKFJHKSKF";
  }

  @Test
  public void testSearchChemElement() {
    final Folder rootFolder = TestFactory.createAFolder("root", user);
    final ChemicalSearchResults hits = new ChemicalSearchResults();
    String smile = "CCC(C1)";
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(user);
    when(folderManager.getRootFolderForUser(user)).thenReturn(rootFolder);
    when(chemicalService.searchChemicals(smile, "SUBSTRUCTURE", 0, 10, user)).thenReturn(hits);

    ChemSearchResultsPage resultsPage =
        rsChemController.searchChemicals(
            new ChemicalSearchRequestDTO(smile, 0, 10, "SUBSTRUCTURE"));
    assertNull(resultsPage.getTotalHitCount());
  }

  private void generalMocks() {
    when(userMgr.getUserByUsername(user.getUsername())).thenReturn(user);
  }

  @Test
  public void loadChemElements() {
    ChemEditorInputDto chemEditorInput = new ChemEditorInputDto();
    generalMocks();
    when(chemicalService.getChemicalEditorInput(2L, null, user)).thenReturn(chemEditorInput);

    AjaxReturnObject<ChemEditorInputDto> data =
        rsChemController.loadChemElements(chem.getId(), null, mockPrincipal);
    assertNotNull(data.getData());
  }

  @Test
  public void convertOK() {
    ChemConversionInputDto input = new ChemConversionInputDto(chemElementMolString, "", "smiles");
    ConvertedStructureDto converted = ConvertedStructureDto.builder().structure("CCC(C1)").build();
    when(chemicalService.convert(input)).thenReturn(converted);
    ResponseEntity<ConvertedStructureDto> cs =
        rsChemController.convert(input, new BeanPropertyBindingResult(input, "chem"));
    assertEquals(HttpStatus.OK, cs.getStatusCode());
    assertEquals("CCC(C1)", cs.getBody().getStructure());
    assertNull(cs.getBody().getErrorMessage());
  }

  @Test
  public void conversionExceptionHandledOK() {
    ChemConversionInputDto input = new ChemConversionInputDto(chemElementMolString, "", "smiles");
    when(chemicalService.convert(input)).thenThrow(new RuntimeException("error"));
    ResponseEntity<ConvertedStructureDto> cs =
        rsChemController.convert(input, new BeanPropertyBindingResult(input, "chem"));
    assertEquals(HttpStatus.BAD_REQUEST, cs.getStatusCode());
    assertEquals("error", cs.getBody().getErrorMessage());
  }

  @Test
  public void conversionInputValidation() {
    ChemConversionInputDto input = new ChemConversionInputDto(null, "", "smiles");
    verify(chemicalService, never()).convert(input);
    // simulate error after validation

    String msg = "errorMessage";
    BindingResult br = Mockito.mock(BindingResult.class);
    Mockito.when(br.hasErrors()).thenReturn(Boolean.TRUE);
    Mockito.when(
            validator.populateErrorList(
                Mockito.any(BindingResult.class), Mockito.any(ErrorList.class)))
        .thenReturn(ErrorList.of(msg));
    ResponseEntity<ConvertedStructureDto> cs = rsChemController.convert(input, br);
    assertEquals(HttpStatus.BAD_REQUEST, cs.getStatusCode());
    assertEquals(msg, cs.getBody().getErrorMessage());
  }

  @Test
  public void getImageChem() throws IOException {
    final RSChemElement chem = TestFactory.createChemElement(1L, 2L);
    chem.setDataImage(new byte[] {1, 2, 3, 4, 5});
    generalMocks();
    when(chemicalService.getChemicalElementByRevision(2L, null, user)).thenReturn(chem);
    ResponseEntity<byte[]> response = rsChemController.getImageChem(2L, 123L, mockPrincipal, null);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  public void whenGetStoichiometry_thenReturnStoichiometryData() throws IOException {
    RSChemElement parentReaction = TestFactory.createChemElement(1L, 2L);
    Stoichiometry stoichiometry = makeStoichiometry(parentReaction);

    StoichiometryDTO stoichiometryDTO = new StoichiometryDTO();
    stoichiometryDTO.setId(1L);
    stoichiometryDTO.setParentReactionId(parentReaction.getId());

    StoichiometryMoleculeDTO reactantDTO =
        StoichiometryMoleculeDTO.builder()
            .id(2L)
            .rsChemElementId(3L)
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .molecularWeight(46.07)
            .build();

    StoichiometryMoleculeDTO productDTO =
        StoichiometryMoleculeDTO.builder()
            .id(3L)
            .rsChemElementId(4L)
            .role(MoleculeRole.PRODUCT)
            .formula("C2H4O")
            .name("Acetaldehyde")
            .smiles("CC=O")
            .molecularWeight(44.05)
            .build();

    stoichiometryDTO.setMolecules(List.of(reactantDTO, productDTO));

    when(userMgr.getUserByUsername(user.getUsername())).thenReturn(user);
    when(chemicalService.getStoichiometry(2L, null, user)).thenReturn(Optional.of(stoichiometry));

    AjaxReturnObject<StoichiometryDTO> response =
        rsChemController.getStoichiometry(2L, null, mockPrincipal);

    assertEquals(Long.valueOf(1L), response.getData().getId());
    assertEquals(parentReaction.getId(), response.getData().getParentReactionId());
    assertEquals(2, response.getData().getMolecules().size());

    List<StoichiometryMoleculeDTO> reactants =
        response.getData().getMolecules().stream()
            .filter(m -> m.getRole() == MoleculeRole.REACTANT)
            .collect(Collectors.toList());
    assertEquals(1, reactants.size());
    assertEquals("Ethanol", reactants.get(0).getName());
    assertEquals("C2H6O", reactants.get(0).getFormula());

    List<StoichiometryMoleculeDTO> products =
        response.getData().getMolecules().stream()
            .filter(m -> m.getRole() == MoleculeRole.PRODUCT)
            .collect(java.util.stream.Collectors.toList());
    assertEquals(1, products.size());
    assertEquals("Acetaldehyde", products.get(0).getName());
    assertEquals("C2H4O", products.get(0).getFormula());
  }

  @NotNull
  private static Stoichiometry makeStoichiometry(RSChemElement parentReaction) throws IOException {
    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(1L);
    stoichiometry.setParentReaction(parentReaction);

    StoichiometryMolecule reactant =
        StoichiometryMolecule.builder()
            .id(2L)
            .stoichiometry(stoichiometry)
            .rsChemElement(TestFactory.createChemElement(3L, 3L))
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .molecularWeight(46.07)
            .build();

    StoichiometryMolecule product =
        StoichiometryMolecule.builder()
            .id(3L)
            .stoichiometry(stoichiometry)
            .rsChemElement(TestFactory.createChemElement(4L, 4L))
            .role(MoleculeRole.PRODUCT)
            .formula("C2H4O")
            .name("Acetaldehyde")
            .smiles("CC=O")
            .molecularWeight(44.05)
            .build();

    stoichiometry.addMolecule(reactant);
    stoichiometry.addMolecule(product);
    return stoichiometry;
  }

  @Test
  public void whenGetStoichiometryNotFound_thenReturnError() {
    when(chemicalService.getStoichiometry(2L, 1, user)).thenReturn(java.util.Optional.empty());

    AjaxReturnObject<StoichiometryDTO> response =
        rsChemController.getStoichiometry(2L, 1, mockPrincipal);

    assertFalse(response.isSuccess());
    assertEquals(
        "No stoichiometry found for chemical with id 2 and revision 1",
        response.getError().getAllErrorMessagesAsStringsSeparatedBy(" "));
  }

  @Test
  public void whenSaveStoichiometry_thenReturnSavedStoichiometry() throws IOException {
    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(1L);
    RSChemElement parentReaction = TestFactory.createChemElement(1L, 2L);
    stoichiometry.setParentReaction(parentReaction);

    StoichiometryDTO stoichiometryDTO = new StoichiometryDTO();
    stoichiometryDTO.setId(1L);
    stoichiometryDTO.setParentReactionId(parentReaction.getId());

    when(userMgr.getUserByUsername(user.getUsername())).thenReturn(user);
    when(chemicalService.createStoichiometry(2L, null, user)).thenReturn(stoichiometry);

    AjaxReturnObject<StoichiometryDTO> response =
        rsChemController.saveStoichiometry(2L, null, mockPrincipal);

    assertNotNull(response);
    assertNotNull(response.getData());
    assertEquals(Long.valueOf(1L), response.getData().getId());
    assertEquals(parentReaction.getId(), response.getData().getParentReactionId());
  }

  @Test
  public void whenSaveStoichiometryFails_thenReturnError() {
    when(userMgr.getUserByUsername(user.getUsername())).thenReturn(user);
    when(chemicalService.createStoichiometry(2L, null, user))
        .thenThrow(new StoichiometryException("Error creating stoichiometry"));

    AjaxReturnObject<StoichiometryDTO> response =
        rsChemController.saveStoichiometry(2L, null, mockPrincipal);

    assertEquals(
        "Problem creating stoichiometry for chemId: 2. Error creating stoichiometry",
        response.getError().getAllErrorMessagesAsStringsSeparatedBy(" "));
  }

  @Test
  public void whenUpdateStoichiometry_thenReturnUpdatedStoichiometry() throws IOException {
    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(1L);
    RSChemElement parentReaction = TestFactory.createChemElement(1L, 2L);
    stoichiometry.setParentReaction(parentReaction);

    StoichiometryMoleculeUpdateDTO moleculeUpdateDTO =
        StoichiometryMoleculeUpdateDTO.builder()
            .id(1L)
            .coefficient(2.0)
            .mass(100.0)
            .moles(0.5)
            .expectedAmount(200.0)
            .actualAmount(180.0)
            .actualYield(90.0)
            .limitingReagent(true)
            .notes("Updated notes")
            .build();

    StoichiometryUpdateDTO stoichiometryUpdateDTO =
        StoichiometryUpdateDTO.builder()
            .id(1L)
            .molecules(java.util.List.of(moleculeUpdateDTO))
            .build();

    StoichiometryDTO updatedStoichiometryDTO = new StoichiometryDTO();
    updatedStoichiometryDTO.setId(1L);
    updatedStoichiometryDTO.setParentReactionId(parentReaction.getId());

    when(userMgr.getUserByUsername(user.getUsername())).thenReturn(user);
    when(chemicalService.updateStoichiometry(stoichiometryUpdateDTO, user))
        .thenReturn(stoichiometry);

    AjaxReturnObject<StoichiometryDTO> response =
        rsChemController.updateStoichiometry(1L, stoichiometryUpdateDTO, mockPrincipal);

    assertNotNull(response);
    assertNotNull(response.getData());
    assertEquals(Long.valueOf(1L), response.getData().getId());
    assertEquals(parentReaction.getId(), response.getData().getParentReactionId());
  }

  @Test
  public void whenUpdateStoichiometryFails_thenReturnError() {
    StoichiometryMoleculeUpdateDTO moleculeUpdateDTO =
        StoichiometryMoleculeUpdateDTO.builder()
            .id(1L)
            .coefficient(2.0)
            .mass(100.0)
            .moles(0.5)
            .expectedAmount(200.0)
            .actualAmount(180.0)
            .actualYield(90.0)
            .limitingReagent(true)
            .notes("Updated notes")
            .build();

    StoichiometryUpdateDTO stoichiometryUpdateDTO =
        StoichiometryUpdateDTO.builder()
            .id(1L)
            .molecules(java.util.List.of(moleculeUpdateDTO))
            .build();

    when(userMgr.getUserByUsername(user.getUsername())).thenReturn(user);
    when(chemicalService.updateStoichiometry(stoichiometryUpdateDTO, user))
        .thenThrow(new StoichiometryException("Update failed"));

    AjaxReturnObject<StoichiometryDTO> response =
        rsChemController.updateStoichiometry(1L, stoichiometryUpdateDTO, mockPrincipal);

    assertEquals(
        "Error updating stoichiometry: Update failed",
        response.getError().getAllErrorMessagesAsStringsSeparatedBy(" "));
  }

  @Test
  public void whenDeleteStoichiometry_thenReturnSuccess() {
    when(userMgr.getUserByUsername(user.getUsername())).thenReturn(user);
    when(chemicalService.deleteStoichiometry(1L, user)).thenReturn(true);

    AjaxReturnObject<Boolean> response = rsChemController.deleteStoichiometry(1L, mockPrincipal);

    assertEquals(true, response.getData());
    assertTrue(response.isSuccess());
  }

  @Test
  public void whenDeleteNonExistentStoichiometry_thenReturnError() {
    when(chemicalService.deleteStoichiometry(999L, user)).thenReturn(false);

    AjaxReturnObject<Boolean> response = rsChemController.deleteStoichiometry(999L, mockPrincipal);

    assertFalse(response.isSuccess());
  }
}

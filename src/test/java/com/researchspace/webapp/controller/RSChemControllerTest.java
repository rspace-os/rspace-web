package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.IControllerInputValidator;
import com.researchspace.model.dtos.chemistry.ChemConversionInputDto;
import com.researchspace.model.dtos.chemistry.ChemicalSearchRequestDTO;
import com.researchspace.model.dtos.chemistry.ConvertedStructureDto;
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
import com.researchspace.service.impl.RSChemService.ChemicalSearchResults;
import com.researchspace.webapp.controller.RSChemController.ChemEditorInputDto;
import com.researchspace.webapp.controller.RSChemController.ChemSearchResultsPage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
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
}

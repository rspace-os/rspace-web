package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeUpdateDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.service.StoichiometryService;
import com.researchspace.service.chemistry.StoichiometryException;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import org.apache.shiro.authz.AuthorizationException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StoichiometryApiControllerTest {

  @Mock private StoichiometryService stoichiometryService;

  @InjectMocks private StoichiometryApiController controller;

  private User user;

  @BeforeEach
  public void setUp() {
    user = TestFactory.createAnyUser("user");
  }

  @Test
  public void whenGetStoichiometrySuccess_thenReturnStoichiometry() throws IOException {
    RSChemElement parentReaction = TestFactory.createChemElement(null, 1L);
    parentReaction.setRecord(TestFactory.createAnySD());
    Stoichiometry stoichiometry = makeStoichiometry(parentReaction);

    when(stoichiometryService.getByParentChemical(1L, null, user)).thenReturn(stoichiometry);

    StoichiometryDTO response = controller.getStoichiometry(1L, null, user);

    assertEquals(Long.valueOf(1L), response.getId());
    assertEquals(parentReaction.getId(), response.getParentReactionId());
    assertEquals(2, response.getMolecules().size());

    List<StoichiometryMoleculeDTO> reactants =
        response.getMolecules().stream()
            .filter(m -> m.getRole() == MoleculeRole.REACTANT)
            .collect(Collectors.toList());
    assertEquals(1, reactants.size());
    assertEquals("Ethanol", reactants.get(0).getName());
    assertEquals("C2H6O", reactants.get(0).getFormula());

    List<StoichiometryMoleculeDTO> products =
        response.getMolecules().stream()
            .filter(m -> m.getRole() == MoleculeRole.PRODUCT)
            .collect(java.util.stream.Collectors.toList());
    assertEquals(1, products.size());
    assertEquals("Acetaldehyde", products.get(0).getName());
    assertEquals("C2H4O", products.get(0).getFormula());
  }

  @Test
  public void whenGetStoichiometryNotFound_thenReturnError() {
    when(stoichiometryService.getByParentChemical(2L, 1, user))
        .thenThrow(
            new NotFoundException("No stoichiometry found for chemical with id 2 and revision 1"));

    NotFoundException ex =
        assertThrows(NotFoundException.class, () -> controller.getStoichiometry(2L, 1, user));

    assertEquals("No stoichiometry found for chemical with id 2 and revision 1", ex.getMessage());
  }

  @Test
  public void whenSaveStoichiometry_thenReturnSavedStoichiometry() throws IOException {
    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(1L);
    RSChemElement parentReaction = TestFactory.createChemElement(1L, 2L);
    stoichiometry.setParentReaction(parentReaction);

    when(stoichiometryService.create(2L, null, user)).thenReturn(stoichiometry);

    StoichiometryDTO response = controller.saveStoichiometry(2L, null, user);

    assertNotNull(response);
    assertEquals(Long.valueOf(1L), response.getId());
    assertEquals(parentReaction.getId(), response.getParentReactionId());
  }

  @Test
  public void whenSaveStoichiometryFails_thenReturnError() {
    when(stoichiometryService.create(2L, null, user))
        .thenThrow(new StoichiometryException("Error creating stoichiometry"));

    StoichiometryException ex =
        assertThrows(
            StoichiometryException.class, () -> controller.saveStoichiometry(2L, null, user));

    assertEquals("Error creating stoichiometry", ex.getMessage());
  }

  @Test
  public void whenUpdateStoichiometry_thenReturnUpdatedStoichiometry() throws IOException {
    RSChemElement parentReaction = TestFactory.createChemElement(1L, 2L);
    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(1L);
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

    when(stoichiometryService.update(1L, stoichiometryUpdateDTO, user)).thenReturn(stoichiometry);

    StoichiometryDTO response = controller.updateStoichiometry(1L, stoichiometryUpdateDTO, user);

    assertNotNull(response);
    assertEquals(Long.valueOf(1L), response.getId());
    assertEquals(parentReaction.getId(), response.getParentReactionId());
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

    when(stoichiometryService.update(1L, stoichiometryUpdateDTO, user))
        .thenThrow(new StoichiometryException("Update failed"));

    StoichiometryException ex =
        assertThrows(
            StoichiometryException.class,
            () -> controller.updateStoichiometry(1L, stoichiometryUpdateDTO, user));

    assertEquals("Update failed", ex.getMessage());
  }

  @Test
  public void whenDeleteStoichiometry_thenReturnSuccess() {
    doNothing().when(stoichiometryService).delete(1L, user);

    Boolean response = controller.deleteStoichiometry(1L, user);

    assertEquals(true, response);
    assertTrue(response);
  }

  @Test
  public void whenDeleteNonExistentStoichiometry_thenReturnError() {
    doThrow(new NotFoundException("Error deleting stoichiometry with id 999"))
        .when(stoichiometryService)
        .delete(999L, user);

    assertThrows(NotFoundException.class, () -> controller.deleteStoichiometry(999L, user));
  }

  @Test
  public void whenGetStoichiometryPermissionDenied_thenError() {
    doThrow(new AuthorizationException("User does not have permission to read stoichiometry"))
        .when(stoichiometryService)
        .getByParentChemical(2L, null, user);

    AuthorizationException ex =
        assertThrows(
            AuthorizationException.class, () -> controller.getStoichiometry(2L, null, user));
    assertEquals("User does not have permission to read stoichiometry", ex.getMessage());
  }

  @Test
  public void whenSaveStoichiometryPermissionDenied_thenError() {
    doThrow(new StoichiometryException("User does not have permission to save stoichiometry"))
        .when(stoichiometryService)
        .create(2L, null, user);

    StoichiometryException ex =
        assertThrows(
            StoichiometryException.class, () -> controller.saveStoichiometry(2L, null, user));
    assertEquals("User does not have permission to save stoichiometry", ex.getMessage());
  }

  @Test
  public void whenUpdateStoichiometryPermissionDenied_thenError() {
    StoichiometryUpdateDTO stoichiometryUpdateDTO = StoichiometryUpdateDTO.builder().id(1L).build();

    doThrow(new StoichiometryException("User does not have permission to update stoichiometry"))
        .when(stoichiometryService)
        .update(1L, stoichiometryUpdateDTO, user);

    StoichiometryException ex =
        assertThrows(
            StoichiometryException.class,
            () -> controller.updateStoichiometry(1L, stoichiometryUpdateDTO, user));
    assertEquals("User does not have permission to update stoichiometry", ex.getMessage());
  }

  @Test
  public void whenDeleteStoichiometryPermissionDenied_thenError() {
    doThrow(new StoichiometryException("User does not have permission to delete stoichiometry"))
        .when(stoichiometryService)
        .delete(1L, user);

    StoichiometryException ex =
        assertThrows(StoichiometryException.class, () -> controller.deleteStoichiometry(1L, user));
    assertEquals("User does not have permission to delete stoichiometry", ex.getMessage());
  }

  @NotNull
  private static Stoichiometry makeStoichiometry(RSChemElement parentReaction) throws IOException {
    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(1L);
    StructuredDocument doc = TestFactory.createAnySD();
    parentReaction.setRecord(doc);
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

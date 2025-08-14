package com.researchspace.api.v1.controller;

import static com.hp.hpl.jena.util.iterator.Filter.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeUpdateDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.StoichiometryManager;
import com.researchspace.service.chemistry.StoichiometryException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StoichiometryApiControllerTest {

  @Mock private ChemistryService chemicalService;
  @Mock private StoichiometryManager stoichiometryManager;
  @Mock private IPermissionUtils permissionUtils;

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

    when(stoichiometryManager.get(1L)).thenReturn(stoichiometry);
    when(permissionUtils.isPermitted((BaseRecord) parentReaction.getRecord(), PermissionType.WRITE, user))
        .thenReturn(true);
    when(chemicalService.getStoichiometry(1L, null, user)).thenReturn(Optional.of(stoichiometry));

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

  @Test
  public void whenGetStoichiometryNotFound_thenReturnError() throws IOException {
    // Permit check passes via chemId
    RSChemElement chemical = TestFactory.createChemElement(2L, 2L);
    when(stoichiometryManager.get(2L)).thenReturn(null);
    when(chemicalService.getChemicalElementByRevision(2L, null, user)).thenReturn(chemical);
    when(permissionUtils.isPermitted((BaseRecord) chemical.getRecord(), PermissionType.WRITE, user))
        .thenReturn(true);

    when(chemicalService.getStoichiometry(2L, 1, user)).thenReturn(java.util.Optional.empty());

    NotFoundException ex =
        assertThrows(NotFoundException.class, () -> controller.getStoichiometry(2L, 1, user));

    assertEquals("No stoichiometry found for chemical with id 2 and revision 1", ex.getMessage());
  }

  @Test
  public void whenSaveStoichiometry_thenReturnSavedStoichiometry() throws IOException {
    // Arrange permission for chemId path
    RSChemElement chemical = TestFactory.createChemElement(2L, 2L);
    when(stoichiometryManager.get(2L)).thenReturn(null);
    when(chemicalService.getChemicalElementByRevision(2L, null, user)).thenReturn(chemical);
    when(permissionUtils.isPermitted((BaseRecord) chemical.getRecord(), PermissionType.WRITE, user))
        .thenReturn(true);

    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(1L);
    RSChemElement parentReaction = TestFactory.createChemElement(1L, 2L);
    stoichiometry.setParentReaction(parentReaction);

    when(chemicalService.createStoichiometry(2L, null, user)).thenReturn(stoichiometry);

    StoichiometryDTO response = controller.saveStoichiometry(2L, null, user);

    assertNotNull(response);
    assertEquals(Long.valueOf(1L), response.getId());
    assertEquals(parentReaction.getId(), response.getParentReactionId());
  }

  @Test
  public void whenSaveStoichiometryFails_thenReturnError() throws IOException {
    // Allow permission to progress
    RSChemElement chemical = TestFactory.createChemElement(2L, 2L);
    when(stoichiometryManager.get(2L)).thenReturn(null);
    when(chemicalService.getChemicalElementByRevision(2L, null, user)).thenReturn(chemical);
    when(permissionUtils.isPermitted((BaseRecord) chemical.getRecord(), PermissionType.WRITE, user))
        .thenReturn(true);

    when(chemicalService.createStoichiometry(2L, null, user))
        .thenThrow(new StoichiometryException("Error creating stoichiometry"));

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> controller.saveStoichiometry(2L, null, user));

    assertEquals(
        "Problem creating stoichiometry for chemId: 2. Error creating stoichiometry",
        ex.getMessage());
  }

  @Test
  public void whenUpdateStoichiometry_thenReturnUpdatedStoichiometry() throws IOException {
    // Arrange permission via stoichiometry id path
    RSChemElement parentReaction = TestFactory.createChemElement(1L, 2L);
    Stoichiometry existing = new Stoichiometry();
    existing.setId(1L);
    existing.setParentReaction(parentReaction);
    when(stoichiometryManager.get(1L)).thenReturn(existing);
    when(permissionUtils.isPermitted(
            (BaseRecord) parentReaction.getRecord(), PermissionType.WRITE, user))
        .thenReturn(true);

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

    when(chemicalService.updateStoichiometry(stoichiometryUpdateDTO, user))
        .thenReturn(stoichiometry);

    StoichiometryDTO response = controller.updateStoichiometry(1L, stoichiometryUpdateDTO, user);

    assertNotNull(response);
    assertEquals(Long.valueOf(1L), response.getId());
    assertEquals(parentReaction.getId(), response.getParentReactionId());
  }

  @Test
  public void whenUpdateStoichiometryFails_thenReturnError() throws IOException {
    // Permission allowed for stoich id path
    RSChemElement parentReaction = TestFactory.createChemElement(1L, 2L);
    Stoichiometry existing = new Stoichiometry();
    existing.setId(1L);
    existing.setParentReaction(parentReaction);
    when(stoichiometryManager.get(1L)).thenReturn(existing);
    when(permissionUtils.isPermitted(
            (BaseRecord) parentReaction.getRecord(), PermissionType.WRITE, user))
        .thenReturn(true);

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

    when(chemicalService.updateStoichiometry(stoichiometryUpdateDTO, user))
        .thenThrow(new StoichiometryException("Update failed"));

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> controller.updateStoichiometry(1L, stoichiometryUpdateDTO, user));

    assertEquals("Error updating stoichiometry: Update failed", ex.getMessage());
  }

  @Test
  public void whenDeleteStoichiometry_thenReturnSuccess() throws IOException {
    // Permission allowed for stoich id path
    RSChemElement parentReaction = TestFactory.createChemElement(1L, 2L);
    Stoichiometry existing = new Stoichiometry();
    existing.setId(1L);
    existing.setParentReaction(parentReaction);
    when(stoichiometryManager.get(1L)).thenReturn(existing);
    when(permissionUtils.isPermitted(
            (BaseRecord) parentReaction.getRecord(), PermissionType.WRITE, user))
        .thenReturn(true);

    when(chemicalService.deleteStoichiometry(1L, user)).thenReturn(true);

    Boolean response = controller.deleteStoichiometry(1L, user);

    assertEquals(true, response);
    assertTrue(response);
  }

  @Test
  public void whenDeleteNonExistentStoichiometry_thenReturnError() throws IOException {
    // Permission allowed for stoich id path
    RSChemElement parentReaction = TestFactory.createChemElement(1L, 2L);
    Stoichiometry existing = new Stoichiometry();
    existing.setId(999L);
    existing.setParentReaction(parentReaction);
    when(stoichiometryManager.get(999L)).thenReturn(existing);
    when(permissionUtils.isPermitted(
            (BaseRecord) parentReaction.getRecord(), PermissionType.WRITE, user))
        .thenReturn(true);

    when(chemicalService.deleteStoichiometry(999L, user)).thenReturn(false);

    assertThrows(NotFoundException.class, () -> controller.deleteStoichiometry(999L, user));
  }

  @Test
  public void whenGetStoichiometryPermissionDenied_thenError() throws IOException {
    RSChemElement chemical = TestFactory.createChemElement(2L, 2L);
    when(stoichiometryManager.get(2L)).thenReturn(null);
    when(chemicalService.getChemicalElementByRevision(2L, null, user)).thenReturn(chemical);
    when(permissionUtils.isPermitted((BaseRecord) chemical.getRecord(), PermissionType.WRITE, user))
        .thenReturn(false);

    StoichiometryException ex =
        assertThrows(
            StoichiometryException.class, () -> controller.getStoichiometry(2L, null, user));
    assertEquals("User does not have permission to retrieve stoichiometry", ex.getMessage());
  }

  @Test
  public void whenSaveStoichiometryPermissionDenied_thenError() throws IOException {
    RSChemElement chemical = TestFactory.createChemElement(2L, 2L);
    when(stoichiometryManager.get(2L)).thenReturn(null);
    when(chemicalService.getChemicalElementByRevision(2L, null, user)).thenReturn(chemical);
    when(permissionUtils.isPermitted((BaseRecord) chemical.getRecord(), PermissionType.WRITE, user))
        .thenReturn(false);

    StoichiometryException ex =
        assertThrows(
            StoichiometryException.class, () -> controller.saveStoichiometry(2L, null, user));
    assertEquals("User does not have permission to save stoichiometry", ex.getMessage());
  }

  @Test
  public void whenUpdateStoichiometryPermissionDenied_thenError() throws IOException {
    RSChemElement parentReaction = TestFactory.createChemElement(1L, 2L);
    Stoichiometry existing = new Stoichiometry();
    existing.setId(1L);
    existing.setParentReaction(parentReaction);
    when(stoichiometryManager.get(1L)).thenReturn(existing);
    when(permissionUtils.isPermitted(
            (BaseRecord) parentReaction.getRecord(), PermissionType.WRITE, user))
        .thenReturn(false);

    StoichiometryUpdateDTO stoichiometryUpdateDTO = StoichiometryUpdateDTO.builder().id(1L).build();

    StoichiometryException ex =
        assertThrows(
            StoichiometryException.class,
            () -> controller.updateStoichiometry(1L, stoichiometryUpdateDTO, user));
    assertEquals("User does not have permission to update stoichiometry", ex.getMessage());
  }

  @Test
  public void whenDeleteStoichiometryPermissionDenied_thenError() throws IOException {
    RSChemElement parentReaction = TestFactory.createChemElement(1L, 2L);
    Stoichiometry existing = new Stoichiometry();
    existing.setId(1L);
    existing.setParentReaction(parentReaction);
    when(stoichiometryManager.get(1L)).thenReturn(existing);
    when(permissionUtils.isPermitted(
            (BaseRecord) parentReaction.getRecord(), PermissionType.WRITE, user))
        .thenReturn(false);

    StoichiometryException ex =
        assertThrows(StoichiometryException.class, () -> controller.deleteStoichiometry(1L, user));
    assertEquals("User does not have permission to delete stoichiometry", ex.getMessage());
  }

  @Test
  public void whenPermissionCheckCannotResolveResource_thenNotFound() {
    when(stoichiometryManager.get(1L)).thenReturn(null);
    when(chemicalService.getChemicalElementByRevision(1L, null, user)).thenReturn(null);

    NotFoundException nf =
        assertThrows(
            NotFoundException.class,
            () ->
                controller.updateStoichiometry(
                    1L, StoichiometryUpdateDTO.builder().id(1L).build(), user));
    assertEquals("Stoichiometry not found", nf.getMessage());
  }
}

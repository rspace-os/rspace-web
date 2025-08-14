package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.StoichiometryManager;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.chemistry.StoichiometryException;
import java.io.IOException;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StoichiometryServiceImplTest {

  @Mock private ChemistryService chemistryService;
  @Mock private StoichiometryManager stoichiometryManager;
  @Mock private IPermissionUtils permissionUtils;
  @Mock private ChemistryProvider chemistryProvider;
  @Mock private RSChemElementManager rsChemElementManager;

  @InjectMocks private StoichiometryServiceImpl service;

  private User user;

  @BeforeEach
  void setUp() {
    user = TestFactory.createAnyUser("user");
  }

  @Test
  void getByParentChemical_whenNotFound_throwsNotFound() {
    when(stoichiometryManager.findByParentReactionId(1L)).thenReturn(Optional.empty());

    NotFoundException ex =
        assertThrows(NotFoundException.class, () -> service.getByParentChemical(1L, 2, user));

    assertTrue(
        ex.getMessage().contains("No stoichiometry found for chemical with id 1 and revision 2"));
  }

  @Test
  void getByParentChemical_whenNoPermission_throwsAuthz() throws Exception {
    Stoichiometry stoich = makeStoichiometryWithRecord(10L);
    when(stoichiometryManager.findByParentReactionId(1L)).thenReturn(Optional.of(stoich));
    when(permissionUtils.isPermitted(any(), eq(PermissionType.READ), eq(user))).thenReturn(false);

    Exception ex =
        assertThrows(
            AuthorizationException.class, () -> service.getByParentChemical(1L, null, user));
    assertEquals(
        "User does not have read permissions on document containing stoichiometry",
        ex.getMessage());
  }

  @Test
  void getByParentChemical_whenOk_returnsStoichiometry() throws Exception {
    Stoichiometry stoich = makeStoichiometryWithRecord(10L);
    when(stoichiometryManager.findByParentReactionId(1L)).thenReturn(Optional.of(stoich));
    when(permissionUtils.isPermitted(any(), eq(PermissionType.READ), eq(user))).thenReturn(true);

    Stoichiometry result = service.getByParentChemical(1L, null, user);

    assertEquals(stoich, result);
  }

  @Test
  void create_whenOwningRecordMissing_throwsNotFound() {
    when(chemistryService.getChemicalElementByRevision(2L, null, user))
        .thenReturn(mock(RSChemElement.class));
    NotFoundException ex =
        assertThrows(NotFoundException.class, () -> service.create(2L, null, user));

    assertTrue(ex.getMessage().contains("Record containing chemical with id 2 not found"));
  }

  @Test
  void create_whenNoWritePermission_throwsAuthz() throws Exception {
    RSChemElement chem = TestFactory.createChemElement(null, 2L);
    Record record = TestFactory.createAnySD();
    chem.setRecord(record);

    when(chemistryService.getChemicalElementByRevision(2L, null, user)).thenReturn(chem);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(false);

    assertThrows(AuthorizationException.class, () -> service.create(2L, null, user));
  }

  @Test
  void create_whenExistingAlreadyPresent_throwsStoichException() throws Exception {
    RSChemElement chem = TestFactory.createChemElement(null, 2L);
    Record record = TestFactory.createAnySD();
    chem.setRecord(record);
    when(chemistryService.getChemicalElementByRevision(2L, null, user)).thenReturn(chem);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);

    Stoichiometry existing = new Stoichiometry();
    existing.setId(99L);
    when(stoichiometryManager.findByParentReactionId(2L)).thenReturn(Optional.of(existing));

    StoichiometryException ex =
        assertThrows(StoichiometryException.class, () -> service.create(2L, null, user));
    assertTrue(ex.getMessage().contains("Stoichiometry already exists for reaction chemId=2"));
  }

  @Test
  void create_whenNoAnalysisReturned_throwsStoichException() throws Exception {
    RSChemElement chem = TestFactory.createChemElement(null, 2L);
    Record record = TestFactory.createAnySD();
    chem.setRecord(record);
    when(chemistryService.getChemicalElementByRevision(2L, null, user)).thenReturn(chem);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);
    when(stoichiometryManager.findByParentReactionId(2L)).thenReturn(Optional.empty());

    when(chemistryProvider.getStoichiometry(chem)).thenReturn(Optional.empty());

    StoichiometryException ex =
        assertThrows(StoichiometryException.class, () -> service.create(2L, null, user));
    assertTrue(ex.getMessage().contains("Unable to generate stoichiometry"));
  }

  @Test
  void create_whenCreateFromAnalysisThrowsIOException_wrapsAsStoichException() throws Exception {
    RSChemElement chem = TestFactory.createChemElement(null, 2L);
    Record record = TestFactory.createAnySD();
    chem.setRecord(record);
    when(chemistryService.getChemicalElementByRevision(2L, null, user)).thenReturn(chem);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);
    when(stoichiometryManager.findByParentReactionId(2L)).thenReturn(Optional.empty());

    ElementalAnalysisDTO analysis = mock(ElementalAnalysisDTO.class);
    when(chemistryProvider.getStoichiometry(chem)).thenReturn(Optional.of(analysis));
    when(stoichiometryManager.createFromAnalysis(eq(analysis), eq(chem), eq(user)))
        .thenThrow(new IOException("IO fail"));

    StoichiometryException ex =
        assertThrows(StoichiometryException.class, () -> service.create(2L, null, user));
    assertTrue(ex.getMessage().contains("Problem while creating new Stoichiometry: IO fail"));
  }

  @Test
  void create_whenOk_returnsNewStoichiometry() throws Exception {
    RSChemElement chem = TestFactory.createChemElement(null, 2L);
    Record record = TestFactory.createAnySD();
    chem.setRecord(record);
    when(chemistryService.getChemicalElementByRevision(2L, null, user)).thenReturn(chem);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);
    when(stoichiometryManager.findByParentReactionId(2L)).thenReturn(Optional.empty());

    ElementalAnalysisDTO analysis = mock(ElementalAnalysisDTO.class);
    when(chemistryProvider.getStoichiometry(chem)).thenReturn(Optional.of(analysis));

    Stoichiometry created = new Stoichiometry();
    created.setId(7L);
    created.setParentReaction(chem);
    when(stoichiometryManager.createFromAnalysis(eq(analysis), eq(chem), eq(user)))
        .thenReturn(created);

    Stoichiometry result = service.create(2L, null, user);
    assertEquals(7L, result.getId().longValue());
    assertEquals(chem, result.getParentReaction());
  }

  @Test
  void update_whenOwningRecordMissing_throwsNotFound() throws Exception {
    Stoichiometry existing = new Stoichiometry();
    existing.setId(3L);
    RSChemElement parent = TestFactory.createChemElement(null, 123L);
    existing.setParentReaction(parent);
    when(stoichiometryManager.get(3L)).thenReturn(existing);

    NotFoundException ex =
        assertThrows(
            NotFoundException.class,
            () -> service.update(3L, mock(StoichiometryUpdateDTO.class), user));
    assertTrue(ex.getMessage().contains("Record containing stoichiometry with id 3 not found"));
  }

  @Test
  void update_whenNoWritePermission_throwsAuthz() throws Exception {
    Stoichiometry existing = new Stoichiometry();
    existing.setId(3L);
    RSChemElement parent = TestFactory.createChemElement(null, 123L);
    parent.setRecord(TestFactory.createAnySD());
    existing.setParentReaction(parent);
    when(stoichiometryManager.get(3L)).thenReturn(existing);

    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(false);

    assertThrows(
        AuthorizationException.class,
        () -> service.update(3L, mock(StoichiometryUpdateDTO.class), user));
  }

  @Test
  void update_whenOk_returnsUpdatedStoichiometry() throws Exception {
    Stoichiometry existing = new Stoichiometry();
    existing.setId(3L);
    RSChemElement parent = TestFactory.createChemElement(null, 123L);
    parent.setRecord(TestFactory.createAnySD());
    existing.setParentReaction(parent);
    when(stoichiometryManager.get(3L)).thenReturn(existing);

    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);

    Stoichiometry updated = new Stoichiometry();
    updated.setId(3L);
    when(stoichiometryManager.update(any(StoichiometryUpdateDTO.class), eq(user)))
        .thenReturn(updated);

    Stoichiometry result = service.update(3L, mock(StoichiometryUpdateDTO.class), user);
    assertEquals(updated, result);
  }

  @Test
  void delete_whenNoWritePermission_throwsAuthz() throws Exception {
    Stoichiometry existing = makeStoichiometryWithRecord(5L);
    when(stoichiometryManager.get(5L)).thenReturn(existing);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(false);

    assertThrows(AuthorizationException.class, () -> service.delete(5L, user));
  }

  @Test
  void delete_whenRemoveThrows_wrapsAsNotFound() throws Exception {
    Stoichiometry existing = makeStoichiometryWithRecord(5L);
    when(stoichiometryManager.get(5L)).thenReturn(existing);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);

    doThrow(new RuntimeException("boom")).when(stoichiometryManager).remove(5L);

    NotFoundException ex = assertThrows(NotFoundException.class, () -> service.delete(5L, user));
    assertTrue(ex.getMessage().contains("Error deleting stoichiometry with id 5"));
  }

  @Test
  void delete_whenOk_callsRemove() throws Exception {
    Stoichiometry existing = makeStoichiometryWithRecord(5L);
    when(stoichiometryManager.get(5L)).thenReturn(existing);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);

    assertDoesNotThrow(() -> service.delete(5L, user));
    verify(stoichiometryManager).remove(5L);
  }

  @Test
  void getMoleculeInfo_whenBlank_throwsNotFound() {
    assertThrows(StoichiometryException.class, () -> service.getMoleculeInfo(null));
    assertThrows(StoichiometryException.class, () -> service.getMoleculeInfo(" "));
  }

  @Test
  void getMoleculeInfo_whenInfoPresent_returnsMappedMolecule() {
    ElementalAnalysisDTO analysis = mock(ElementalAnalysisDTO.class);
    MoleculeInfoDTO mol = mock(MoleculeInfoDTO.class);
    when(mol.getRole()).thenReturn(com.researchspace.model.stoichiometry.MoleculeRole.REACTANT);
    when(mol.getSmiles()).thenReturn("C2H6O");
    when(mol.getMass()).thenReturn(46.07);
    when(mol.getFormula()).thenReturn("C2H6O");
    when(analysis.getMoleculeInfo()).thenReturn(java.util.Collections.singletonList(mol));
    when(rsChemElementManager.getInfo("CCO")).thenReturn(Optional.of(analysis));

    StoichiometryMolecule result = service.getMoleculeInfo("CCO");

    assertEquals("C2H6O", result.getSmiles());
    assertEquals("C2H6O", result.getFormula());
    assertEquals(46.07, result.getMolecularWeight());
    assertEquals(Boolean.FALSE, result.getLimitingReagent());
    assertEquals(com.researchspace.model.stoichiometry.MoleculeRole.REACTANT, result.getRole());
  }

  @Test
  void getMoleculeInfo_whenNoInfo_throwsNotFound() {
    when(rsChemElementManager.getInfo("CCO")).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.getMoleculeInfo("CCO"));
  }

  private Stoichiometry makeStoichiometryWithRecord(Long reactionId) throws Exception {
    RSChemElement parent = TestFactory.createChemElement(null, reactionId);
    parent.setRecord(TestFactory.createAnySD());
    Stoichiometry s = new Stoichiometry();
    s.setId(1L);
    s.setParentReaction(parent);
    return s;
  }
}

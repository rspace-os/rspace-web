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
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.StoichiometryManager;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.chemistry.StoichiometryException;
import com.researchspace.testutils.TestFactory;
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
  @Mock private RecordManager recordManager;

  @InjectMocks private StoichiometryServiceImpl service;

  private User user;

  @BeforeEach
  void setUp() {
    user = TestFactory.createAnyUser("user");
  }

  @Test
  void create_whenOwningRecordMissing_throwsNotFound() {
    when(chemistryService.getChemicalElementByRevision(2L, null, user))
        .thenReturn(mock(RSChemElement.class));
    when(recordManager.get(123L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> service.createFromReaction(123L, 2L, user));
  }

  @Test
  void create_whenNoWritePermission_throwsAuth() throws Exception {
    RSChemElement chem = TestFactory.createChemElement(null, 2L);
    Record record = TestFactory.createAnySD();
    chem.setRecord(record);

    when(chemistryService.getChemicalElementByRevision(2L, null, user)).thenReturn(chem);
    when(recordManager.get(123L)).thenReturn(record);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(false);

    assertThrows(AuthorizationException.class, () -> service.createFromReaction(123L, 2L, user));
  }

  @Test
  void create_whenExistingAlreadyPresent_throwsStoichException() throws Exception {
    RSChemElement chem = TestFactory.createChemElement(null, 2L);
    Record record = TestFactory.createAnySD();
    chem.setRecord(record);
    when(chemistryService.getChemicalElementByRevision(2L, null, user)).thenReturn(chem);
    when(recordManager.get(123L)).thenReturn(record);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);

    Stoichiometry existing = new Stoichiometry();
    existing.setId(99L);
    when(stoichiometryManager.findByParentReactionId(2L)).thenReturn(Optional.of(existing));

    StoichiometryException ex =
        assertThrows(
            StoichiometryException.class, () -> service.createFromReaction(123L, 2L, user));
    assertTrue(ex.getMessage().contains("Stoichiometry already exists for reaction chemId=2"));
  }

  @Test
  void create_whenNoAnalysisReturned_throwsStoichException() throws Exception {
    RSChemElement chem = TestFactory.createChemElement(null, 2L);
    Record record = TestFactory.createAnySD();
    chem.setRecord(record);
    when(chemistryService.getChemicalElementByRevision(2L, null, user)).thenReturn(chem);
    when(recordManager.get(123L)).thenReturn(record);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);
    when(stoichiometryManager.findByParentReactionId(2L)).thenReturn(Optional.empty());

    when(chemistryProvider.getStoichiometry(chem)).thenReturn(Optional.empty());

    StoichiometryException ex =
        assertThrows(
            StoichiometryException.class, () -> service.createFromReaction(123L, 2L, user));
    assertTrue(ex.getMessage().contains("Unable to generate stoichiometry"));
  }

  @Test
  void create_whenCreateFromAnalysisThrowsIOException_wrapsAsStoichException() throws Exception {
    RSChemElement chem = TestFactory.createChemElement(null, 2L);
    Record record = TestFactory.createAnySD();
    chem.setRecord(record);
    when(chemistryService.getChemicalElementByRevision(2L, null, user)).thenReturn(chem);
    when(recordManager.get(123L)).thenReturn(record);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);
    when(stoichiometryManager.findByParentReactionId(2L)).thenReturn(Optional.empty());

    ElementalAnalysisDTO analysis = mock(ElementalAnalysisDTO.class);
    when(chemistryProvider.getStoichiometry(chem)).thenReturn(Optional.of(analysis));
    when(stoichiometryManager.createFromAnalysis(eq(analysis), eq(chem), eq(record), eq(user)))
        .thenThrow(new IOException("IO fail"));

    StoichiometryException ex =
        assertThrows(
            StoichiometryException.class, () -> service.createFromReaction(123L, 2L, user));
    assertTrue(ex.getMessage().contains("Problem while creating new Stoichiometry: IO fail"));
  }

  @Test
  void create_whenOk_returnsNewStoichiometry() throws Exception {
    RSChemElement chem = TestFactory.createChemElement(null, 2L);
    Record record = TestFactory.createAnySD();
    chem.setRecord(record);
    when(chemistryService.getChemicalElementByRevision(2L, null, user)).thenReturn(chem);
    when(recordManager.get(123L)).thenReturn(record);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);
    when(stoichiometryManager.findByParentReactionId(2L)).thenReturn(Optional.empty());

    ElementalAnalysisDTO analysis = mock(ElementalAnalysisDTO.class);
    when(chemistryProvider.getStoichiometry(chem)).thenReturn(Optional.of(analysis));

    Stoichiometry created = new Stoichiometry();
    created.setId(7L);
    created.setParentReaction(chem);
    created.setRecord(record);
    when(stoichiometryManager.createFromAnalysis(eq(analysis), eq(chem), eq(record), eq(user)))
        .thenReturn(created);

    Stoichiometry result = service.createFromReaction(123L, 2L, user);
    assertEquals(7L, result.getId().longValue());
    assertEquals(chem, result.getParentReaction());
  }

  @Test
  void createEmpty_whenRecordMissing_throwsNotFound() {
    when(recordManager.get(123L)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> service.createEmpty(123L, user));
  }

  @Test
  void createEmpty_whenNoWritePermission_throwsAuthz() {
    Record record = TestFactory.createAnySD();
    when(recordManager.get(123L)).thenReturn(record);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(false);

    assertThrows(AuthorizationException.class, () -> service.createEmpty(123L, user));
  }

  @Test
  void createEmpty_whenOk_returnsNewStoichiometry() throws Exception {
    Record record = TestFactory.createAnySD();
    when(recordManager.get(123L)).thenReturn(record);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);
    lenient().when(stoichiometryManager.findByRecordId(123L)).thenReturn(Optional.empty());

    Stoichiometry emptyStoich = new Stoichiometry();
    emptyStoich.setId(456L);
    emptyStoich.setRecord(record);
    when(stoichiometryManager.createEmpty(eq(record), eq(user))).thenReturn(emptyStoich);

    Stoichiometry result = service.createEmpty(123L, user);

    assertNotNull(result);
    assertEquals(456L, result.getId());
    assertEquals(record, result.getRecord());
  }

  @Test
  void update_whenOwningRecordMissing_throwsNotFound() throws Exception {
    Stoichiometry existing = new Stoichiometry();
    existing.setId(3L);
    RSChemElement parent = TestFactory.createChemElement(null, 123L);
    existing.setParentReaction(parent);
    // existing.setRecord(null); // Explicitly null for test case
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
    Record record = TestFactory.createAnySD();
    existing.setRecord(record);
    RSChemElement parent = TestFactory.createChemElement(null, 123L);
    parent.setRecord(record);
    existing.setParentReaction(parent);
    when(stoichiometryManager.get(3L)).thenReturn(existing);

    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(false);

    assertThrows(
        AuthorizationException.class,
        () -> service.update(3L, mock(StoichiometryUpdateDTO.class), user));
  }

  @Test
  void update_whenDirectRecord_returnsUpdatedStoichiometry() throws Exception {
    Stoichiometry existing = makeStoichiometryDirectlyWithRecord(123L);
    when(stoichiometryManager.get(1L)).thenReturn(existing);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);

    Stoichiometry updated = new Stoichiometry();
    updated.setId(1L);
    when(stoichiometryManager.update(any(StoichiometryUpdateDTO.class), eq(user)))
        .thenReturn(updated);

    Stoichiometry result = service.update(1L, mock(StoichiometryUpdateDTO.class), user);
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
  void delete_whenRemoveFails_throwsStoich() throws Exception {
    Stoichiometry existing = makeStoichiometryWithRecord(5L);
    when(stoichiometryManager.get(5L)).thenReturn(existing);
    when(permissionUtils.isPermitted(any(), eq(PermissionType.WRITE), eq(user))).thenReturn(true);

    doThrow(new RuntimeException("problem removing")).when(stoichiometryManager).remove(5L);

    StoichiometryException ex =
        assertThrows(StoichiometryException.class, () -> service.delete(5L, user));
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
    Record record = TestFactory.createAnySD();
    parent.setRecord(record);
    Stoichiometry s = new Stoichiometry();
    s.setId(1L);
    s.setParentReaction(parent);
    s.setRecord(record);
    return s;
  }

  private Stoichiometry makeStoichiometryDirectlyWithRecord(Long recordId) throws Exception {
    Record record = TestFactory.createAnySD();
    record.setId(recordId);
    Stoichiometry s = new Stoichiometry();
    s.setId(1L);
    s.setRecord(record);
    return s;
  }
}

package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.stoichiometry.StockDeductionResult;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.dao.StoichiometryInventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.StoichiometryMoleculeManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.SubSampleApiManager;
import java.math.BigDecimal;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StoichiometryInventoryLinkManagerImplTest {

  @Mock private StoichiometryInventoryLinkDao linkDao;
  @Mock private StoichiometryMoleculeManager moleculeManager;
  @Mock private IPermissionUtils elnPerms;
  @Mock private InventoryPermissionUtils invPerms;
  @Mock private SubSampleApiManager subSampleMgr;

  @InjectMocks private StoichiometryInventoryLinkManagerImpl manager;

  private User user;
  private StoichiometryMolecule molecule;
  private Sample invSample;
  private SubSample invSubSample;
  private StructuredDocument owningRecord;

  @Before
  public void setUp() {
    user = new User();
    user.setUsername("u1");
    molecule = new StoichiometryMolecule();
    molecule.setId(10L);
    molecule.setStoichiometry(new Stoichiometry());
    invSample = new Sample();
    invSample.setId(200L);
    invSubSample = new SubSample();
    invSubSample.setId(300L);
    owningRecord = mock(StructuredDocument.class);
  }

  @Test
  public void createLinkSuccess() {
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setInventoryItemGlobalId("SA200");

    when(moleculeManager.getById(10L)).thenReturn(molecule);
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    when(invPerms.assertUserCanEditInventoryRecord(any(GlobalIdentifier.class), eq(user)))
        .thenReturn(invSample);

    when(linkDao.save(any(StoichiometryInventoryLink.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    StoichiometryInventoryLink dto = manager.createLink(10L, req, user);
    assertEquals(Long.valueOf(10L), dto.getStoichiometryMolecule().getId());
    assertEquals("SA200", dto.getInventoryRecord().getOid().getIdString());
  }

  @Test
  public void createLinkWhenMoleculeAlreadyHasLinkThrows() {
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setInventoryItemGlobalId("SA200");

    // molecule already linked
    molecule.setInventoryLink(new StoichiometryInventoryLink());

    when(moleculeManager.getById(10L)).thenReturn(molecule);
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> manager.createLink(10L, req, user));
    assertEquals("Stoichiometry molecule already has an inventory link", ex.getMessage());
  }

  @Test
  public void createLinkWhenInventoryRecordIsSampleTemplateThrows() {
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setInventoryItemGlobalId("IT200");

    invSample.setTemplate(true);

    when(moleculeManager.getById(10L)).thenReturn(molecule);
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    when(invPerms.assertUserCanEditInventoryRecord(any(GlobalIdentifier.class), eq(user)))
        .thenReturn(invSample);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> manager.createLink(10L, req, user));
    assertEquals(
        "IT200 is a sample template. Only Containers, Samples and Subsamples are valid for"
            + " linking.",
        ex.getMessage());
  }

  @Test
  public void createLinkDeniedOnElnPermissions() {
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setInventoryItemGlobalId("SA200");

    when(moleculeManager.getById(10L)).thenReturn(molecule);
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(false);

    assertThrows(NotFoundException.class, () -> manager.createLink(10L, req, user));
  }

  @Test
  public void getByIdDeniedOnInventoryPermissions() {
    StoichiometryInventoryLink existing = new StoichiometryInventoryLink();
    existing.setId(123L);
    existing.setStoichiometryMolecule(molecule);
    existing.setInventoryRecord(invSample);

    when(linkDao.getSafeNull(123L)).thenReturn(java.util.Optional.of(existing));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.READ, user)).thenReturn(true);
    doThrow(new NotFoundException())
        .when(invPerms)
        .assertUserCanEditInventoryRecord(invSample, user);

    assertThrows(NotFoundException.class, () -> manager.getById(123L, user));
  }

  @Test
  public void deleteSuccess() {
    StoichiometryInventoryLink existing = new StoichiometryInventoryLink();
    existing.setId(123L);
    existing.setStoichiometryMolecule(molecule);
    existing.setSample(invSample);
    when(linkDao.getSafeNull(123L)).thenReturn(java.util.Optional.of(existing));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);

    doNothing().when(invPerms).assertUserCanEditInventoryRecord(invSample, user);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    manager.deleteLink(existing.getId(), user);
    verify(linkDao).remove(123L);
  }

  @Test
  public void deleteDeniedOnInventoryPermissions() {
    StoichiometryInventoryLink existing = new StoichiometryInventoryLink();
    existing.setId(123L);
    existing.setStoichiometryMolecule(molecule);
    existing.setSample(invSample);
    when(linkDao.getSafeNull(123L)).thenReturn(java.util.Optional.of(existing));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    doThrow(new NotFoundException())
        .when(invPerms)
        .assertUserCanEditInventoryRecord(invSample, user);
    assertThrows(NotFoundException.class, () -> manager.deleteLink(existing.getId(), user));
  }

  @Test
  public void createLinkNotReducingStockDoesntRegisterUsage() {
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setInventoryItemGlobalId("SS300");

    when(moleculeManager.getById(10L)).thenReturn(molecule);
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    when(invPerms.assertUserCanEditInventoryRecord(any(GlobalIdentifier.class), eq(user)))
        .thenReturn(invSubSample);
    when(linkDao.save(any(StoichiometryInventoryLink.class))).thenAnswer(inv -> inv.getArgument(0));

    manager.createLink(10L, req, user);
    verify(subSampleMgr, never())
        .registerApiSubSampleUsage(any(Long.class), any(QuantityInfo.class), eq(user));
  }

  @Test
  public void deleteDeniedOnElnPermissions() {
    StoichiometryInventoryLink existing = new StoichiometryInventoryLink();
    existing.setId(123L);
    existing.setStoichiometryMolecule(molecule);
    existing.setInventoryRecord(invSample);
    when(linkDao.getSafeNull(123L)).thenReturn(java.util.Optional.of(existing));
    assertThrows(NotFoundException.class, () -> manager.deleteLink(existing.getId(), user));
  }

  @Test
  public void getByIdMissingEntityThrowsNotFound() {
    when(linkDao.getSafeNull(999L)).thenReturn(java.util.Optional.empty());
    assertThrows(NotFoundException.class, () -> manager.getById(999L, user));
  }

  @Test
  public void deductStockSuccess() {
    StoichiometryInventoryLink original = new StoichiometryInventoryLink();
    original.setId(321L);
    molecule.setActualAmount(10.0);
    original.setStoichiometryMolecule(molecule);
    original.setInventoryRecord(invSubSample);

    invSubSample.setQuantity(new QuantityInfo(BigDecimal.valueOf(100), RSUnitDef.GRAM.getId()));

    when(linkDao.getSafeNull(321L)).thenReturn(java.util.Optional.of(original));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    doNothing()
        .when(invPerms)
        .assertUserCanEditInventoryRecord(original.getInventoryRecord(), user);

    StockDeductionResult result = manager.deductStock(List.of(321L), user);

    assertEquals(1, result.getResults().size());
    assertTrue(result.getResults().get(0).isSuccess());
    assertTrue(original.isStockDeducted());
    verify(linkDao).save(original);
  }

  @Test
  public void deductStockWithInsufficientStockReturnsErrorResult() {
    StoichiometryInventoryLink original = new StoichiometryInventoryLink();
    original.setId(321L);
    molecule.setActualAmount(20.0);
    original.setStoichiometryMolecule(molecule);
    original.setInventoryRecord(invSubSample);

    // SubSample has only 5 g stock
    invSubSample.setQuantity(new QuantityInfo(BigDecimal.valueOf(5), RSUnitDef.GRAM.getId()));

    when(linkDao.getSafeNull(321L)).thenReturn(java.util.Optional.of(original));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    doNothing()
        .when(invPerms)
        .assertUserCanEditInventoryRecord(original.getInventoryRecord(), user);

    StockDeductionResult result = manager.deductStock(List.of(321L), user);

    assertEquals(1, result.getResults().size());
    assertTrue(!result.getResults().get(0).isSuccess());
    assertEquals(
        "Insufficient stock to perform this action. Attempting to use 20 g of stock amount 5 g"
            + " for SS300",
        result.getResults().get(0).getErrorMessage());
  }

  private StoichiometryInventoryLink createMoleculeAndLink(
      Long linkId, Long subSampleId, StoichiometryMolecule mol) {
    SubSample sub = new SubSample();
    sub.setId(subSampleId);
    StoichiometryInventoryLink link = new StoichiometryInventoryLink();
    link.setId(linkId);
    link.setStoichiometryMolecule(mol);
    link.setInventoryRecord(sub);
    return link;
  }

  @Test
  public void deductStockWithExceptionErrorMessagesTest() {
    // NotFoundException should return exception message in result
    StoichiometryMolecule mol1 = mock(StoichiometryMolecule.class);
    StoichiometryInventoryLink link1 = createMoleculeAndLink(101L, 1001L, mol1);
    InventoryRecord ss1 = link1.getInventoryRecord();
    when(linkDao.getSafeNull(101L)).thenReturn(java.util.Optional.of(link1));
    when(moleculeManager.getDocContainingMolecule(mol1)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    doThrow(new NotFoundException("Molecule 1 Not Found"))
        .when(invPerms)
        .assertUserCanEditInventoryRecord(ss1, user);

    // IllegalArgumentException should return exception message in result
    StoichiometryMolecule mol2 = mock(StoichiometryMolecule.class);
    StoichiometryInventoryLink link2 = createMoleculeAndLink(102L, 1002L, mol2);
    InventoryRecord ss2 = link2.getInventoryRecord();
    when(linkDao.getSafeNull(102L)).thenReturn(java.util.Optional.of(link2));
    when(moleculeManager.getDocContainingMolecule(mol2)).thenReturn(owningRecord);
    doThrow(new IllegalArgumentException("Molecule 2 Insufficient Stock"))
        .when(invPerms)
        .assertUserCanEditInventoryRecord(ss2, user);

    // Other exceptions should return generic error message in result
    StoichiometryMolecule mol3 = mock(StoichiometryMolecule.class);
    StoichiometryInventoryLink link3 = createMoleculeAndLink(103L, 1003L, mol3);
    InventoryRecord ss3 = link3.getInventoryRecord();
    when(linkDao.getSafeNull(103L)).thenReturn(java.util.Optional.of(link3));
    when(moleculeManager.getDocContainingMolecule(mol3)).thenReturn(owningRecord);
    doThrow(new RuntimeException("Internal error not returned to user"))
        .when(invPerms)
        .assertUserCanEditInventoryRecord(ss3, user);

    StockDeductionResult result = manager.deductStock(List.of(101L, 102L, 103L), user);

    assertEquals(3, result.getResults().size());

    assertEquals(Long.valueOf(101L), result.getResults().get(0).getLinkId());
    assertEquals("Molecule 1 Not Found", result.getResults().get(0).getErrorMessage());

    assertEquals(Long.valueOf(102L), result.getResults().get(1).getLinkId());
    assertEquals("Molecule 2 Insufficient Stock", result.getResults().get(1).getErrorMessage());

    assertEquals(Long.valueOf(103L), result.getResults().get(2).getLinkId());
    assertEquals(
        "An internal error occurred while deducting stock",
        result.getResults().get(2).getErrorMessage());
  }
}

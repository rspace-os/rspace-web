package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    assertFalse(result.getResults().get(0).isSuccess());
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
  public void deductStockWithNotFoundExceptionReturnsErrorMessage() {
    StoichiometryMolecule mol = new StoichiometryMolecule();
    StoichiometryInventoryLink link = createMoleculeAndLink(101L, 1001L, mol);
    InventoryRecord ss = link.getInventoryRecord();
    when(linkDao.getSafeNull(101L)).thenReturn(java.util.Optional.of(link));
    when(moleculeManager.getDocContainingMolecule(mol)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    doThrow(new NotFoundException("Molecule 1 Not Found"))
        .when(invPerms)
        .assertUserCanEditInventoryRecord(ss, user);

    StockDeductionResult result = manager.deductStock(List.of(101L), user);

    assertEquals(1, result.getResults().size());
    assertEquals(Long.valueOf(101L), result.getResults().get(0).getLinkId());
    assertEquals("Molecule 1 Not Found", result.getResults().get(0).getErrorMessage());
    assertFalse(result.getResults().get(0).isSuccess());
    verify(linkDao, never()).save(any());
  }

  @Test
  public void deductStockWithIllegalArgumentExceptionReturnsErrorMessage() {
    StoichiometryMolecule mol = new StoichiometryMolecule();
    StoichiometryInventoryLink link = createMoleculeAndLink(102L, 1002L, mol);
    InventoryRecord ss = link.getInventoryRecord();
    when(linkDao.getSafeNull(102L)).thenReturn(java.util.Optional.of(link));
    when(moleculeManager.getDocContainingMolecule(mol)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    doThrow(new IllegalArgumentException("Molecule 2 Insufficient Stock"))
        .when(invPerms)
        .assertUserCanEditInventoryRecord(ss, user);

    StockDeductionResult result = manager.deductStock(List.of(102L), user);

    assertEquals(1, result.getResults().size());
    assertEquals(Long.valueOf(102L), result.getResults().get(0).getLinkId());
    assertEquals("Molecule 2 Insufficient Stock", result.getResults().get(0).getErrorMessage());
    assertFalse(result.getResults().get(0).isSuccess());
    verify(linkDao, never()).save(any());
  }

  @Test
  public void deductStockWithUnexpectedExceptionReturnsGenericErrorMessage() {
    StoichiometryMolecule mol = new StoichiometryMolecule();
    StoichiometryInventoryLink link = createMoleculeAndLink(103L, 1003L, mol);
    InventoryRecord ss = link.getInventoryRecord();
    when(linkDao.getSafeNull(103L)).thenReturn(java.util.Optional.of(link));
    when(moleculeManager.getDocContainingMolecule(mol)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    doThrow(new RuntimeException("Internal error not returned to user"))
        .when(invPerms)
        .assertUserCanEditInventoryRecord(ss, user);

    StockDeductionResult result = manager.deductStock(List.of(103L), user);

    assertEquals(1, result.getResults().size());
    assertEquals(Long.valueOf(103L), result.getResults().get(0).getLinkId());
    assertEquals(
        "An internal error occurred while deducting stock",
        result.getResults().get(0).getErrorMessage());
    assertFalse(result.getResults().get(0).isSuccess());
    verify(linkDao, never()).save(any());
  }
}

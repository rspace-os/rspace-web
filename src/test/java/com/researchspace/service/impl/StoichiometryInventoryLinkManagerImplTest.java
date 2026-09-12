package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.calls;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.stoichiometry.StockDeductionResult;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.dao.StoichiometryInventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.StoichiometryMoleculeManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.SampleSiblingRowLock;
import com.researchspace.service.inventory.SubSampleApiManager;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
public class StoichiometryInventoryLinkManagerImplTest {

  @Mock private StoichiometryInventoryLinkDao linkDao;
  @Mock private StoichiometryMoleculeManager moleculeManager;
  @Mock private IPermissionUtils elnPerms;
  @Mock private InventoryPermissionUtils invPerms;
  @Mock private SubSampleApiManager subSampleMgr;
  @Mock private SampleSiblingRowLock siblingRowLock;

  private StoichiometryInventoryLinkManagerImpl manager;

  private User user;
  private StoichiometryMolecule molecule;
  private Sample invSample;
  private SubSample invSubSample;
  private StructuredDocument owningRecord;

  @BeforeEach
  public void setUp() {
    manager =
        new StoichiometryInventoryLinkManagerImpl(
            linkDao,
            moleculeManager,
            elnPerms,
            invPerms,
            subSampleMgr,
            siblingRowLock,
            new MessageSourceUtils(new JsonMessageSource()));
    user = new User();
    user.setUsername("u1");
    molecule = new StoichiometryMolecule();
    molecule.setId(10L);
    molecule.setStoichiometry(new Stoichiometry());
    invSample = new Sample();
    invSample.setId(200L);
    invSubSample = new SubSample();
    invSubSample.setId(300L);
    // the deduction locks the parent sample's sibling set before the subsample's own row, so
    // every subsample a deduction touches needs a parent to resolve
    invSubSample.setSample(invSample);
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

    assertEquals(
        "Stoichiometry molecule already has an inventory link",
        assertThrows(IllegalArgumentException.class, () -> manager.createLink(10L, req, user))
            .getMessage());
  }

  @Test
  public void createLinkWhenInventoryRecordIsSampleTemplateThrows() {
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setInventoryItemGlobalId("IT200");

    SampleTemplate invSampleTemplate = new SampleTemplate();
    invSampleTemplate.setId(200L);

    when(moleculeManager.getById(10L)).thenReturn(molecule);
    when(invPerms.assertUserCanEditInventoryRecord(any(GlobalIdentifier.class), eq(user)))
        .thenReturn(invSampleTemplate);

    assertEquals(
        "IT200 is a sample template. Only Containers, Samples and Subsamples are valid for"
            + " linking.",
        assertThrows(IllegalArgumentException.class, () -> manager.createLink(10L, req, user))
            .getMessage());
  }

  @Test
  public void deductStockWhenLinkBelongsToDifferentStoichiometryReturnsErrorResult() {
    long requestedStoichiometryId = 55L;
    long actualStoichiometryId = 99L;

    StoichiometryMolecule mol = new StoichiometryMolecule();
    Stoichiometry otherStoichiometry = new Stoichiometry();
    otherStoichiometry.setId(actualStoichiometryId);
    mol.setStoichiometry(otherStoichiometry);
    mol.setActualAmount(10.0);

    StoichiometryInventoryLink link = createMoleculeAndLink(321L, 300L, mol);

    when(linkDao.getSafeNull(321L)).thenReturn(java.util.Optional.of(link));

    StockDeductionResult result =
        manager.deductStock(requestedStoichiometryId, List.of(321L), user);

    assertEquals(1, result.getResults().size());
    assertFalse(result.getResults().get(0).isSuccess());
    assertEquals(
        String.format(
            "Link with id %d does not belong to stoichiometry with id %d",
            321L, requestedStoichiometryId),
        result.getResults().get(0).getErrorMessage());
    verify(linkDao, never()).save(any());
  }

  @Test
  public void deductStockSuccess() {
    StoichiometryInventoryLink original = new StoichiometryInventoryLink();
    original.setId(321L);
    long stoichiometryId = 55L;
    molecule.getStoichiometry().setId(stoichiometryId);
    molecule.setActualAmount(10.0);
    original.setStoichiometryMolecule(molecule);
    original.setInventoryRecord(invSubSample);

    invSubSample.setQuantity(new QuantityInfo(BigDecimal.valueOf(100), RSUnitDef.GRAM.getId()));

    when(linkDao.getSafeNull(321L)).thenReturn(java.util.Optional.of(original));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    when(subSampleMgr.lockSubSampleForEdit(invSubSample.getId(), user)).thenReturn(invSubSample);
    when(subSampleMgr.getQuantityForUpdate(invSubSample.getId()))
        .thenReturn(invSubSample.getQuantity());

    StockDeductionResult result = manager.deductStock(stoichiometryId, List.of(321L), user);

    assertEquals(1, result.getResults().size());
    assertTrue(result.getResults().get(0).isSuccess());
    assertEquals(Long.valueOf(stoichiometryId), result.getStoichiometryId());
    assertTrue(original.isStockDeducted());
    verify(linkDao).save(original);
  }

  @Test
  public void eachSubmittedLinkIdIsResolvedExactlyOnce() {
    // Three passes need the link: the sibling-set lock, the lock ordering and the deduction loop.
    // Each used to load it again, so a repeated id was loaded six times and lockOrderKey resolved
    // arbitrary ids on its own, twenty lines below the filtering written to stop that (parallel
    // review, S4). One load per DISTINCT id, whatever the submitted cardinality.
    StoichiometryInventoryLink original = new StoichiometryInventoryLink();
    original.setId(321L);
    long stoichiometryId = 55L;
    molecule.getStoichiometry().setId(stoichiometryId);
    molecule.setActualAmount(10.0);
    original.setStoichiometryMolecule(molecule);
    original.setInventoryRecord(invSubSample);

    invSubSample.setQuantity(new QuantityInfo(BigDecimal.valueOf(100), RSUnitDef.GRAM.getId()));

    when(linkDao.getSafeNull(321L)).thenReturn(java.util.Optional.of(original));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    when(subSampleMgr.lockSubSampleForEdit(invSubSample.getId(), user)).thenReturn(invSubSample);
    when(subSampleMgr.getQuantityForUpdate(invSubSample.getId()))
        .thenReturn(invSubSample.getQuantity());

    manager.deductStock(stoichiometryId, List.of(321L, 321L), user);

    verify(linkDao, times(1)).getSafeNull(321L);
  }

  @Test
  public void repeatedLinkIdIsDeductedOnce() {
    StoichiometryInventoryLink original = new StoichiometryInventoryLink();
    original.setId(321L);
    long stoichiometryId = 55L;
    molecule.getStoichiometry().setId(stoichiometryId);
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
    when(subSampleMgr.lockSubSampleForEdit(invSubSample.getId(), user)).thenReturn(invSubSample);
    when(subSampleMgr.getQuantityForUpdate(invSubSample.getId()))
        .thenReturn(invSubSample.getQuantity());

    StockDeductionResult result =
        manager.deductStock(stoichiometryId, List.of(321L, 321L, 321L), user);

    // the repeat is dropped before any stock is touched, so the amount comes off once (RSDEV-1319)
    verify(subSampleMgr)
        .registerApiSubSampleUsage(eq(invSubSample.getId()), any(QuantityInfo.class), eq(user));
    // but the public API contract is one result row per submitted entry, so the response
    // cardinality is unchanged: three rows, all reporting the single deduction's outcome
    assertEquals(3, result.getResults().size());
    result
        .getResults()
        .forEach(
            row -> {
              assertEquals(321L, row.getLinkId().longValue());
              assertTrue(row.isSuccess());
            });
  }

  @Test
  public void deductStockWithInsufficientStockReturnsErrorResult() {
    StoichiometryInventoryLink original = new StoichiometryInventoryLink();
    original.setId(321L);
    long stoichiometryId = 1000L;
    molecule.getStoichiometry().setId(stoichiometryId);
    molecule.setActualAmount(20.0);
    original.setStoichiometryMolecule(molecule);
    original.setInventoryRecord(invSubSample);

    // SubSample has only 5 g stock
    invSubSample.setQuantity(new QuantityInfo(BigDecimal.valueOf(5), RSUnitDef.GRAM.getId()));

    when(linkDao.getSafeNull(321L)).thenReturn(java.util.Optional.of(original));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    when(subSampleMgr.lockSubSampleForEdit(invSubSample.getId(), user)).thenReturn(invSubSample);
    when(subSampleMgr.getQuantityForUpdate(invSubSample.getId()))
        .thenReturn(invSubSample.getQuantity());

    StockDeductionResult result = manager.deductStock(stoichiometryId, List.of(321L), user);

    assertEquals(1, result.getResults().size());
    assertFalse(result.getResults().get(0).isSuccess());
    assertEquals(
        "Insufficient stock to perform this action. Attempting to use 20 g of stock amount 5 g"
            + " for SS300",
        result.getResults().get(0).getErrorMessage());
  }

  @Test
  public void insufficientStockIsJudgedFromTheLockedScalarNotTheEntity() {
    // A concurrent operation may have drained the subsample since any entity copy of it was
    // loaded, and the locked entity itself holds this transaction's snapshot (lockRowForUpdate
    // serialises, it does not refresh). The over-use check therefore reads the value as a scalar
    // under the lock; judging from either entity here (both report plenty) would accept a
    // deduction that registerApiSubSampleUsage then silently clamps at zero.
    StoichiometryInventoryLink original = new StoichiometryInventoryLink();
    original.setId(321L);
    long stoichiometryId = 55L;
    molecule.getStoichiometry().setId(stoichiometryId);
    molecule.setActualAmount(20.0);
    original.setStoichiometryMolecule(molecule);
    original.setInventoryRecord(invSubSample);
    // the entity snapshot still reports plenty; the committed row holds only 5 g
    invSubSample.setQuantity(new QuantityInfo(BigDecimal.valueOf(100), RSUnitDef.GRAM.getId()));

    when(linkDao.getSafeNull(321L)).thenReturn(java.util.Optional.of(original));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    when(subSampleMgr.lockSubSampleForEdit(invSubSample.getId(), user)).thenReturn(invSubSample);
    when(subSampleMgr.getQuantityForUpdate(invSubSample.getId()))
        .thenReturn(new QuantityInfo(BigDecimal.valueOf(5), RSUnitDef.GRAM.getId()));

    StockDeductionResult result = manager.deductStock(stoichiometryId, List.of(321L), user);

    assertFalse(result.getResults().get(0).isSuccess());
    assertEquals(
        "Insufficient stock to perform this action. Attempting to use 20 g of stock amount 5 g"
            + " for SS300",
        result.getResults().get(0).getErrorMessage());
    verify(subSampleMgr, never())
        .registerApiSubSampleUsage(any(), any(QuantityInfo.class), any(User.class));
  }

  @Test
  public void locksSubSamplesInIdOrderWhateverOrderTheLinksWereSubmittedIn() {
    // Two deductions over the same subsamples in opposite request orders would each hold one row
    // and wait for the other. Locking in id order means every caller takes them in the same
    // sequence, so one simply waits for the other (DevDocs/adr/0007).
    StoichiometryMolecule mol = new StoichiometryMolecule();
    mol.setStoichiometry(new Stoichiometry());
    long stoichiometryId = 55L;
    mol.getStoichiometry().setId(stoichiometryId);
    mol.setActualAmount(1.0);
    StoichiometryInventoryLink higher = createMoleculeAndLink(500L, 900L, mol);
    StoichiometryInventoryLink lower = createMoleculeAndLink(501L, 800L, mol);

    when(linkDao.getSafeNull(500L)).thenReturn(java.util.Optional.of(higher));
    when(linkDao.getSafeNull(501L)).thenReturn(java.util.Optional.of(lower));
    when(moleculeManager.getDocContainingMolecule(mol)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    when(invPerms.canUserEditInventoryRecord(any(SubSample.class), eq(user))).thenReturn(true);
    when(subSampleMgr.lockSubSampleForEdit(900L, user)).thenReturn(stocked(900L));
    when(subSampleMgr.lockSubSampleForEdit(800L, user)).thenReturn(stocked(800L));
    when(subSampleMgr.getQuantityForUpdate(900L)).thenReturn(stocked(900L).getQuantity());
    when(subSampleMgr.getQuantityForUpdate(800L)).thenReturn(stocked(800L).getQuantity());

    // submitted highest-subsample-first
    manager.deductStock(stoichiometryId, List.of(500L, 501L), user);

    InOrder inOrder = inOrder(subSampleMgr);
    inOrder.verify(subSampleMgr).lockSubSampleForEdit(800L, user);
    inOrder.verify(subSampleMgr).lockSubSampleForEdit(900L, user);
  }

  @Test
  public void locksTheParentSampleSetBeforeTheOriginRow() {
    // The canonical order every stock writer uses is sibling set first, row second. Taking the row
    // first (as the over-use check used to) deadlocks two deductions on two siblings of one
    // sample: each holds its own row while asking registerApiSubSampleUsage for the set that
    // contains the other's.
    StoichiometryInventoryLink original = new StoichiometryInventoryLink();
    original.setId(321L);
    long stoichiometryId = 55L;
    molecule.getStoichiometry().setId(stoichiometryId);
    molecule.setActualAmount(1.0);
    original.setStoichiometryMolecule(molecule);
    original.setInventoryRecord(invSubSample);
    invSubSample.setQuantity(new QuantityInfo(BigDecimal.valueOf(100), RSUnitDef.GRAM.getId()));

    when(linkDao.getSafeNull(321L)).thenReturn(java.util.Optional.of(original));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    when(invPerms.canUserEditInventoryRecord(any(SubSample.class), eq(user))).thenReturn(true);
    when(subSampleMgr.lockSubSampleForEdit(invSubSample.getId(), user)).thenReturn(invSubSample);
    when(subSampleMgr.getQuantityForUpdate(invSubSample.getId()))
        .thenReturn(invSubSample.getQuantity());

    manager.deductStock(stoichiometryId, List.of(321L), user);

    InOrder inOrder = inOrder(siblingRowLock, subSampleMgr);
    // calls(1): the set is deliberately asked for twice (the up-front hoist and the per-link
    // re-ask); what matters is that the first ask precedes the row lock
    inOrder.verify(siblingRowLock, calls(1)).lockSiblingRowsAndRecalculateTotal(invSample.getId());
    inOrder.verify(subSampleMgr).lockSubSampleForEdit(invSubSample.getId(), user);
  }

  @Test
  public void locksParentSampleSetsAscendingBeforeAnyRowLock() {
    // A deduction spanning several samples must acquire every parent's sibling set up front,
    // ascending by sample id, before its first row lock: acquiring each set only as the loop
    // reaches its link would order the sets by link position, which can invert against another
    // writer's ascending order and deadlock. Submitted highest-sample-first to prove the sort.
    StoichiometryMolecule mol = new StoichiometryMolecule();
    mol.setStoichiometry(new Stoichiometry());
    long stoichiometryId = 55L;
    mol.getStoichiometry().setId(stoichiometryId);
    mol.setActualAmount(1.0);
    // subsample 900 under sample 9000, subsample 800 under sample 8000
    StoichiometryInventoryLink higher = createMoleculeAndLink(500L, 900L, mol);
    StoichiometryInventoryLink lower = createMoleculeAndLink(501L, 800L, mol);

    when(linkDao.getSafeNull(500L)).thenReturn(java.util.Optional.of(higher));
    when(linkDao.getSafeNull(501L)).thenReturn(java.util.Optional.of(lower));
    when(moleculeManager.getDocContainingMolecule(mol)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    when(invPerms.canUserEditInventoryRecord(any(SubSample.class), eq(user))).thenReturn(true);
    when(subSampleMgr.lockSubSampleForEdit(900L, user)).thenReturn(stocked(900L));
    when(subSampleMgr.lockSubSampleForEdit(800L, user)).thenReturn(stocked(800L));
    when(subSampleMgr.getQuantityForUpdate(900L)).thenReturn(stocked(900L).getQuantity());
    when(subSampleMgr.getQuantityForUpdate(800L)).thenReturn(stocked(800L).getQuantity());

    manager.deductStock(stoichiometryId, List.of(500L, 501L), user);

    InOrder inOrder = inOrder(siblingRowLock, subSampleMgr);
    // calls(1): each set is re-asked per link later; the assertion is that BOTH sets are taken,
    // ascending, before the first row lock
    inOrder.verify(siblingRowLock, calls(1)).lockSiblingRowsAndRecalculateTotal(8000L);
    inOrder.verify(siblingRowLock, calls(1)).lockSiblingRowsAndRecalculateTotal(9000L);
    inOrder.verify(subSampleMgr).lockSubSampleForEdit(800L, user);
  }

  @Test
  public void aForeignOrUneditableLinkLocksNoSiblingSetUpFront() {
    // The hoist resolves ids straight off a public request: a link from another stoichiometry, or
    // one whose record the caller cannot edit, must not lock its sibling set, or a caller could
    // name unrelated link ids solely to delay authorized writers until the request fails
    // (Copilot review, PR #1090). Each such link still fails per-row with its specific reason.
    StoichiometryMolecule foreignMol = new StoichiometryMolecule();
    Stoichiometry otherStoichiometry = new Stoichiometry();
    otherStoichiometry.setId(99L);
    foreignMol.setStoichiometry(otherStoichiometry);
    foreignMol.setActualAmount(1.0);
    StoichiometryInventoryLink foreign = createMoleculeAndLink(500L, 900L, foreignMol);

    long stoichiometryId = 55L;
    molecule.getStoichiometry().setId(stoichiometryId);
    molecule.setActualAmount(1.0);
    StoichiometryInventoryLink uneditable = createMoleculeAndLink(501L, 800L, molecule);

    when(linkDao.getSafeNull(500L)).thenReturn(java.util.Optional.of(foreign));
    when(linkDao.getSafeNull(501L)).thenReturn(java.util.Optional.of(uneditable));
    when(invPerms.canUserEditInventoryRecord(any(SubSample.class), eq(user))).thenReturn(false);

    StockDeductionResult result = manager.deductStock(stoichiometryId, List.of(500L, 501L), user);

    verify(siblingRowLock, never()).lockSiblingRowsAndRecalculateTotal(any());
    assertEquals(2, result.getResults().size());
    result.getResults().forEach(row -> assertFalse(row.isSuccess()));
  }

  @Test
  public void aLockFailureAbortsTheWholeDeductionRatherThanBecomingOneFailedRow() {
    // The row locks this method now takes can fail (deadlock loser, lock-wait timeout). Hibernate
    // leaves the session unusable and the transaction rollback-only after one, so catching it per
    // link would run the remaining links on a poisoned session and end in an
    // UnexpectedRollbackException that loses the per-row results entirely. It escapes instead, and
    // the tier maps it to the 409 the Stoichiometry retry UI already handles.
    StoichiometryInventoryLink original = new StoichiometryInventoryLink();
    original.setId(321L);
    long stoichiometryId = 55L;
    molecule.getStoichiometry().setId(stoichiometryId);
    molecule.setActualAmount(1.0);
    original.setStoichiometryMolecule(molecule);
    original.setInventoryRecord(invSubSample);
    invSubSample.setQuantity(new QuantityInfo(BigDecimal.valueOf(100), RSUnitDef.GRAM.getId()));

    when(linkDao.getSafeNull(321L)).thenReturn(java.util.Optional.of(original));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    when(subSampleMgr.lockSubSampleForEdit(invSubSample.getId(), user))
        .thenThrow(new CannotAcquireLockException("deadlock loser"));

    assertThrows(
        CannotAcquireLockException.class,
        () -> manager.deductStock(stoichiometryId, List.of(321L), user));
  }

  /** A subsample holding plenty, so a deduction against it succeeds. */
  private SubSample stocked(Long id) {
    SubSample subSample = new SubSample();
    subSample.setId(id);
    subSample.setQuantity(new QuantityInfo(BigDecimal.valueOf(100), RSUnitDef.GRAM.getId()));
    return subSample;
  }

  private StoichiometryInventoryLink createMoleculeAndLink(
      Long linkId, Long subSampleId, StoichiometryMolecule mol) {
    SubSample sub = new SubSample();
    sub.setId(subSampleId);
    // parent sample id derived from the subsample id (800 -> 8000) so ordering tests can name it
    Sample parent = new Sample();
    parent.setId(subSampleId * 10);
    sub.setSample(parent);
    StoichiometryInventoryLink link = new StoichiometryInventoryLink();
    link.setId(linkId);
    link.setStoichiometryMolecule(mol);
    link.setInventoryRecord(sub);
    return link;
  }

  @Test
  public void deductStockWithNotFoundExceptionReturnsErrorMessage() {
    StoichiometryMolecule mol = new StoichiometryMolecule();
    mol.setStoichiometry(new Stoichiometry());
    long stoichiometryId = 55L;
    mol.getStoichiometry().setId(stoichiometryId);
    StoichiometryInventoryLink link = createMoleculeAndLink(101L, 1001L, mol);
    InventoryRecord ss = link.getInventoryRecord();
    when(linkDao.getSafeNull(101L)).thenReturn(java.util.Optional.of(link));
    when(moleculeManager.getDocContainingMolecule(mol)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    doThrow(new NotFoundException("Molecule 1 Not Found"))
        .when(invPerms)
        .assertUserCanEditInventoryRecord(ss, user);

    StockDeductionResult result = manager.deductStock(stoichiometryId, List.of(101L), user);

    assertEquals(1, result.getResults().size());
    assertEquals(Long.valueOf(101L), result.getResults().get(0).getLinkId());
    assertEquals("Molecule 1 Not Found", result.getResults().get(0).getErrorMessage());
    assertFalse(result.getResults().get(0).isSuccess());
    verify(linkDao, never()).save(any());
  }

  @Test
  public void deductStockWithIllegalArgumentExceptionReturnsErrorMessage() {
    StoichiometryMolecule mol = new StoichiometryMolecule();
    mol.setStoichiometry(new Stoichiometry());
    long stoichiometryId = 55L;
    mol.getStoichiometry().setId(stoichiometryId);
    StoichiometryInventoryLink link = createMoleculeAndLink(102L, 1002L, mol);
    InventoryRecord ss = link.getInventoryRecord();
    when(linkDao.getSafeNull(102L)).thenReturn(java.util.Optional.of(link));
    when(moleculeManager.getDocContainingMolecule(mol)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    doThrow(new IllegalArgumentException("Molecule 2 Insufficient Stock"))
        .when(invPerms)
        .assertUserCanEditInventoryRecord(ss, user);

    StockDeductionResult result = manager.deductStock(stoichiometryId, List.of(102L), user);

    assertEquals(1, result.getResults().size());
    assertEquals(Long.valueOf(102L), result.getResults().get(0).getLinkId());
    assertEquals("Molecule 2 Insufficient Stock", result.getResults().get(0).getErrorMessage());
    assertFalse(result.getResults().get(0).isSuccess());
    verify(linkDao, never()).save(any());
  }

  @Test
  public void deductStockWithUnexpectedExceptionReturnsGenericErrorMessage() {
    StoichiometryMolecule mol = new StoichiometryMolecule();
    mol.setStoichiometry(new Stoichiometry());
    long stoichiometryId = 55L;
    mol.getStoichiometry().setId(stoichiometryId);
    StoichiometryInventoryLink link = createMoleculeAndLink(103L, 1003L, mol);
    InventoryRecord ss = link.getInventoryRecord();
    when(linkDao.getSafeNull(103L)).thenReturn(java.util.Optional.of(link));
    when(moleculeManager.getDocContainingMolecule(mol)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    doThrow(new RuntimeException("Internal error not returned to user"))
        .when(invPerms)
        .assertUserCanEditInventoryRecord(ss, user);

    StockDeductionResult result = manager.deductStock(stoichiometryId, List.of(103L), user);

    assertEquals(1, result.getResults().size());
    assertEquals(Long.valueOf(103L), result.getResults().get(0).getLinkId());
    assertEquals(
        "An internal error occurred while deducting stock",
        result.getResults().get(0).getErrorMessage());
    assertFalse(result.getResults().get(0).isSuccess());
    verify(linkDao, never()).save(any());
  }
}

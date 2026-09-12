package com.researchspace.service.impl;

import com.researchspace.api.v1.model.stoichiometry.StockDeductionResult;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.dao.StoichiometryInventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.StoichiometryInventoryLinkManager;
import com.researchspace.service.StoichiometryMoleculeManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.SampleSiblingRowLock;
import com.researchspace.service.inventory.SubSampleApiManager;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StoichiometryInventoryLinkManagerImpl implements StoichiometryInventoryLinkManager {
  private final StoichiometryInventoryLinkDao linkDao;
  private final StoichiometryMoleculeManager stoichiometryMoleculeManager;
  private final IPermissionUtils elnPermissionUtils;
  private final InventoryPermissionUtils invPermissionUtils;
  private final SubSampleApiManager subSampleMgr;
  private final SampleSiblingRowLock siblingRowLock;
  private final QuantityUtils quantityUtils;
  private final MessageSourceUtils messages;

  @Autowired
  public StoichiometryInventoryLinkManagerImpl(
      StoichiometryInventoryLinkDao linkDao,
      StoichiometryMoleculeManager stoichiometryMoleculeManager,
      IPermissionUtils elnPermissionUtils,
      InventoryPermissionUtils invPermissionUtils,
      SubSampleApiManager subSampleMgr,
      SampleSiblingRowLock siblingRowLock,
      MessageSourceUtils messages) {
    this.linkDao = linkDao;
    this.stoichiometryMoleculeManager = stoichiometryMoleculeManager;
    this.elnPermissionUtils = elnPermissionUtils;
    this.invPermissionUtils = invPermissionUtils;
    this.subSampleMgr = subSampleMgr;
    this.siblingRowLock = siblingRowLock;
    this.messages = messages;
    this.quantityUtils = new QuantityUtils();
  }

  @Override
  public StoichiometryInventoryLink createLink(
      Long stoichiometryMoleculeId, StoichiometryInventoryLinkRequest req, User user) {
    StoichiometryMolecule stoichiometryMolecule =
        stoichiometryMoleculeManager.getById(stoichiometryMoleculeId);

    if (stoichiometryMolecule.getInventoryLink() != null) {
      throw new IllegalArgumentException(
          messages.getMessage("errors.inventory.stoichiometry.alreadyLinked"));
    }

    InventoryRecord inventoryRecord =
        invPermissionUtils.assertUserCanEditInventoryRecord(
            new GlobalIdentifier(req.getInventoryItemGlobalId()), user);

    if (inventoryRecord.isSampleTemplate()) {
      throw new IllegalArgumentException(
          messages.getMessage(
              "errors.inventory.stoichiometry.unsupportedLinkTarget",
              new Object[] {inventoryRecord.getGlobalIdentifier()}));
    }

    StoichiometryInventoryLink link = new StoichiometryInventoryLink();
    link.setStoichiometryMolecule(stoichiometryMolecule);
    link.setInventoryRecord(inventoryRecord);

    return linkDao.save(link);
  }

  @Override
  public StockDeductionResult deductStock(long stoichiometryId, List<Long> linkIds, User user) {
    StockDeductionResult result = new StockDeductionResult();
    result.setStoichiometryId(stoichiometryId);
    // Lock every distinct parent sample's sibling rows up front, ascending by sample id, before
    // any per-link row lock. Every writer that touches multiple lock groups must acquire them in
    // this one canonical order (sibling sets ascending, then rows): the sibling-set lock IS row
    // locks on all of a sample's subsamples, so once the sets are held, every later row lock is a
    // re-acquisition and cannot participate in a deadlock cycle against the operations endpoint or
    // List of Materials. Only a link that belongs to THIS stoichiometry and whose record the caller
    // may edit locks anything here: without those filters a caller could name unrelated link ids
    // solely to lock their sibling sets and delay authorized writers (Copilot review, PR #1090).
    // Any link the filters skip, like an unresolvable or non-subsample one, locks nothing and
    // fails per-row below with its specific reason, as before; the loop's own checks are retained.
    Map<Long, StoichiometryInventoryLink> resolved = resolveOnce(linkIds);
    Set<Long> parentSampleIds = new TreeSet<>();
    for (StoichiometryInventoryLink link : resolved.values()) {
      if (link.getStoichiometryMolecule().getStoichiometry().getId() != stoichiometryId) {
        continue;
      }
      if (!invPermissionUtils.canUserEditInventoryRecord(link.getInventoryRecord(), user)) {
        continue;
      }
      if (link.getInventoryRecord() instanceof SubSample subSample) {
        parentSampleIds.add(subSample.getSample().getId());
      }
    }
    parentSampleIds.forEach(siblingRowLock::lockSiblingRowsAndRecalculateTotal);

    // dedupe: a repeated link id deducts its amount once (RSDEV-1319). The response still carries
    // one result row per submitted entry, so the API's cardinality contract is unchanged
    Map<Long, StockDeductionResult.IndividualResult> resultsById = new HashMap<>();
    for (Long id : inLockOrder(linkIds, resolved)) {
      try {
        StoichiometryInventoryLink link = requireLink(resolved, id);
        StoichiometryMolecule stoichiometryMolecule = link.getStoichiometryMolecule();
        if (stoichiometryMolecule.getStoichiometry().getId() != stoichiometryId) {
          throw new IllegalArgumentException(
              messages.getMessage(
                  "errors.inventory.stoichiometry.linkNotInStoichiometry",
                  new Object[] {id, stoichiometryId}));
        }
        verifyStoichiometryPermissions(stoichiometryMolecule, PermissionType.WRITE, user);
        invPermissionUtils.assertUserCanEditInventoryRecord(link.getInventoryRecord(), user);

        Double actualAmount = stoichiometryMolecule.getActualAmount();
        if (actualAmount == null) {
          throw new IllegalArgumentException(
              messages.getMessage("errors.inventory.stoichiometry.actualAmountRequired"));
        }
        QuantityInfo quantityInfo =
            new QuantityInfo(BigDecimal.valueOf(actualAmount), RSUnitDef.GRAM.getId());

        processStockDeduction(user, link, quantityInfo, link.getInventoryRecord());
        if (!link.isStockDeducted()) {
          link.setStockDeducted(true);
          linkDao.save(link);
        }
        resultsById.put(id, new StockDeductionResult.IndividualResult(id, true));
      } catch (NotFoundException | IllegalArgumentException e) {
        resultsById.put(id, new StockDeductionResult.IndividualResult(id, false, e.getMessage()));
      } catch (DataAccessException e) {
        // A row lock this method takes can fail (deadlock loser, lock-wait timeout, stale row).
        // Hibernate leaves the session unusable and the transaction rollback-only afterwards, so
        // continuing the loop would run the remaining links on a poisoned session and end in an
        // UnexpectedRollbackException that loses every per-row result. Let it out instead: the
        // transaction rolls back cleanly and the tier maps it to a 409 the caller can retry.
        throw e;
      } catch (Exception e) {
        log.error("Unexpected error deducting stock for link {}", id, e);
        resultsById.put(
            id,
            new StockDeductionResult.IndividualResult(
                id, false, messages.getMessage("errors.inventory.stoichiometry.deductionFailed")));
      }
    }
    linkIds.forEach(id -> result.addResult(resultsById.get(id)));
    return result;
  }

  /**
   * The submitted link ids, deduped and ordered by the inventory record each one points at. Two
   * deductions over the same subsamples submitted in opposite orders would otherwise each hold one
   * row and wait for the other; taking the rows in id order means one simply waits for the other
   * (DevDocs/adr/0007). An id whose link cannot be resolved sorts last: it fails as a not-found row
   * without locking anything. The key carries the record's type as well as its id because Sample,
   * SubSample and Container ids are separate spaces and can collide: only subsample rows are locked
   * today, so a collision is harmless now, but it would become a deadlock the day another record
   * type is deducted from.
   */
  private List<Long> inLockOrder(
      List<Long> linkIds, Map<Long, StoichiometryInventoryLink> resolved) {
    return linkIds.stream()
        .distinct()
        .map(id -> Map.entry(id, lockOrderKey(resolved, id)))
        .sorted(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .toList();
  }

  private String lockOrderKey(Map<Long, StoichiometryInventoryLink> resolved, Long linkId) {
    return Optional.ofNullable(resolved.get(linkId))
        .map(StoichiometryInventoryLink::getInventoryRecord)
        .map(record -> String.format("%s%020d", record.getType(), record.getId()))
        .orElse("~");
  }

  /**
   * Every distinct submitted id that names a link, loaded once. The three passes of {@link
   * #deductStock} - the sibling-set lock, the lock ordering and the deduction loop - each used to
   * load the link again, so a five-id request issued fifteen loads and {@code lockOrderKey} in
   * particular resolved arbitrary ids twenty lines below the filtering written to stop exactly that
   * (parallel review, S4).
   *
   * <p>Resolution is deliberately NOT permission-filtered. The loop answers "no such link", "not in
   * this stoichiometry" and "you may not edit that record" with three different messages, so it
   * needs the link even where the caller has no right to it. What IS filtered is the set of sibling
   * sets locked up front, which is the pass a caller could otherwise abuse to lock rows they cannot
   * edit.
   */
  private Map<Long, StoichiometryInventoryLink> resolveOnce(List<Long> linkIds) {
    Map<Long, StoichiometryInventoryLink> resolved = new HashMap<>();
    linkIds.stream()
        .distinct()
        .forEach(id -> linkDao.getSafeNull(id).ifPresent(link -> resolved.put(id, link)));
    return resolved;
  }

  private void processStockDeduction(
      User user,
      StoichiometryInventoryLink link,
      QuantityInfo quantityInfo,
      InventoryRecord inventoryRecord) {
    if (link.getInventoryRecord() instanceof SubSample) {
      SubSample subSample = (SubSample) link.getInventoryRecord();
      // Sibling-set lock BEFORE the row lock, the canonical order every stock writer uses. For
      // deductStock this is a re-acquisition (the sets were locked up front); it stands on its own
      // so any future caller of this method cannot reintroduce the row-then-set inversion, where
      // two deductions on two siblings each hold their own row and wait for the other's.
      siblingRowLock.lockSiblingRowsAndRecalculateTotal(subSample.getSample().getId());
      // The over-use check reads the row it is about to decrement, under the same lock the
      // decrement takes, rather than the link's own copy: a concurrent operation may have drained
      // the subsample since that copy was loaded, and registerApiSubSampleUsage clamps at zero, so
      // a check against stale stock would report success for a deduction that never happened. The
      // value is a locked SCALAR, not the locked entity: the entity holds this transaction's
      // snapshot (locking guarantees serialisation only).
      SubSample liveSubSample = subSampleMgr.lockSubSampleForEdit(subSample.getId(), user);
      QuantityInfo currentQuantity = subSampleMgr.getQuantityForUpdate(subSample.getId());
      BigDecimal totalAfterStockUpdate =
          quantityUtils.sum(List.of(currentQuantity, quantityInfo.negate())).getNumericValue();
      if (totalAfterStockUpdate.compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException(
            messages.getMessage(
                "errors.inventory.stoichiometry.insufficientStock",
                new Object[] {
                  quantityInfo.toPlainString(),
                  currentQuantity.toPlainString(),
                  liveSubSample.getGlobalIdentifier()
                }));
      }
      subSampleMgr.registerApiSubSampleUsage(inventoryRecord.getId(), quantityInfo, user);
      generateNewStoichiometryRevision(link.getStoichiometryMolecule());
    }
  }

  /**
   * Ensures each change to an inventory link (add new, update quantity, delete) creates a new
   * Stoichiometry revision.
   */
  private void generateNewStoichiometryRevision(StoichiometryMolecule stoichiometryMolecule) {
    Stoichiometry parent = stoichiometryMolecule.getStoichiometry();
    parent.touchForAudit();
  }

  private void verifyStoichiometryPermissions(
      StoichiometryMolecule stoichiometryMolecule, PermissionType permissionType, User user) {
    StructuredDocument recordContainingStoichiometry =
        stoichiometryMoleculeManager.getDocContainingMolecule(stoichiometryMolecule);
    if (!elnPermissionUtils.isPermitted(recordContainingStoichiometry, permissionType, user)) {
      throw new NotFoundException(
          messages.getMessage(
              "errors.inventory.stoichiometry.notAccessible", new Object[] {permissionType}));
    }
  }

  private StoichiometryInventoryLink requireLink(
      Map<Long, StoichiometryInventoryLink> resolved, Long linkId) {
    StoichiometryInventoryLink link = resolved.get(linkId);
    if (link == null) {
      throw new NotFoundException(
          messages.getMessage(
              "errors.inventory.stoichiometry.linkNotFound", new Object[] {linkId}));
    }
    return link;
  }
}

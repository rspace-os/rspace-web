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
import com.researchspace.service.inventory.SubSampleApiManager;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StoichiometryInventoryLinkManagerImpl implements StoichiometryInventoryLinkManager {
  private final StoichiometryInventoryLinkDao linkDao;
  private final StoichiometryMoleculeManager stoichiometryMoleculeManager;
  private final IPermissionUtils elnPermissionUtils;
  private final InventoryPermissionUtils invPermissionUtils;
  private final SubSampleApiManager subSampleMgr;
  private final QuantityUtils quantityUtils;
  private final MessageSourceUtils messages;

  @Autowired
  public StoichiometryInventoryLinkManagerImpl(
      StoichiometryInventoryLinkDao linkDao,
      StoichiometryMoleculeManager stoichiometryMoleculeManager,
      IPermissionUtils elnPermissionUtils,
      InventoryPermissionUtils invPermissionUtils,
      SubSampleApiManager subSampleMgr,
      MessageSourceUtils messages) {
    this.linkDao = linkDao;
    this.stoichiometryMoleculeManager = stoichiometryMoleculeManager;
    this.elnPermissionUtils = elnPermissionUtils;
    this.invPermissionUtils = invPermissionUtils;
    this.subSampleMgr = subSampleMgr;
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
    // dedupe: a repeated link id deducts its amount once (RSDEV-1319). The response still carries
    // one result row per submitted entry, so the API's cardinality contract is unchanged
    Map<Long, StockDeductionResult.IndividualResult> resultsById = new HashMap<>();
    for (Long id : inLockOrder(linkIds)) {
      try {
        StoichiometryInventoryLink link = getLinkOrThrowNotFound(id);
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
   * without locking anything.
   */
  private List<Long> inLockOrder(List<Long> linkIds) {
    return linkIds.stream()
        .distinct()
        .map(id -> Map.entry(id, lockOrderKey(id)))
        .sorted(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .toList();
  }

  private Long lockOrderKey(Long linkId) {
    return linkDao
        .getSafeNull(linkId)
        .map(StoichiometryInventoryLink::getInventoryRecord)
        .map(InventoryRecord::getId)
        .orElse(Long.MAX_VALUE);
  }

  private void processStockDeduction(
      User user,
      StoichiometryInventoryLink link,
      QuantityInfo quantityInfo,
      InventoryRecord inventoryRecord) {
    if (link.getInventoryRecord() instanceof SubSample) {
      SubSample subSample = (SubSample) link.getInventoryRecord();
      // The over-use check reads the row it is about to decrement, under the same lock the
      // decrement takes, rather than the link's own copy: a concurrent operation may have drained
      // the subsample since that copy was loaded, and registerApiSubSampleUsage clamps at zero, so
      // a check against stale stock would report success for a deduction that never happened.
      SubSample liveSubSample = subSampleMgr.lockSubSampleForEdit(subSample.getId(), user);
      BigDecimal totalAfterStockUpdate =
          quantityUtils
              .sum(List.of(liveSubSample.getQuantity(), quantityInfo.negate()))
              .getNumericValue();
      if (totalAfterStockUpdate.compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException(
            messages.getMessage(
                "errors.inventory.stoichiometry.insufficientStock",
                new Object[] {
                  quantityInfo.toPlainString(),
                  liveSubSample.getQuantity().toPlainString(),
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

  private StoichiometryInventoryLink getLinkOrThrowNotFound(long linkId) {
    return linkDao
        .getSafeNull(linkId)
        .orElseThrow(
            () ->
                new NotFoundException(
                    messages.getMessage(
                        "errors.inventory.stoichiometry.linkNotFound", new Object[] {linkId})));
  }
}

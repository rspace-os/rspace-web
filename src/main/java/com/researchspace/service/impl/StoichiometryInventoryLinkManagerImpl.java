package com.researchspace.service.impl;

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
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.StoichiometryInventoryLinkManager;
import com.researchspace.service.StoichiometryMoleculeManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.SubSampleApiManager;
import java.math.BigDecimal;
import java.util.List;
import javax.ws.rs.NotFoundException;
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

  @Autowired
  public StoichiometryInventoryLinkManagerImpl(
      StoichiometryInventoryLinkDao linkDao,
      StoichiometryMoleculeManager stoichiometryMoleculeManager,
      IPermissionUtils elnPermissionUtils,
      InventoryPermissionUtils invPermissionUtils,
      SubSampleApiManager subSampleMgr) {
    this.linkDao = linkDao;
    this.stoichiometryMoleculeManager = stoichiometryMoleculeManager;
    this.elnPermissionUtils = elnPermissionUtils;
    this.invPermissionUtils = invPermissionUtils;
    this.subSampleMgr = subSampleMgr;
    this.quantityUtils = new QuantityUtils();
  }

  @Override
  public StoichiometryInventoryLink createLink(
      Long stoichiometryMoleculeId, StoichiometryInventoryLinkRequest req, User user) {
    StoichiometryMolecule stoichiometryMolecule =
        stoichiometryMoleculeManager.getById(stoichiometryMoleculeId);

    verifyStoichiometryPermissions(stoichiometryMolecule, PermissionType.WRITE, user);
    if (stoichiometryMolecule.getInventoryLink() != null) {
      throw new IllegalArgumentException("Stoichiometry molecule already has an inventory link");
    }

    InventoryRecord inventoryRecord =
        invPermissionUtils.assertUserCanEditInventoryRecord(
            new GlobalIdentifier(req.getInventoryItemGlobalId()), user);

    if (inventoryRecord instanceof Sample) {
      Sample sample = (Sample) inventoryRecord;
      if (sample.isTemplate()) {
        throw new IllegalArgumentException(
            inventoryRecord.getGlobalIdentifier()
                + " is a sample template. Only Containers, Samples and Subsamples are valid for"
                + " linking.");
      }
    }

    StoichiometryInventoryLink link = new StoichiometryInventoryLink();
    link.setStoichiometryMolecule(stoichiometryMolecule);
    link.setInventoryRecord(inventoryRecord);

    link = linkDao.save(link);
    generateNewStoichiometryRevision(stoichiometryMolecule);
    return link;
  }

  @Override
  public StockDeductionResult deductStock(List<Long> linkIds, User user) {
    StockDeductionResult result = new StockDeductionResult();
    for (Long id : linkIds) {
      try {
        StoichiometryInventoryLink link = getLinkOrThrowNotFound(id);
        StoichiometryMolecule stoichiometryMolecule = link.getStoichiometryMolecule();
        verifyStoichiometryPermissions(stoichiometryMolecule, PermissionType.WRITE, user);
        invPermissionUtils.assertUserCanEditInventoryRecord(link.getInventoryRecord(), user);

        Double actualAmount = stoichiometryMolecule.getActualAmount();
        if (actualAmount == null) {
          throw new IllegalArgumentException("Molecule actual amount must be set for deduction");
        }
        QuantityInfo quantityInfo =
            new QuantityInfo(BigDecimal.valueOf(actualAmount), RSUnitDef.GRAM.getId());

        processStockDeduction(user, link, quantityInfo, link.getInventoryRecord());
        if (!link.isStockDeducted()) {
          link.setStockDeducted(true);
          linkDao.save(link);
          generateNewStoichiometryRevision(stoichiometryMolecule);
        }
        result.addResult(new StockDeductionResult.IndividualResult(id, true, null));
      } catch (NotFoundException | IllegalArgumentException e) {
        result.addResult(new StockDeductionResult.IndividualResult(id, false, e.getMessage()));
      } catch (Exception e) {
        log.error("Unexpected error deducting stock for link {}", id, e);
        result.addResult(
            new StockDeductionResult.IndividualResult(
                id, false, "An internal error occurred while deducting stock"));
      }
    }
    return result;
  }

  private void processStockDeduction(
      User user,
      StoichiometryInventoryLink link,
      QuantityInfo quantityInfo,
      InventoryRecord inventoryRecord) {
    if (link.getInventoryRecord() instanceof SubSample) {
      SubSample subSample = (SubSample) link.getInventoryRecord();
      BigDecimal totalAfterStockUpdate =
          quantityUtils
              .sum(List.of(subSample.getQuantity(), quantityInfo.negate()))
              .getNumericValue();
      if (totalAfterStockUpdate.compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException(
            "Insufficient stock to perform this action. Attempting to use "
                + quantityInfo.toPlainString()
                + " of stock amount "
                + subSample.getQuantity().toPlainString()
                + " for "
                + subSample.getGlobalIdentifier());
      }
      subSampleMgr.registerApiSubSampleUsage(inventoryRecord.getId(), quantityInfo, user);
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

  @Override
  public StoichiometryInventoryLink getById(long linkId, User user) {
    StoichiometryInventoryLink entity = getLinkOrThrowNotFound(linkId);
    verifyStoichiometryPermissions(entity.getStoichiometryMolecule(), PermissionType.READ, user);
    invPermissionUtils.assertUserCanEditInventoryRecord(entity.getInventoryRecord(), user);
    return entity;
  }

  @Override
  public void deleteLink(long linkId, User user) {
    StoichiometryInventoryLink entity = getLinkOrThrowNotFound(linkId);
    verifyStoichiometryPermissions(entity.getStoichiometryMolecule(), PermissionType.WRITE, user);
    invPermissionUtils.assertUserCanEditInventoryRecord(entity.getInventoryRecord(), user);
    linkDao.remove(linkId);
    generateNewStoichiometryRevision(entity.getStoichiometryMolecule());
  }

  private void verifyStoichiometryPermissions(
      StoichiometryMolecule stoichiometryMolecule, PermissionType permissionType, User user) {
    StructuredDocument recordContainingStoichiometry =
        stoichiometryMoleculeManager.getDocContainingMolecule(stoichiometryMolecule);
    if (!elnPermissionUtils.isPermitted(recordContainingStoichiometry, permissionType, user)) {
      throw new NotFoundException(
          String.format(
              "Stoichiometry resource not found or not accessible (missing %s permissions)",
              permissionType));
    }
  }

  private StoichiometryInventoryLink getLinkOrThrowNotFound(long linkId) {
    return linkDao
        .getSafeNull(linkId)
        .orElseThrow(() -> new NotFoundException("Stoichiometry link not found: id=" + linkId));
  }
}

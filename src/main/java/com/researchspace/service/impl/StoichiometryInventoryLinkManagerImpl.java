package com.researchspace.service.impl;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkDTO;
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
  public StoichiometryInventoryLinkDTO createLink(
      StoichiometryInventoryLinkRequest req, User user) {
    StoichiometryMolecule stoichiometryMolecule =
        stoichiometryMoleculeManager.getById(req.getStoichiometryMoleculeId());

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

    QuantityInfo quantityInfo = makeQuantity(req);
    link.setQuantity(quantityInfo);
    link.setReducesStock(req.reducesStock());
    link = linkDao.save(link);

    processStockReduction(user, link, quantityInfo, inventoryRecord);
    generateNewStoichiometryRevision(stoichiometryMolecule);
    return new StoichiometryInventoryLinkDTO(link);
  }

  private void processStockReduction(User user, StoichiometryInventoryLink link, QuantityInfo quantityInfo, InventoryRecord inventoryRecord) {
    if (link.getInventoryRecord() instanceof SubSample && link.getReducesStock()) {
      SubSample subSample = (SubSample) link.getInventoryRecord();
      BigDecimal totalAfterStockUpdate = quantityUtils.sum(List.of(subSample.getQuantity(), quantityInfo.negate())).getNumericValue();
      if(totalAfterStockUpdate.compareTo(BigDecimal.ZERO) < 0){
        throw new IllegalArgumentException("Insufficient stock to perform this action. Attempting to use " + quantityInfo.toPlainString() + " of stock amount " + subSample.getQuantity().toPlainString() + " for " + subSample.getGlobalIdentifier());
      }
      subSampleMgr.registerApiSubSampleUsage(inventoryRecord.getId(), quantityInfo, user);
    }
  }

  private QuantityInfo makeQuantity(StoichiometryInventoryLinkRequest request) {
    if (request.getQuantity() != null && request.getUnitId() != null) {
      return new QuantityInfo(request.getQuantity(), request.getUnitId());
    } else {
      throw new IllegalArgumentException("quantityUsed and unitId are required");
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
  public StoichiometryInventoryLinkDTO getById(long linkId, User user) {
    StoichiometryInventoryLink entity = getLinkOrThrowNotFound(linkId);
    verifyStoichiometryPermissions(entity.getStoichiometryMolecule(), PermissionType.READ, user);
    invPermissionUtils.assertUserCanEditInventoryRecord(entity.getInventoryRecord(), user);
    return new StoichiometryInventoryLinkDTO(entity);
  }

  @Override
  public StoichiometryInventoryLinkDTO updateQuantity(
      long linkId, ApiQuantityInfo newQuantity, User user) {
    if (newQuantity.getUnitId() == null) {
      throw new IllegalArgumentException("Unit ID is required");
    }

    StoichiometryInventoryLink entity = getLinkOrThrowNotFound(linkId);
    verifyStoichiometryPermissions(entity.getStoichiometryMolecule(), PermissionType.WRITE, user);
    invPermissionUtils.assertUserCanEditInventoryRecord(entity.getInventoryRecord(), user);

    processStockReduction(user, entity, newQuantity.toQuantityInfo(), entity.getInventoryRecord());

    entity.setQuantity(newQuantity.toQuantityInfo());
    entity = linkDao.save(entity);
    generateNewStoichiometryRevision(entity.getStoichiometryMolecule());
    return new StoichiometryInventoryLinkDTO(entity);
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

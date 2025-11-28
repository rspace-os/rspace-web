package com.researchspace.service.impl;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkDTO;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.dao.StoichiometryInventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.service.StoichiometryInventoryLinkManager;
import com.researchspace.service.StoichiometryMoleculeManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
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

  @Autowired
  public StoichiometryInventoryLinkManagerImpl(
      StoichiometryInventoryLinkDao linkDao,
      StoichiometryMoleculeManager stoichiometryMoleculeManager,
      IPermissionUtils elnPermissionUtils,
      InventoryPermissionUtils invPermissionUtils) {
    this.linkDao = linkDao;
    this.stoichiometryMoleculeManager = stoichiometryMoleculeManager;
    this.elnPermissionUtils = elnPermissionUtils;
    this.invPermissionUtils = invPermissionUtils;
  }

  @Override
  public StoichiometryInventoryLinkDTO createLink(
      StoichiometryInventoryLinkRequest req, User user) {
    StoichiometryMolecule stoichiometryMolecule =
        stoichiometryMoleculeManager.getById(req.getStoichiometryMoleculeId());

    verifyStoichiometryPermissions(stoichiometryMolecule, PermissionType.WRITE, user);

    InventoryRecord inventoryRecord =
        invPermissionUtils.assertUserCanEditInventoryRecord(
            new GlobalIdentifier(req.getInventoryItemGlobalId()), user);

    StoichiometryInventoryLink link = new StoichiometryInventoryLink();
    link.setStoichiometryMolecule(stoichiometryMolecule);
    link.setInventoryRecord(inventoryRecord);
    QuantityInfo quantityInfo = makeQuantity(req);
    link.setQuantityUsed(quantityInfo);
    link = linkDao.save(link);
    generateNewStoichiometryRevision(stoichiometryMolecule);
    return toDto(link);
  }

  private QuantityInfo makeQuantity(StoichiometryInventoryLinkRequest request) {
    if (request.getQuantityUsed() != null && request.getUnitId() != null) {
      return new QuantityInfo(request.getQuantityUsed(), request.getUnitId());
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
    return toDto(entity);
  }

  @Override
  public StoichiometryInventoryLinkDTO updateQuantity(
      long linkId, ApiQuantityInfo newQuantity, User user) {
    StoichiometryInventoryLink entity = getLinkOrThrowNotFound(linkId);
    verifyStoichiometryPermissions(entity.getStoichiometryMolecule(), PermissionType.WRITE, user);
    invPermissionUtils.assertUserCanEditInventoryRecord(entity.getInventoryRecord(), user);
    entity.setQuantityUsed(newQuantity.toQuantityInfo());
    entity = linkDao.save(entity);
    generateNewStoichiometryRevision(entity.getStoichiometryMolecule());
    return toDto(entity);
  }

  @Override
  public void deleteLink(long linkId, User user) {
    StoichiometryInventoryLink entity = getLinkOrThrowNotFound(linkId);
    verifyStoichiometryPermissions(entity.getStoichiometryMolecule(), PermissionType.WRITE, user);
    invPermissionUtils.assertUserCanEditInventoryRecord(entity.getInventoryRecord(), user);
    linkDao.remove(linkId);
    generateNewStoichiometryRevision(entity.getStoichiometryMolecule());
  }

  private StoichiometryInventoryLinkDTO toDto(StoichiometryInventoryLink entity) {
    StoichiometryInventoryLinkDTO dto = new StoichiometryInventoryLinkDTO();
    dto.setId(entity.getId());
    dto.setInventoryItemGlobalId(entity.getInventoryRecord().getOid().getIdString());
    dto.setStoichiometryMoleculeId(entity.getStoichiometryMolecule().getId());
    dto.setQuantityUsed(
        entity.getQuantityUsed().getNumericValue() == null
            ? null
            : new ApiQuantityInfo(
                entity.getQuantityUsed().getNumericValue(), entity.getQuantityUsed().getUnitId()));
    return dto;
  }

  private void verifyStoichiometryPermissions(
      StoichiometryMolecule stoichiometryMolecule, PermissionType permissionType, User user) {
    BaseRecord recordContainingStoichiometry =
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

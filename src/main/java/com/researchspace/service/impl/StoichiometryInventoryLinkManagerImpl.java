package com.researchspace.service.impl;

import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkDTO;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.dao.StoichiometryInventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
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
    link.setQuantityUsed(req.getQuantityUsed());
    link = linkDao.save(link);
    return toDto(link);
  }

  @Override
  public StoichiometryInventoryLinkDTO getById(long linkId, User user) {
    StoichiometryInventoryLink entity = getLinkOrThrowNotFound(linkId);
    verifyStoichiometryPermissions(entity.getStoichiometryMolecule(), PermissionType.READ, user);
    invPermissionUtils.assertUserCanEditInventoryRecord(entity.getInventoryRecord(), user);
    return toDto(entity);
  }

  @Override
  public StoichiometryInventoryLinkDTO updateQuantity(long linkId, double newQuantity, User user) {
    StoichiometryInventoryLink entity = getLinkOrThrowNotFound(linkId);
    verifyStoichiometryPermissions(entity.getStoichiometryMolecule(), PermissionType.WRITE, user);
    invPermissionUtils.assertUserCanEditInventoryRecord(entity.getInventoryRecord(), user);
    entity.setQuantityUsed(newQuantity);
    entity = linkDao.save(entity);
    return toDto(entity);
  }

  @Override
  public void deleteLink(long linkId, User user) {
    StoichiometryInventoryLink entity = getLinkOrThrowNotFound(linkId);
    verifyStoichiometryPermissions(entity.getStoichiometryMolecule(), PermissionType.WRITE, user);
    invPermissionUtils.assertUserCanEditInventoryRecord(entity.getInventoryRecord(), user);
    linkDao.remove(linkId);
  }

  private StoichiometryInventoryLinkDTO toDto(StoichiometryInventoryLink e) {
    StoichiometryInventoryLinkDTO dto = new StoichiometryInventoryLinkDTO();
    dto.setId(e.getId());
    dto.setInventoryItemGlobalId(e.getInventoryRecord().getOid().getIdString());
    dto.setStoichiometryMoleculeId(e.getStoichiometryMolecule().getId());
    dto.setQuantityUsed(e.getQuantityUsed());
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

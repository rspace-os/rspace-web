package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.LinkTargetResolver;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryLinkManagerImpl implements InventoryLinkManager {

  @Autowired private InventoryLinkDao linkDao;
  @Autowired private InventoryPermissionUtils permissionUtils;
  @Autowired private LinkTargetResolver linkTargetResolver;

  @Override
  public InventoryLink createLink(ApiInventoryLink apiLink, User actor) {
    assertTargetExistsAndReadable(apiLink, actor);
    InventoryLink entity = new InventoryLink();
    applyApiToEntity(apiLink, entity);
    return linkDao.save(entity);
  }

  @Override
  public InventoryLink updateLink(InventoryLink existing, ApiInventoryLink apiLink, User actor) {
    assertTargetExistsAndReadable(apiLink, actor);
    applyApiToEntity(apiLink, existing);
    return linkDao.save(existing);
  }

  @Override
  public void deleteLink(InventoryLink existing, User actor) {
    existing.setDeleted(true);
    linkDao.save(existing);
  }

  @Override
  public List<ApiInventoryReferencingItem> findReferencingItems(String targetGlobalId, User actor) {
    GlobalIdentifier target = new GlobalIdentifier(targetGlobalId);
    List<ExtraLinkField> fields =
        linkDao.findReferencingLinkFields(target.getPrefix(), target.getDbId());
    List<ApiInventoryReferencingItem> rows = new ArrayList<>(fields.size());
    for (ExtraLinkField field : fields) {
      InventoryRecord parent = field.getInventoryRecord();
      if (parent == null) {
        continue;
      }
      if (!permissionUtils.canUserReadInventoryRecord(parent, actor)) {
        continue;
      }
      ApiInventoryReferencingItem row = new ApiInventoryReferencingItem();
      row.setSourceGlobalId(parent.getOid().toString());
      row.setSourceName(parent.getName());
      row.setSourceType(parent.getType().toString());
      row.setRelationType(field.getLink().getRelationType());
      row.setVersionPin(field.getLink().getVersionPin());
      if (field.getLink().getModifiedAt() != null) {
        row.setModifiedAtMillis(field.getLink().getModifiedAt().getTime());
      }
      rows.add(row);
    }
    return rows;
  }

  /**
   * Rejects links whose target does not resolve to a real record the actor can READ. Applies to all
   * targets, Inventory and ELN alike. The version suffix (if any) is ignored: only the base record
   * needs to exist and be readable.
   */
  private void assertTargetExistsAndReadable(ApiInventoryLink apiLink, User actor) {
    GlobalIdentifier gid = new GlobalIdentifier(apiLink.getTargetGlobalId());
    if (!linkTargetResolver.targetExistsAndIsReadable(gid, actor)) {
      throw new ApiRuntimeException(
          "errors.inventory.field.link.targetNotFound", apiLink.getTargetGlobalId());
    }
  }

  private void applyApiToEntity(ApiInventoryLink api, InventoryLink entity) {
    entity.setRelationType(api.getRelationType());
    entity.setTargetGlobalId(api.getTargetGlobalId());
    GlobalIdentifier gid = new GlobalIdentifier(api.getTargetGlobalId());
    entity.setTargetPrefix(gid.getPrefix());
    entity.setTargetDbId(gid.getDbId());
    if (gid.hasVersionId()) {
      entity.setVersionPin(gid.getVersionId());
    } else {
      entity.setVersionPin(api.getVersionPin());
    }
  }
}

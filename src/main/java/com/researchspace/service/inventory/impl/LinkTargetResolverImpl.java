package com.researchspace.service.inventory.impl;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.LinkTargetResolver;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.NotFoundException;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Component;

/**
 * Resolves a link target across modules: Inventory items via {@link InventoryPermissionUtils}, ELN
 * items (documents, notebooks, gallery files) via {@link BaseRecordManager}. The supported prefixes
 * mirror {@link com.researchspace.service.inventory.InventoryLinkValidator}'s allowed target kinds.
 * Any failure to resolve a record (not found, not readable, unsupported prefix) is reported as "not
 * readable" rather than propagated, so the caller can reject the link uniformly.
 */
@Component("linkTargetResolver")
public class LinkTargetResolverImpl implements LinkTargetResolver {

  private static final Set<GlobalIdPrefix> INVENTORY_PREFIXES =
      EnumSet.of(
          GlobalIdPrefix.SA,
          GlobalIdPrefix.SS,
          GlobalIdPrefix.IC,
          GlobalIdPrefix.IN,
          GlobalIdPrefix.IT);

  private static final Set<GlobalIdPrefix> ELN_BASE_RECORD_PREFIXES =
      EnumSet.of(GlobalIdPrefix.SD, GlobalIdPrefix.NB, GlobalIdPrefix.GL);

  @Autowired private InventoryPermissionUtils inventoryPermissionUtils;
  @Autowired private BaseRecordManager baseRecordManager;

  @Override
  public boolean targetExistsAndIsReadable(GlobalIdentifier target, User user) {
    if (target == null) {
      return false;
    }
    GlobalIdentifier base =
        target.hasVersionId() ? new GlobalIdentifier(target.getPrefix(), target.getDbId()) : target;
    GlobalIdPrefix prefix = base.getPrefix();
    if (INVENTORY_PREFIXES.contains(prefix)) {
      return isInventoryReadable(base, user);
    }
    if (ELN_BASE_RECORD_PREFIXES.contains(prefix)) {
      return isElnReadable(base, user);
    }
    return false;
  }

  private boolean isInventoryReadable(GlobalIdentifier target, User user) {
    try {
      return inventoryPermissionUtils.canUserReadInventoryRecord(target, user);
    } catch (NotFoundException e) {
      return false;
    }
  }

  private boolean isElnReadable(GlobalIdentifier target, User user) {
    try {
      List<BaseRecord> readable =
          baseRecordManager.getByGlobalIdsAndReadPermission(
              Collections.singletonList(target), user);
      // the loader resolves by numeric id alone, so a typed id can load a
      // different record kind sharing the number (e.g. "GL150" loads folder
      // FL150): only a record whose own oid prefix matches the requested one
      // counts as the link target
      for (BaseRecord record : readable) {
        if (record.getOid() != null && record.getOid().getPrefix() == target.getPrefix()) {
          return true;
        }
      }
      return false;
    } catch (ObjectRetrievalFailureException | AuthorizationException e) {
      return false;
    }
  }
}

package com.researchspace.service.inventory.impl;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.LinkTargetResolver;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
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
          GlobalIdPrefix.IT,
          GlobalIdPrefix.NT);

  private static final Set<GlobalIdPrefix> ELN_BASE_RECORD_PREFIXES =
      EnumSet.of(GlobalIdPrefix.SD, GlobalIdPrefix.NB, GlobalIdPrefix.GL);

  @Autowired private InventoryPermissionUtils inventoryPermissionUtils;
  @Autowired private BaseRecordManager baseRecordManager;
  @Autowired private IPermissionUtils permissionUtils;

  @Override
  public boolean targetExistsAndIsReadable(GlobalIdentifier target, User user) {
    if (target == null) {
      return false;
    }
    // an unshare notifies the affected user to refresh their cached Shiro
    // authorisation; apply any pending refresh before checking, or the
    // viewer keeps the revoked read grant until the server restarts
    permissionUtils.refreshCacheIfNotified();
    GlobalIdentifier base =
        target.hasVersionId() ? new GlobalIdentifier(target.getPrefix(), target.getDbId()) : target;
    GlobalIdPrefix prefix = base.getPrefix();
    if (INVENTORY_PREFIXES.contains(prefix)) {
      // deliberately deleted-tolerant: a trashed target the actor can read is still a legitimate
      // link target, and only targetIsLiveAndReadable adds the not-deleted filter. The shared
      // helper supplies the type-exact check this method needs just as much, since it gates link
      // creation and findReferencingItems.
      return readableInventoryRecord(base, user).isPresent();
    }
    if (ELN_BASE_RECORD_PREFIXES.contains(prefix)) {
      return isElnReadable(base, user);
    }
    return false;
  }

  @Override
  public boolean targetIsLiveAndReadable(GlobalIdentifier target, User user) {
    if (target == null) {
      return false;
    }
    permissionUtils.refreshCacheIfNotified();
    GlobalIdentifier base =
        target.hasVersionId() ? new GlobalIdentifier(target.getPrefix(), target.getDbId()) : target;
    GlobalIdPrefix prefix = base.getPrefix();
    if (INVENTORY_PREFIXES.contains(prefix)) {
      return readableInventoryRecord(base, user).filter(record -> !record.isDeleted()).isPresent();
    }
    if (ELN_BASE_RECORD_PREFIXES.contains(prefix)) {
      return liveReadableElnRecord(base, user).isPresent();
    }
    return false;
  }

  @Override
  public Optional<InventoryRecord> viewableInventoryTarget(GlobalIdentifier target, User user) {
    if (target == null) {
      return Optional.empty();
    }
    permissionUtils.refreshCacheIfNotified();
    GlobalIdentifier base =
        target.hasVersionId() ? new GlobalIdentifier(target.getPrefix(), target.getDbId()) : target;
    if (!INVENTORY_PREFIXES.contains(base.getPrefix())) {
      return Optional.empty();
    }
    return typeExactInventoryRecord(base)
        .filter(
            record ->
                inventoryPermissionUtils.canUserReadInventoryRecord(record, user)
                    || inventoryPermissionUtils.canUserLimitedReadInventoryRecord(record, user));
  }

  private Optional<InventoryRecord> readableInventoryRecord(GlobalIdentifier base, User user) {
    return typeExactInventoryRecord(base)
        .filter(record -> inventoryPermissionUtils.canUserReadInventoryRecord(record, user));
  }

  private Optional<InventoryRecord> typeExactInventoryRecord(GlobalIdentifier base) {
    try {
      InventoryRecord record =
          inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(base);
      // samples and sample templates share one numeric id space and the retriever resolves both
      // SA and IT through the same lookup, so "IT90" can load sample SA90 (readableElnRecord
      // guards the same way); only a record whose own oid prefix matches the requested one is
      // the target
      if (record.getOid() == null || record.getOid().getPrefix() != base.getPrefix()) {
        return Optional.empty();
      }
      return Optional.of(record);
    } catch (NotFoundException e) {
      return Optional.empty();
    }
  }

  private boolean isElnReadable(GlobalIdentifier target, User user) {
    return readableElnRecord(target, user).isPresent();
  }

  private Optional<BaseRecord> liveReadableElnRecord(GlobalIdentifier target, User user) {
    return readableElnRecord(target, user).filter(record -> !record.isDeleted());
  }

  /**
   * Looked up with {@code getSafeNull}, never the permission-checking getters: those throw from a
   * transactional FolderManager for a missing, unreadable or trashed folder, which marks the
   * caller's transaction rollback-only even though the exception is caught, and callers carry on
   * after a false inside a write transaction (a skipped PIDINST import entry, RSDEV-1528).
   */
  private Optional<BaseRecord> readableElnRecord(GlobalIdentifier target, User user) {
    return baseRecordManager
        .getSafeNull(target.getDbId())
        // the loader resolves by numeric id alone, so a typed id can load a different record kind
        // sharing the number (e.g. "GL150" loads folder FL150): only a record whose own oid prefix
        // matches the requested one counts as the link target
        .filter(
            record -> record.getOid() != null && record.getOid().getPrefix() == target.getPrefix())
        // the rule FolderManager.getFolder applies: a trashed folder or notebook is no target
        .filter(record -> !record.isFolder() || !isDeletedFolder(record, user))
        // filter, not isPermitted: isPermitted lets anyone READ a published record, which never
        // made it a link target for them
        .filter(
            record ->
                !permissionUtils
                    .filter(new ArrayList<>(List.of(record)), PermissionType.READ, user)
                    .isEmpty());
  }

  private static boolean isDeletedFolder(BaseRecord folder, User user) {
    return folder.isDeleted()
        || folder.isDeletedForUser(folder.getOwner())
        || folder.isDeletedForUser(user);
  }
}

package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.service.inventory.DataCiteRelationType;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.service.inventory.InventoryLinkValidator;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.LinkTargetResolver;
import com.researchspace.service.inventory.LinkTargetSnapshotResolver;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryLinkManagerImpl implements InventoryLinkManager {

  @Autowired private InventoryLinkDao linkDao;
  @Autowired private InventoryPermissionUtils permissionUtils;
  @Autowired private LinkTargetResolver linkTargetResolver;
  @Autowired private LinkTargetSnapshotResolver snapshotResolver;

  @Override
  public InventoryLink createLink(ApiInventoryLink apiLink, User actor) {
    validateForWrite(apiLink);
    assertTargetExistsAndReadable(apiLink, actor);
    InventoryLink entity = new InventoryLink();
    applyApiToEntity(apiLink, entity);
    return linkDao.save(entity);
  }

  @Override
  public InventoryLink updateLink(InventoryLink existing, ApiInventoryLink apiLink, User actor) {
    validateForWrite(apiLink);
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
    GlobalIdentifier target;
    try {
      target = new GlobalIdentifier(targetGlobalId);
    } catch (IllegalArgumentException ex) {
      // a malformed Global ID does not resolve to any target; surface it as a clean API error
      // rather than letting the raw IllegalArgumentException escape to the HTTP layer
      throw new ApiRuntimeException("errors.inventory.field.link.targetNotFound", targetGlobalId);
    }
    // the caller must be able to READ the target itself: filtering the sources alone would
    // still confirm an unreadable record's existence and reveal its inbound links to anyone
    // who guesses its id. Same error as the malformed/missing case, so nothing is disclosed.
    if (!linkTargetResolver.targetExistsAndIsReadable(target, actor)) {
      throw new ApiRuntimeException("errors.inventory.field.link.targetNotFound", targetGlobalId);
    }
    List<ApiInventoryReferencingItem> rows = new ArrayList<>();
    for (ExtraLinkField field :
        linkDao.findReferencingLinkFields(target.getPrefix(), target.getDbId())) {
      addRowIfReadable(rows, field.getInventoryRecord(), field.getLink(), actor);
    }
    // template-defined structured link fields are first-class links too
    for (InventoryLinkField field :
        linkDao.findReferencingStructuredLinkFields(target.getPrefix(), target.getDbId())) {
      addRowIfReadable(rows, field.getInventoryRecord(), field.getLink(), actor);
    }
    return rows;
  }

  private void addRowIfReadable(
      List<ApiInventoryReferencingItem> rows,
      InventoryRecord parent,
      InventoryLink link,
      User actor) {
    if (parent == null || link == null || parent.isDeleted()) {
      return;
    }
    if (!permissionUtils.canUserReadInventoryRecord(parent, actor)) {
      return;
    }
    ApiInventoryReferencingItem row = new ApiInventoryReferencingItem();
    row.setSourceGlobalId(parent.getOid().toString());
    row.setSourceName(parent.getName());
    row.setSourceType(parent.getType().toString());
    row.setRelationType(link.getRelationType());
    row.setVersionPin(link.getVersionPin());
    if (link.getModifiedAt() != null) {
      row.setModifiedAtMillis(link.getModifiedAt().getTime());
    }
    rows.add(row);
  }

  @Override
  public ApiInventoryLinkTargetSummary getTargetSummary(String targetGlobalId, User actor) {
    GlobalIdentifier gid = parseAllowedTargetOrThrow(targetGlobalId);
    // current state of the base record: pin/revision deliberately null so the
    // "Target deleted" pill reflects the record as it is now, not as pinned
    return snapshotResolver.resolveSummary(gid.getPrefix(), gid.getDbId(), null, null, actor);
  }

  /**
   * Rejects links whose target does not resolve to a real record the actor can READ. Applies to all
   * targets, Inventory and ELN alike. The version suffix (if any) is ignored: only the base record
   * needs to exist and be readable.
   */
  /**
   * Structural validation of an incoming link payload, mirroring {@link InventoryLinkValidator}.
   * The controller-layer validator can be bypassed (an extra-field update payload that omits {@code
   * type} skips the LINK validation branch, and structured sample fields never run it), so the
   * manager enforces the same rules at the single write path: a parseable target id, an allowed
   * target kind, and a DataCite relation type. Failures map to 422 with the same bundle keys the
   * validator uses.
   */
  private void validateForWrite(ApiInventoryLink apiLink) {
    parseAllowedTargetOrThrow(apiLink.getTargetGlobalId());
    if (!DataCiteRelationType.isValid(apiLink.getRelationType())) {
      throw new ApiRuntimeException(
          "errors.inventory.field.link.relationTypeInvalid", apiLink.getRelationType());
    }
  }

  /**
   * Parses a target Global ID and asserts its prefix is an allowed link target kind, mapping
   * failures to the same 422 i18n errors on both the write path and the summary read path.
   */
  private GlobalIdentifier parseAllowedTargetOrThrow(String targetGlobalId) {
    GlobalIdentifier gid;
    try {
      gid = new GlobalIdentifier(targetGlobalId);
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new ApiRuntimeException("errors.inventory.field.link.targetNotFound", targetGlobalId);
    }
    if (!InventoryLinkValidator.isAllowedTargetPrefix(gid.getPrefix())) {
      throw new ApiRuntimeException(
          "errors.inventory.field.link.targetKindUnsupported", gid.getPrefix().name());
    }
    return gid;
  }

  private void assertTargetExistsAndReadable(ApiInventoryLink apiLink, User actor) {
    GlobalIdentifier gid = new GlobalIdentifier(apiLink.getTargetGlobalId());
    if (!linkTargetResolver.targetExistsAndIsReadable(gid, actor)) {
      throw new ApiRuntimeException(
          "errors.inventory.field.link.targetNotFound", apiLink.getTargetGlobalId());
    }
  }

  private void applyApiToEntity(ApiInventoryLink api, InventoryLink entity) {
    entity.setRelationType(api.getRelationType());
    GlobalIdentifier gid = new GlobalIdentifier(api.getTargetGlobalId());
    // persist the unsuffixed base id: the version lives in versionPin, so a
    // "vN" suffix on the incoming id must not be doubly encoded in the row
    entity.setTargetGlobalId(new GlobalIdentifier(gid.getPrefix(), gid.getDbId()).getIdString());
    entity.setTargetPrefix(gid.getPrefix());
    entity.setTargetDbId(gid.getDbId());
    Long versionPin = gid.hasVersionId() ? gid.getVersionId() : api.getVersionPin();
    entity.setVersionPin(versionPin);
    // Capture the exact audit revision the pinned version maps to. Null version means "latest",
    // which the snapshot resolver resolves dynamically at read time, so no revision is stored.
    Long revisionId =
        versionPin == null
            ? null
            : snapshotResolver.resolveRevisionForVersion(
                gid.getPrefix(), gid.getDbId(), versionPin);
    entity.setTargetRevisionId(revisionId);
  }
}

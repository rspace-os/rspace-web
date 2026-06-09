package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.service.inventory.LinkTargetResolver;
import com.researchspace.service.inventory.LinkTargetSnapshotResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Resolves Inventory link targets through the Envers audit tables. See {@link
 * LinkTargetSnapshotResolver}. Not transactional itself; invoked within the transaction of the
 * calling {@code *Manager} service.
 */
@Component("linkTargetSnapshotResolver")
public class LinkTargetSnapshotResolverImpl implements LinkTargetSnapshotResolver {

  @Autowired private AuditManager auditManager;
  @Autowired private LinkTargetResolver linkTargetResolver;

  @Override
  public Long resolveRevisionForVersion(GlobalIdPrefix prefix, Long dbId, Long version) {
    if (prefix == null || version == null) {
      return null;
    }
    Number revision = null;
    if (prefix == GlobalIdPrefix.SD) {
      revision = auditManager.getRevisionNumberForDocumentVersion(dbId, version);
    } else if (prefix == GlobalIdPrefix.GL) {
      revision = auditManager.getRevisionNumberForMediaFileVersion(dbId, version);
    } else if (isInventoryPrefix(prefix)) {
      revision =
          auditManager.getRevisionNumberForInventoryRecordVersion(
              entityClassFor(prefix), dbId, version);
    }
    return revision == null ? null : revision.longValue();
  }

  @Override
  public ApiInventoryLinkTargetSummary resolveSummary(
      GlobalIdPrefix prefix, Long dbId, Long versionPin, Long targetRevisionId, User user) {
    ApiInventoryLinkTargetSummary summary = new ApiInventoryLinkTargetSummary();
    if (prefix == null || dbId == null) {
      return summary;
    }
    summary.setGlobalId(buildGlobalId(prefix, dbId, versionPin));

    Class<?> cls = entityClassFor(prefix);
    if (cls == null) {
      return summary;
    }
    AuditedEntity<?> snapshot =
        targetRevisionId != null
            ? auditManager.getObjectForRevision(cls, dbId, targetRevisionId)
            : auditManager.getNewestRevisionForEntity(cls, dbId);
    if (snapshot == null || snapshot.getEntity() == null) {
      return summary;
    }
    Object entity = snapshot.getEntity();
    if (!isReadable(prefix, dbId, entity, user)) {
      // Redacted: globalId only, never disclose name/type of a target the actor cannot read.
      return summary;
    }
    summary.setType(typeFor(prefix));
    summary.setName(nameOf(entity));
    summary.setDeleted(deletedOf(entity));
    return summary;
  }

  /**
   * Live read permission, falling back to snapshot ownership when only audit data remains (hard
   * delete). Soft-deleted targets keep a live row, so they pass the live check.
   */
  private boolean isReadable(GlobalIdPrefix prefix, Long dbId, Object entity, User user) {
    GlobalIdentifier baseGid = new GlobalIdentifier(prefix, dbId);
    if (linkTargetResolver.targetExistsAndIsReadable(baseGid, user)) {
      return true;
    }
    // Fallback: the live record is gone (hard-deleted), so no live permission check is possible.
    // Intentionally restrict the audit-only snapshot to its original owner; every other user
    // (including admins, who have no live grant on a destroyed record) is denied here.
    User owner = ownerOf(entity);
    return owner != null
        && user != null
        && owner.getUsername() != null
        && owner.getUsername().equals(user.getUsername());
  }

  private String buildGlobalId(GlobalIdPrefix prefix, Long dbId, Long versionPin) {
    String base = prefix.name() + dbId;
    return versionPin == null ? base : base + "v" + versionPin;
  }

  private boolean isInventoryPrefix(GlobalIdPrefix prefix) {
    return prefix == GlobalIdPrefix.SA
        || prefix == GlobalIdPrefix.SS
        || prefix == GlobalIdPrefix.IC
        || prefix == GlobalIdPrefix.IN
        || prefix == GlobalIdPrefix.IT;
  }

  private Class<?> entityClassFor(GlobalIdPrefix prefix) {
    switch (prefix) {
      case SA:
        return Sample.class;
      case SS:
        return SubSample.class;
      case IC:
        return Container.class;
      case IN:
        return Instrument.class;
      case IT:
        // Sample templates are persisted as Sample rows (treated as SA elsewhere).
        return Sample.class;
      case SD:
        return StructuredDocument.class;
      case NB:
        return Notebook.class;
      case GL:
        return EcatMediaFile.class;
      default:
        return null;
    }
  }

  private String typeFor(GlobalIdPrefix prefix) {
    switch (prefix) {
      case SA:
        return "SAMPLE";
      case SS:
        return "SUBSAMPLE";
      case IC:
        return "CONTAINER";
      case IN:
        return "INSTRUMENT";
      case IT:
        return "SAMPLE";
      case SD:
        return "DOCUMENT";
      case NB:
        return "NOTEBOOK";
      case GL:
        return "MEDIA_FILE";
      default:
        return null;
    }
  }

  private String nameOf(Object entity) {
    if (entity instanceof InventoryRecord) {
      return ((InventoryRecord) entity).getName();
    }
    if (entity instanceof BaseRecord) {
      return ((BaseRecord) entity).getName();
    }
    return null;
  }

  private boolean deletedOf(Object entity) {
    if (entity instanceof InventoryRecord) {
      return ((InventoryRecord) entity).isDeleted();
    }
    if (entity instanceof BaseRecord) {
      return ((BaseRecord) entity).isDeleted();
    }
    return false;
  }

  private User ownerOf(Object entity) {
    if (entity instanceof InventoryRecord) {
      return ((InventoryRecord) entity).getOwner();
    }
    if (entity instanceof BaseRecord) {
      return ((BaseRecord) entity).getOwner();
    }
    return null;
  }
}

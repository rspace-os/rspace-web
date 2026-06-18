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
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
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
    // All three branches use the non-throwing "find" lookups: capturing the pinned revision is
    // best-effort (applyApiToEntity stores null = "resolve latest at read time" when a version has
    // no audit row), so an unresolved version must degrade to null rather than throw. The throwing
    // getRevisionNumberFor{Document,MediaFile}Version variants would, crossing the transactional
    // AuditManager boundary, mark the caller's transaction rollback-only and fail the whole save.
    if (prefix == GlobalIdPrefix.SD) {
      revision = auditManager.findRevisionNumberForDocumentVersion(dbId, version);
    } else if (prefix == GlobalIdPrefix.GL) {
      revision = auditManager.findRevisionNumberForMediaFileVersion(dbId, version);
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
    summary.setReadable(true);
    summary.setType(typeFor(prefix));
    summary.setName(nameOf(entity));
    summary.setDeleted(deletedOf(entity));
    return summary;
  }

  /**
   * Read permission for the target snapshot. The snapshot owner is checked first because a user can
   * always read their own record, deleted or not, and that check cannot throw. This matters for a
   * soft-deleted folder/notebook: the live readability lookup loads the folder with
   * includeDeleted=false and throws (the document branch returns deleted records, but the folder
   * branch does not). Because that lookup runs through a transactional {@code *Manager}, the throw
   * marks the summary's transaction rollback-only even though it is caught, 500ing the summary
   * endpoint so the link card shows no "Target deleted" pill and keeps Open. Short-circuiting on
   * the owner avoids the throwing call for the owner (who deleted the notebook in the reported
   * case). Non-owners still go through the live check, which also covers shared, still-live
   * targets.
   */
  private boolean isReadable(GlobalIdPrefix prefix, Long dbId, Object entity, User user) {
    User owner = ownerOf(entity);
    if (owner != null
        && user != null
        && owner.getUsername() != null
        && owner.getUsername().equals(user.getUsername())) {
      return true;
    }
    GlobalIdentifier baseGid = new GlobalIdentifier(prefix, dbId);
    return linkTargetResolver.targetExistsAndIsReadable(baseGid, user);
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
        return SampleEntity.class;
      case SS:
        return SubSample.class;
      case IC:
        return Container.class;
      case IN:
        return Instrument.class;
      case IT:
        // Sample templates are now a SampleEntity subtype (DTYPE discriminator) on the shared
        // Sample table; resolve via SampleEntity so Envers returns the concrete SampleTemplate
        // revision (querying Sample.class would filter to DTYPE='Sample' and miss templates).
        return SampleEntity.class;
      case SD:
        return StructuredDocument.class;
      case NB:
        // notebooks are persisted as Folder rows; Envers audits Folder, not
        // Notebook (which is not a separately-mapped entity). Querying
        // Notebook.class throws (NotAuditedException), which crosses the
        // transactional getTargetSummary boundary and 500s the summary, leaving
        // the link card with no "Target deleted" pill and Open still shown.
        return Folder.class;
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

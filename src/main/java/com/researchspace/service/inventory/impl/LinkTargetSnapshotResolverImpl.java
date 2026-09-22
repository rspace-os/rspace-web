package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.service.inventory.LinkTargetResolver;
import com.researchspace.service.inventory.LinkTargetSnapshotResolver;
import java.util.Optional;
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
  @Autowired private IPermissionUtils permissionUtils;

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
      // No audit history does not mean no record: rows can be purged while the record lives on.
      // Report the live record's own state, so a trashed Inventory item reads exactly as it does
      // with a snapshot - deleted, but readable and openable in the trash - rather than being
      // hidden behind "No access". The lookup is permission-gated and type-exact, so an
      // unreadable record and a sibling sharing the db id (SA/IT) both fall through to redaction.
      //
      // Inventory only. The ELN lookup resolves through transactional *Manager proxies
      // (BaseRecordManager -> FolderManager) whose folderDao.get and read/not-deleted assertions
      // throw for a missing, unreadable or deleted record. Catching the throw is not enough: it
      // crosses a transactional proxy and marks the caller's transaction rollback-only, so
      // getTargetSummary would fail at commit with UnexpectedRollbackException instead of
      // returning the redacted summary below - the same trap isReadable's javadoc describes, and
      // reachable here because import can store a link to a notebook that does not exist. The
      // inventory lookup throws only its own NotFoundException from a non-transactional
      // component, so it is safe to consult.
      if (isInventoryPrefix(prefix)) {
        Optional<InventoryRecord> liveTarget =
            linkTargetResolver.readableInventoryTarget(new GlobalIdentifier(prefix, dbId), user);
        if (liveTarget.isPresent()) {
          summary.setReadable(true);
          summary.setType(typeFor(prefix));
          summary.setName(nameOf(liveTarget.get()));
          summary.setDeleted(deletedOf(liveTarget.get()));
          return summary;
        }
      }
      // Otherwise redacted, for every prefix. Nonexistent must look exactly like unreadable
      // (ADR-0002), so the card says "No access" either way and a caller walking ids learns
      // nothing about which records are there.
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
   * always read their own record, deleted or not, and that check cannot throw.
   *
   * <p>For a non-owner, ELN targets are decided from the snapshot itself rather than by reloading
   * the record. The live lookup runs BaseRecordManager -> FolderManager, whose folderDao.get,
   * assertUserHasReadPermission and assertNotDeleted all throw, and all sit behind transactional
   * {@code *Manager} proxies: catching the exception is not enough, because the summary's
   * transaction is already marked rollback-only and getTargetSummary then fails at commit with
   * UnexpectedRollbackException. The card, having got no summary at all, renders no pill and leaves
   * Open enabled, so the endpoint fails open. Both an unreadable notebook (the permission assertion
   * throws) and a readable soft-deleted one (the not-deleted assertion throws) hit this. {@link
   * IPermissionUtils#isPermitted} evaluates the same READ grant against the snapshot with no
   * manager call, so nothing can poison the transaction. It reads sharing as the snapshot captured
   * it, which is the deliberate trade for an endpoint that must not 500.
   *
   * <p>Inventory targets keep the live check: their lookup throws only its own NotFoundException,
   * from non-transactional components, so it is safe to consult and reflects current sharing.
   */
  private boolean isReadable(GlobalIdPrefix prefix, Long dbId, Object entity, User user) {
    User owner = ownerOf(entity);
    if (owner != null
        && user != null
        && owner.getUsername() != null
        && owner.getUsername().equals(user.getUsername())) {
      return true;
    }
    if (!isInventoryPrefix(prefix)) {
      return entity instanceof BaseRecord baseRecord
          && permissionUtils.isPermitted(baseRecord, PermissionType.READ, user);
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
        || prefix == GlobalIdPrefix.IT
        || prefix == GlobalIdPrefix.NT;
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
        // a template is a distinct SampleTemplate entity (DTYPE='SampleTemplate'); query that
        // concrete class so Envers resolves the template revision (Sample.class would filter to
        // DTYPE='Sample' and miss it).
        return SampleTemplate.class;
      case NT:
        // like IT: a distinct single-table subtype (DTYPE='InstrumentTemplate'); query the
        // concrete class so Envers resolves the template revision (Instrument.class would filter
        // to DTYPE='Instrument' and miss it).
        return InstrumentTemplate.class;
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
      case NT:
        return "INSTRUMENT_TEMPLATE";
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

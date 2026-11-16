package com.researchspace.model.inventory.field;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.netfiles.NfsFieldLink;
import com.researchspace.model.netfiles.NfsFileStore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.io.Serializable;
import java.util.Date;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;

@Entity
@Audited
@Getter
@Setter
@EqualsAndHashCode(of = {"id"})
public class InventoryLink implements Serializable {

  private static final long serialVersionUID = 1L;

  public static final int MAX_PATH_LENGTH = 1024;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // a row holds either a Global ID target or a Filestore item target, never both (ADR 0016)
  @Column(name = "target_globalid", length = 64)
  private String targetGlobalId;

  @Column(name = "target_prefix", length = 8)
  @Enumerated(EnumType.STRING)
  private GlobalIdPrefix targetPrefix;

  @Column(name = "target_db_id")
  private Long targetDbId;

  // plain ids, no foreign keys: deleting a user hard-deletes their filestores first, and a link on
  // someone else's item must survive that, as a link to a deleted Global ID target does
  @Column(name = "filestore_id")
  private Long filestoreId;

  @Column(name = "target_remote_path", length = MAX_PATH_LENGTH)
  private String targetRemotePath;

  @Column(name = "target_filesystem_id")
  private Long targetFileSystemId;

  @Column(name = "target_absolute_path", length = MAX_PATH_LENGTH)
  private String targetAbsolutePath;

  @Column(name = "version_pin")
  private Long versionPin;

  /**
   * The Envers revision (REV) this link's pin resolved to, captured at pin time. Null means the
   * link is "latest" and resolves dynamically to the newest revision in the target's audit table.
   * Paired with {@link #versionPin}: versionPin is the user-facing version for display, this is the
   * exact audit-row key used to load the snapshot.
   */
  @Column(name = "target_revision_id")
  private Long targetRevisionId;

  @Column(name = "relation_type", nullable = false, length = 64)
  private String relationType;

  @Column(name = "created_at", nullable = false)
  @Temporal(TemporalType.TIMESTAMP)
  private Date createdAt;

  @Column(name = "modified_at", nullable = false)
  @Temporal(TemporalType.TIMESTAMP)
  private Date modifiedAt;

  @Column(nullable = false)
  private boolean deleted;

  @PrePersist
  void prePersist() {
    Date now = new Date();
    if (createdAt == null) {
      createdAt = now;
    }
    modifiedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    modifiedAt = new Date();
  }

  /**
   * The stored form of a path inside a filestore: a leading slash and the path normalised by the
   * rule of the document link index, which keeps inner slashes (ADR 0015). A missing or blank path
   * gives {@code "/"}, the filestore's own root, which names no item.
   */
  public static String canonicalRemotePath(String remotePath) {
    String relative = NfsFieldLink.normalisePath(remotePath);
    return "/" + (StringUtils.isBlank(relative) ? "" : relative);
  }

  public boolean isFilestoreTarget() {
    return filestoreId != null;
  }

  /**
   * Points this link at an item inside a filestore, deriving the filesystem and the normalised
   * absolute path, and clears any Global ID target with its pin and revision.
   */
  public void setFilestoreTarget(NfsFileStore store, String remotePath) {
    filestoreId = store.getId();
    targetRemotePath = canonicalRemotePath(remotePath);
    targetFileSystemId = store.getFileSystem().getId();
    targetAbsolutePath = NfsFieldLink.normalisePath(store.getAbsolutePath(targetRemotePath));
    targetGlobalId = null;
    targetPrefix = null;
    targetDbId = null;
    versionPin = null;
    targetRevisionId = null;
  }

  /**
   * Points this link at a record by its unversioned Global ID and clears any Filestore item target.
   * The version pin and its revision are left to the caller.
   */
  public void setGlobalIdTarget(GlobalIdentifier base) {
    targetGlobalId = base.getIdString();
    targetPrefix = base.getPrefix();
    targetDbId = base.getDbId();
    filestoreId = null;
    targetRemotePath = null;
    targetFileSystemId = null;
    targetAbsolutePath = null;
  }

  /**
   * Creates a new, unpersisted copy of this link carrying the same target identity, version pin,
   * relation type and deletion state. The copy has a null id and no timestamps (those are set on
   * persist), so it can be attached to a copied field without sharing this managed instance.
   */
  public InventoryLink shallowCopy() {
    InventoryLink copy = new InventoryLink();
    copy.setTargetGlobalId(targetGlobalId);
    copy.setTargetPrefix(targetPrefix);
    copy.setTargetDbId(targetDbId);
    copy.setVersionPin(versionPin);
    copy.setTargetRevisionId(targetRevisionId);
    copy.setFilestoreId(filestoreId);
    copy.setTargetRemotePath(targetRemotePath);
    copy.setTargetFileSystemId(targetFileSystemId);
    copy.setTargetAbsolutePath(targetAbsolutePath);
    copy.setRelationType(relationType);
    copy.setDeleted(deleted);
    return copy;
  }
}

package com.researchspace.model.record;

import static com.researchspace.model.RecordGroupSharing.ANONYMOUS_USER;
import static java.util.stream.Collectors.toCollection;

import com.researchspace.core.util.CollectionFilter;
import com.researchspace.core.util.IDescribable;
import com.researchspace.model.Group;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.TaggableElnRecord;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.AbstractEntityPermissionAdapter;
import com.researchspace.model.permissions.RecordPermissionAdapter;
import com.researchspace.model.permissions.RecordSharingACL;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.LazyInitializationException;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;

/**
 * Base class of Record/Folder types. Holds provenance information and parent relations. This class
 * defines equals/hashcode to be a property of its creation time and creator, which should be
 * immutable once set. Subclasses probably <b>shouldn't</b> override equals/hashcode, to avoid
 * problems with transitivity.
 */
@Audited
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@AuditTrailData(auditDomain = AuditDomain.RECORD)
@Table(indexes = {@Index(columnList = "deleted", name = "isDeleted")})
public abstract class BaseRecord
    implements Comparable<BaseRecord>,
        Serializable,
        IFieldLinkableElement,
        PermissionsAdaptable,
        IRSpaceDoc,
        IDescribable {

  /** Comparator used to order a base record list by name (asc/desc). */
  public static final Comparator<BaseRecord> NAME_COMPARATOR =
      new Comparator<>() {
        @Override
        public int compare(BaseRecord baseRecord1, BaseRecord baseRecord2) {
          return baseRecord1.getName().toLowerCase().compareTo(baseRecord2.getName().toLowerCase());
        }
      };

  /** Comparator used to order base record list by creation date (asc/desc). */
  public static final Comparator<BaseRecord> CREATION_DATE_COMPARATOR =
      new Comparator<>() {
        @Override
        public int compare(BaseRecord br1, BaseRecord br2) {
          return br1.getCreationDateMillis().compareTo(br2.getCreationDateMillis());
        }
      };

  /** Comparator used to order a base record list by modification date (asc/desc). */
  public static final Comparator<BaseRecord> MODIFICATION_DATE_COMPARATOR =
      new Comparator<>() {
        @Override
        public int compare(BaseRecord baseRecord1, BaseRecord baseRecord2) {
          return baseRecord1.getModificationDate().compareTo(baseRecord2.getModificationDate());
        }
      };

  /** Default DB varchar length for input validation */
  public static final int DEFAULT_VARCHAR_LENGTH = 255;

  /** testing on jsp */
  @Transient public static final String TEMPLATE_TYTE_EXT = "templatex";

  /** */
  public static final String LINEAGE_DELIMITER = "|";

  /** Default filter does no filtering - always returns <code>true</code>. */
  protected static final CollectionFilter<BaseRecord> DEFAULT_FILTER = baseRecord -> true;

  private static final FolderTraversalTerminator NULL_TERMINATOR = (current, child) -> false;

  /** */
  private static final long serialVersionUID = -532434205358457270L;

  private static final String TYPE_DELIMITER = ":";
  protected Set<RecordToFolder> parents = new HashSet<>();
  protected boolean deleted = false;
  private boolean fromImport;
  private String originalCreatorUsername;
  private Long id; // database id
  private RecordSharingACL sharingACL;
  private EditInfo editInfo;
  private User owner;
  private String type;
  private Date deletedDate;
  private boolean signed = false;
  private boolean witnessed = false;
  private Long iconId = -1L;
  private SharedStatus sharedStatus = SharedStatus.UNSHARED;
  private FavoritesStatus favoriteStatus;

  public BaseRecord() {
    init();
    setCreationDate(new Date());
    setModificationDate(new Date());
  }

  /**
   * Constructor to preserve some original timestamp data when creating from imports
   *
   * @param override
   */
  public BaseRecord(ImportOverride override) {
    init();
    setCreationDate(new Date(override.getCreated().toEpochMilli()));
    setModificationDate(override.getLastModified().toEpochMilli());
    this.fromImport = true;
    this.originalCreatorUsername = override.getOriginalCreatorUsername();
  }

  /**
   * Utility method to construct a compositeID that is used in the Gallery and in editor and in rich
   * text fields.
   *
   * @param record
   * @param fieldId
   * @return A String in the syntax: <code>fieldId-objectId</code>
   */
  public static String getCompositeId(IRSpaceDoc record, Long fieldId) {
    return fieldId + "-" + record.getId();
  }

  /**
   * Is not null only if this record was created from an import, and the previous ownership is
   * retained.
   *
   * @return
   */
  @Column(nullable = true, length = User.MAX_UNAME_LENGTH)
  public String getOriginalCreatorUsername() {
    return originalCreatorUsername;
  }

  // for hibernate, must be immutable
  @SuppressWarnings("unused")
  private void setOriginalCreatorUsername(String originalCreator) {
    this.originalCreatorUsername = originalCreator;
  }

  /**
   * Whether this record was imported from an export(true) or was created new (false)
   *
   * @return
   */
  public boolean isFromImport() {
    return fromImport;
  }

  // for hibernate; should be immutable
  @SuppressWarnings("unused")
  private void setFromImport(boolean fromImport) {
    this.fromImport = fromImport;
  }

  private void init() {
    setEditInfo(new EditInfo());
    setSharingACL(new RecordSharingACL()); // empty to begin with
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long getId() {
    return id;
  }

  /**
   * Should not normally be called, used by hibernate, but is public for testing access and for
   * handling temporary records.
   *
   * @param id
   */
  public void setId(Long id) {
    this.id = id;
  }

  @Transient
  public GlobalIdentifier getOid() {
    return new GlobalIdentifier(getGlobalIdPrefix(), getId());
  }

  @AuditTrailIdentifier
  @Transient
  // include in 'FullText' search
  public String getGlobalIdentifier() {
    return getOid().toString();
  }

  @Transient
  @FullTextField(analyzer = "structureAnalyzer", name = "fields_fieldData")
  @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "editInfo")))
  public String getSearchableContent() {
    // Combine globalIdentifier and description for full-text search
    String oid = getGlobalIdentifier();
    String desc = getDescription();
    StringBuilder sb = new StringBuilder();
    if (oid != null) {
      sb.append(oid);
    }
    if (desc != null) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(desc);
    }
    return sb.toString();
  }

  @Transient
  protected abstract GlobalIdPrefix getGlobalIdPrefix();

  /**
   * Helper method that checks if the record matches provided global id.
   *
   * @param oid
   * @return
   */
  @Transient
  public boolean isIdentifiedByOid(GlobalIdentifier oid) {
    if (oid == null) {
      return false;
    }
    if (oid.hasVersionId()) {
      if (isStructuredDocument() || isMediaRecord()) {
        return oid.equals(((Record) this).getOidWithVersion());
      }
    }
    return oid.equals(getOid());
  }

  @Embedded
  public RecordSharingACL getSharingACL() {
    if (this.sharingACL == null) {
      this.sharingACL = new RecordSharingACL();
    }
    return sharingACL;
  }

  public void setSharingACL(RecordSharingACL sharingACL) {
    this.sharingACL = sharingACL;
  }

  /** Boolean test for whether this {@link Record} is a StructuredDocument. */
  @Transient
  public boolean isStructuredDocument() {
    return false;
  }

  /** Boolean test for whether this record is a Folder. */
  @Transient
  public boolean isFolder() {
    return false;
  }

  /** Boolean test for whether this record is a Notebook. */
  @Transient
  public boolean isNotebook() {
    return false;
  }

  /** Boolean test for whether this record is a Snippet. */
  @Transient
  public boolean isSnippet() {
    return false;
  }

  /**
   * Boolean test for whether this record is a Notebook entry - i.e., it has at least 1 parent
   * folder that is a notebook.
   */
  @Transient
  public boolean isNotebookEntry() {
    return false;
  }

  /**
   * Any attachment type (Image, Document, AV) that is stored in the Gallery returns <code>true
   * </code>
   */
  @Transient
  public boolean isMediaRecord() {
    return false;
  }

  @Transient
  public boolean isEcatDocument() {
    return false;
  }

  @Transient
  public boolean isTaggable() {
    return this instanceof TaggableElnRecord;
  }

  @Transient
  public abstract Set<BaseRecord> getChildrens();

  /**
   * Returns the single parent, if this folder does just have one parent. This is until we have
   * added support for shared folders
   *
   * @return the single parent, or <code>null</code> if has multiple parents.
   */
  @Transient
  public Folder getParent() {
    if (hasSingleParent()) {
      return getSingleParent();
    }
    return null;
  }

  @Transient
  public Optional<Folder> getSharedFolderParent() {
    for (RecordToFolder rf : parents) {
      Folder testee = rf.getFolder();
      if (testee.getType().equals("FOLDER:SHARED_FOLDER")) {
        return Optional.of(testee);
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the parent folder of this record, where the parent folder is descended from the
   * record's owner's root folder. This method is useful when a record has been shared (and has
   * multiple parents), and you want to find the parent folder of the record's owner in their 'home'
   * folder tree.
   *
   * @return the parent folder, or <code>Optional.empty</code> if no owner parent exists - e.g., if
   *     a folder is not yet attached to the folder tree, or is a root folder, or all parents are
   *     shared folder.
   */
  @Transient
  public Optional<Folder> getOwnerParent() {
    return getParentFolders().stream().filter(f -> !f.isSharedFolder()).findFirst();
  }

  /**
   * This does the opposite to `getOwnerParent`
   *
   * @return the parent folder of this record if that folder is the group shared folder of one or
   *     more of the users groups, else null.
   */
  @Transient
  public Optional<Folder> getSharedParentForUser(User u) {
    List<Long> sharedFolderIDs = new ArrayList<>();
    for (Group aGroup : u.getGroups()) {
      sharedFolderIDs.add(aGroup.getCommunalGroupFolderId());
      sharedFolderIDs.add(aGroup.getSharedSnippetGroupFolderId());
    }
    return getParentFolders().stream()
        .filter(f -> f.isSharedFolder() && sharedFolderIDs.contains(f.getId()))
        .findFirst();
  }

  /**
   * @return `getOwnerParent` if that Folder belongs to User u, else returns
   *     `getSharedParentForUser` if that folder belongs to User u, else returns null
   */
  @Transient
  public Optional<Folder> getOwnerOrSharedParentForUser(User u) {
    return getOwnerParent()
        .filter(folder -> folder.getOwner().equals(u))
        .or(() -> getSharedParentForUser(u));
  }

  public abstract BaseRecord copy();

  /** Convenience method to return the parent folder(s) of this object. */
  @Transient
  public Set<Folder> getParentFolders() {
    return parents.stream()
        .map(RecordToFolder::getFolder)
        .collect(toCollection(() -> new HashSet<>()));
  }

  /** Convenience method to return the parent notebook(s) of this object. */
  @Transient
  public Set<Notebook> getParentNotebooks() {
    return parents.stream()
        .map(RecordToFolder::getFolder)
        .filter(BaseRecord::isNotebook)
        .map(f -> (Notebook) f)
        .collect(toCollection(() -> new HashSet<>()));
  }

  /**
   * GEts the collection of {@link RecordToFolder} objects that have this object as an immediate
   * child.
   *
   * @return a possibly empty but non-null Set of RecordToFolder
   */
  @NotAudited
  // CascadeType.PERSIST is required under Hibernate 6: Hibernate 5's saveOrUpdate() cascaded via
  // the Hibernate-specific SAVE_UPDATE type, persisting new RecordToFolder entries reachable via
  // this collection even without an explicit PERSIST cascade. Hibernate 6 is strict JPA and
  // requires PERSIST to be declared. Without it, code paths that add a new RecordToFolder only to
  // record.parents (via skipAddingToChildren=true) and then call save(record) would silently drop
  // the relationship (DocumentCopyManagerImpl, RecordSharingManagerImpl).
  @OneToMany(
      mappedBy = "record",
      fetch = FetchType.EAGER,
      cascade = {CascadeType.REFRESH, CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
  public Set<RecordToFolder> getParents() {
    return parents;
  }

  /**
   * For internal use by hibernate, or to manipulate detached objects to avoid JSON cycles.
   * Manipulating parent/child relations should be done through Folder add/remove operations.
   *
   * @param parents
   */
  public void setParents(Set<RecordToFolder> parents) {
    this.parents = parents;
  }

  /**
   * Gets the collection of {@link Folder} objects that have this object as a descendant.
   *
   * @return a possibly empty but non-nullList of Folder objects
   */
  @Transient
  public List<Folder> getAllAncestors() {
    List<Folder> ancestors = new ArrayList<>();
    doGetAllAncestors(ancestors);
    return ancestors;
  }

  protected void doGetAllAncestors(List<Folder> ancestors) {
    Set<Folder> flders = getFolders();
    for (Folder parent : flders) {
      if (!ancestors.contains(parent)) {
        ancestors.add(parent);
        parent.doGetAllAncestors(ancestors);
      }
    }
  }

  /**
   * Boolean test for whether this object has a parent folder or ancestor with the given record
   * type.
   *
   * @param type A {@link RecordType}
   * @param includeSelf include this object in type comparison. If <code>false</code>, this object
   *     is ignored.
   * @return <code>true</code> if this object ancestor of given type, <code>false</code> otherwise.
   */
  public boolean hasAncestorOfType(RecordType type, boolean includeSelf) {
    return hasAncestorMatchingPredicate(baseRecord -> baseRecord.hasType(type), includeSelf);
  }

  /**
   * Boolean test as to whether an ancestor of this object matches a given predicate
   *
   * @param predicate
   * @param includeSelf include this object in type comparison. If <code>false</code>, this object
   *     is ignored.
   * @return <code>true</code> if this object ancestor matches predicate, <code>false</code>
   *     otherwise.
   */
  public boolean hasAncestorMatchingPredicate(
      Predicate<BaseRecord> predicate, boolean includeSelf) {
    if (includeSelf && predicate.test(this)) {
      return true;
    }
    List<Folder> ancestors = getAllAncestors();
    for (Folder ancestor : ancestors) {
      if (predicate.test(ancestor)) {
        return true;
      }
    }
    return false;
  }

  /** Returns <code>true</code> if there is at least one parent of this object. */
  public boolean hasParents() {
    return parents.size() > 0;
  }

  /** Returns <code>true</code> if this object has only a single parent. */
  public boolean hasSingleParent() {
    return parents.size() == 1;
  }

  /** Convenience method to get all folders that this record belongs to */
  @Transient
  public Set<Folder> getFolders() {
    Set<Folder> rc = new HashSet<>();
    for (RecordToFolder rtf : getParents()) {
      rc.add(rtf.getFolder());
    }
    return rc;
  }

  /**
   * @return the single parent, if <code> hasSinglePArent()== true</code>, or <code>null</code> if
   *     there are no parents.
   * @throws IllegalStateException if has > 1 parent, i.e., if <code> hasSinglePArent()== false
   *     </code>
   */
  @Transient
  public Folder getSingleParent() {
    if (parents.size() > 1) {
      throw new IllegalStateException("there are " + parents.size() + " parents!");
    }
    if (!hasParents()) {
      return null;
    }
    return parents.iterator().next().getFolder();
  }

  /**
   * Moves this record <b>from</b> <code>from</code> <b>to</b> <code>to</code>
   *
   * @param from
   * @param to
   * @param u
   * @return
   * @throws IllegalAddChildOperation
   */
  public boolean move(Folder from, Folder to, User u) throws IllegalAddChildOperation {
    return doMove(from, to, u, false);
  }

  /**
   * Version of {@link #move(Folder, Folder, User)} method that allows illegal moves out of shared.
   * This variant should be only called in test setup, to reproduce historical issues like
   * RSDEV-796.
   */
  public boolean unsafeMove(Folder from, Folder to, User u) throws IllegalAddChildOperation {
    return doMove(from, to, u, true);
  }

  private boolean doMove(Folder from, Folder to, User u, boolean allowUnsafeMove)
      throws IllegalAddChildOperation {
    if (from == null || to == null || u == null) {
      return false;
    }
    if (!this.getParentFolders().contains(from)) {
      return false;
    }
    if (from.isNotebook() && !getOwner().equals(from.getOwner())) {
      return false;
    }
    if (!allowUnsafeMove) {
      if (isFolder() && ((Folder) this).isSystemFolder()) {
        return false;
      }
      if (from.isTopLevelSharedFolder()) {
        return false;
      }
      if (from.isSharedFolder() && !(to.isSharedFolder() || to.isNotebook())) {
        return false;
      }
      if (!from.isSharedFolder() && to.isSharedFolder()) {
        return false;
      }
      if (!from.isSharedFolder() && !from.getOwner().equals(to.getOwner())) {
        return false; // block move from user's Workspace folder into somebody else's
      }
    }

    boolean removed = from.removeChild(this);
    RecordToFolder added = to.addChild(this, u);
    return added != null && removed;
  }

  /**
   * Boolean test for whether this Record is a descendant of the argument Record.
   *
   * @param record A non-<code>null</code> Record
   * @return <code>true</code> if this record is a descendant of the other record, false otherwise
   *     or if the records are the same ( as determined by equals()).
   * @deprecated - use {@link #getParentHierarchyForUser(User)} and inspect the returned list of
   *     parents.
   */
  @Transient
  public boolean isDescendantOf(BaseRecord record) {
    if (this.equals(record)) {
      return false;
    }
    BaseRecord parent = getParent();
    while (parent != null) {
      if (parent.equals(record)) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  /**
   * Sets the values belonging to all Records: Name, Type, ACL, Owner. Ignores history and temp
   * files and comments
   *
   * @return
   */
  protected BaseRecord shallowCopyRecordInfo(BaseRecord copy) {
    EditInfo infoCopy = getEditInfo().shallowCopy();
    copy.setEditInfo(infoCopy);
    copy.setType(getType());
    copy.setSharingACL(getSharingACL().copy());
    copy.setOwner(getOwner());
    copy.setIconId(getIconId());

    return copy;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    if (id != null) {
      return id.hashCode();
    }
    // // use method calls in case is javaassist proxy object
    result = prime * result + ((getEditInfo() == null) ? 0 : getEditInfo().hashCode());
    result = prime * result + ((getType() == null) ? 0 : getType().hashCode());
    return result;
  }

  /** Equalit is based on DB -id if both have a DB id, else on creation Date / creator */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!getClass().isAssignableFrom(obj.getClass())) {
      return false;
    }
    BaseRecord other = (BaseRecord) obj;
    if (other.id != null && id != null) {
      return other.id.equals(id);
    }
    if (getEditInfo() == null) {
      if (other.getEditInfo() != null) {
        return false;
      }
    } else if (!getEditInfo().equals(other.getEditInfo())) {
      return false;
    }
    if (getType() == null) {
      return other.getType() == null;
    } else return getType().equals(other.getType());
  }

  @Transient
  @FullTextField(analyzer = "structureAnalyzer", name = "name")
  @AuditTrailProperty(name = "name")
  @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "editInfo")))
  public String getName() {
    return getEditInfo().getName();
  }

  /**
   * @param name
   * @throws IllegalArgumentException if <code>name </code> is empty
   */
  public void setName(String name) {
    getEditInfo().setName(name);
  }

  @Embedded
  public EditInfo getEditInfo() {
    return this.editInfo;
  }

  // just for hibernate
  public void setEditInfo(EditInfo editInfo) {
    this.editInfo = editInfo;
  }

  @Transient
  // @SearchableProperty
  public String getCreatedBy() {
    return getEditInfo().getCreatedBy();
  }

  public void setCreatedBy(String createdBy) {
    getEditInfo().setCreatedBy(createdBy);
  }

  /**
   * To be called when there is the possibility that the user could be operating as another user and
   * so may need a strategy to alter this.
   *
   * @param modifiedBy
   * @param modifyByStategy
   */
  public void setModifiedBy(String modifiedBy, IActiveUserStrategy modifyByStategy) {
    modifiedBy = modifyByStategy.getOriginalUser(modifiedBy);
    getEditInfo().setModifiedBy(modifiedBy);
  }

  @Transient
  public String getModifiedBy() {
    return getEditInfo().getModifiedBy();
  }

  public void setModifiedBy(String modifiedBy) {
    getEditInfo().setModifiedBy(modifiedBy);
  }

  @ManyToOne(optional = false)
  @JoinColumn(nullable = false, name = "owner_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  @IndexedEmbedded
  @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
  public User getOwner() {
    return owner;
  }

  public void setOwner(User owner) {
    this.owner = owner;
  }

  @Transient
  public String getDescription() {
    return getEditInfo().getDescription();
  }

  public void setDescription(String description) {
    getEditInfo().setDescription(description);
  }

  /** Stored as long to avoid Time zone issues */
  @Transient
  // @SearchableProperty
  public Date getCreationDate() {
    return getEditInfo().getCreationDate();
  }

  /**
   * Non-public - should be called within object creator
   *
   * @param creationDate
   */
  @Transient
  public void setCreationDate(Date creationDate) {
    getEditInfo().setCreationDate(creationDate);
  }

  /** Convenience method who works with time stamps */
  @Transient
  @GenericField(name = "creationDate")
  @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "editInfo")))
  public Date getCreationDateAsDate() {
    return getEditInfo().getCreationDate();
  }

  @Transient
  public Long getCreationDateMillis() {
    return getEditInfo().getCreationDateMillis();
  }

  @Transient
  public Long getModificationDateMillis() {
    return getEditInfo().getModificationDateMillis();
  }

  /** Stored as long to avoid Time zone issues */
  // @SearchableProperty
  @Transient
  public Long getModificationDate() {
    return getEditInfo().getModificationDate().getTime();
  }

  public void setModificationDate(Long modificationDate) {
    getEditInfo().setModificationDate(new Date(modificationDate));
  }

  /**
   * Convenience method who works with time stamps
   *
   * @param modificationDate
   */
  @Transient
  public void setModificationDate(Date modificationDate) {
    getEditInfo().setModificationDate(modificationDate);
  }

  /** Convenience method who works with time stamps */
  @Transient
  @GenericField(name = "modifiedDate")
  @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "editInfo")))
  public Date getModificationDateAsDate() {
    return getEditInfo().getModificationDate();
  }

  /**
   * Adds the type of this record, if it does not already have this type.. Subclasses can override
   * this method but should call super.addType(type) first.
   *
   * @param rtype
   */
  public void addType(RecordType rtype) {
    if (rtype == null) {
      return;
    }
    if (StringUtils.isBlank(type)) {
      this.type = rtype.name();
      return;
    }
    final String delimiter = TYPE_DELIMITER;
    if (type != null) {
      String[] types = type.split(delimiter);
      if (!ArrayUtils.contains(types, rtype.name())) {
        type = type + delimiter + rtype.name();
      }
    }
  }

  /**
   * Removes the type from this record, if it has this type. Subclasses can override this method but
   * should call super.removeType(type) first.
   *
   * @param rtype
   */
  public void removeType(RecordType rtype) {
    if (rtype == null || StringUtils.isBlank(type)) {
      return;
    }
    if (rtype.name().equals(type)) {
      type = null;
    } else {
      type = type.replace(TYPE_DELIMITER + rtype.name(), "");
      type = type.replace(rtype.name() + TYPE_DELIMITER, "");
    }
  }

  /**
   * Boolean test for whether this record has this {@link RecordType}.
   *
   * @param rtype A {@link RecordType}
   * @return <code>true</code> if records has this type, <code>false</code> otherwise.
   */
  public boolean hasType(RecordType rtype) {
    return type != null && ArrayUtils.contains(type.split(":"), rtype.name());
  }

  @AuditTrailProperty(name = "type")
  public String getType() {
    return type;
  }

  /** for hibernate; should use RecordType.name value clients should use addType(RecordType) */
  public void setType(String type) {
    this.type = type;
  }

  /**
   * Boolean test for whether this record has been flagged as deleted. <br>
   *
   * @return
   */
  public boolean isDeleted() {
    return deleted;
  }

  /*
   * For hibernate only
   */
  void setDeleted(boolean isDeleted) {
    this.deleted = isDeleted;
  }

  /**
   * MArks record as deleted and sets the deleted date. Subclasses can override but should call
   * super.setRecordDeleted first.
   *
   * @param isDeleted
   */
  public void setRecordDeleted(boolean isDeleted) {
    this.deleted = isDeleted;
    // if isDeleted is false, we're restoring.
    setDeletedDate(isDeleted ? new Date() : null);
  }

  /**
   * GEts when an item was deleted. Can be null (if not deleted, or was deleted and restored). <br>
   * Returns a copy of the stored date object for better encapsulation
   *
   * @return
   */
  @Temporal(TemporalType.TIMESTAMP)
  public Date getDeletedDate() {
    return (deletedDate != null) ? new Date(deletedDate.getTime()) : null;
  }

  // for hibernate
  void setDeletedDate(Date deletedDate) {
    this.deletedDate = deletedDate;
  }

  public boolean isSigned() {
    return signed;
  }

  public void setSigned(boolean signed) {
    this.signed = signed;
  }

  /**
   * Subclasses that can be templates can override this.
   *
   * @return
   */
  @Transient
  public boolean isTemplate() {
    return false;
  }

  public boolean isWitnessed() {
    return witnessed;
  }

  public void setWitnessed(boolean witnessed) {
    this.witnessed = witnessed;
  }

  /**
   * Boolean flag for whether this object should ever appear in workspace listings; by default
   * returns false but may return true for media files.
   */
  @Transient
  public boolean isInvisible() {
    if (hasType(RecordType.ROOT_MEDIA)) {
      return true;
    }
    if (hasType(RecordType.SYSTEM) && getParent().isInvisible()) {
      return true;
      // if it's a gallery folder we want to hide from workspace.
    } else if (hasType(RecordType.FOLDER)) {
      List<Folder> ancestors = getAllAncestors();
      return ancestors.stream().anyMatch(f -> f.hasType(RecordType.ROOT_MEDIA));
    } else {
      return false;
    }
  }

  @Transient
  public RSPath getParentHierarchyForUser(User u) {
    return getParentHierarchyForUser(u, NULL_TERMINATOR);
  }

  /**
   * Gets a list of records in the path from this record to the target folder. The results list is
   * returned in order *from* target *to* this record and includes both source and destination. So:
   *
   * <p><code>
   * x.getShortestPathToParent(x); //returns  a 1 element list containing x
   * x.getShortestPathtoParent(immediateParentOfX); //returns  a 2 element list [parent,x]
   *
   * </code>
   *
   * @param target
   */
  @Transient
  public RSPath getShortestPathToParent(Folder target) {
    return getShortestPathToParent(f -> f.equals(target), NULL_TERMINATOR);
  }

  /**
   * Starting from this folder, ascends the folder tree until a folder is found that matches the
   * predicate target.<br>
   * The advantage of this method is that the target folder does not have to be identified up front
   * , but a property of this folder
   *
   * @param target
   * @return
   */
  @Transient
  public RSPath getShortestPathToParent(Predicate<BaseRecord> target) {
    return getShortestPathToParent(target, NULL_TERMINATOR);
  }

  /**
   * Boolean test as to whether this document or folder is in the Workspace
   *
   * @return
   */
  @Transient
  public boolean isInWorkspace() {
    RSPath path = getParentHierarchyForUser(getOwner());
    Iterator<BaseRecord> it = path.iterator();
    while (it.hasNext()) {
      BaseRecord br = it.next();
      if (br.hasType(RecordType.SYSTEM) && !(br.hasType(RecordType.ROOT))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Finds the shortest path to a target via the <code>via</code> folder. <code>via</code> is not on
   * this path, this method returns an empty path.
   *
   * <p>Developed for RSPAC-829
   *
   * @param target The target Folder we are trying to make a path to.
   * @param terminator An optional {@link FolderTraversalTerminator}, can be <code>null</code>
   * @param via an optional transit node, can be <code>null</code>.
   * @return A non-null {@link RSPath}
   */
  @Transient
  public RSPath getShortestPathToParentVia(
      Folder target, FolderTraversalTerminator terminator, Folder via) {
    if (terminator == null) {
      terminator = NULL_TERMINATOR;
    }
    if (via != null) {
      RSPath shortestToVia = getShortestPathToParent(f -> f.equals(via), terminator);
      if (shortestToVia.isEmpty()) {
        return shortestToVia;
      }
      RSPath viaToTarget = via.getShortestPathToParent(f -> f.equals(target), terminator);
      RSPath merged = viaToTarget.merge(shortestToVia);
      return merged;
    } else {
      return getShortestPathToParent(target::equals, terminator);
    }
  }

  @Transient
  public RSPath getShortestPathToParent(
      Predicate<BaseRecord> target, FolderTraversalTerminator terminator) {
    Queue<BaseRecord> queue = new LinkedList<>();
    queue.add(this);

    Set<BaseRecord> visited = new HashSet<>();
    // stores the path used to ascend the hierarchy
    Map<BaseRecord, BaseRecord> predecessor = new HashMap<>();
    BaseRecord terminal = null;
    // keeps track of distances( and hence tracks seen)
    // does BFS over parents
    // (http://ldc.usb.ve/~gabro/teaching/CI2693/lecture11.pdf)
    // first path to userRoot is shortest.
    Map<BaseRecord, Integer> distances = new HashMap<>();
    distances.put(this, 0); // we may not need distances since is BFS with
    // no cycles.
    while (!queue.isEmpty()) {
      BaseRecord curr = queue.poll();
      if (!visited.contains(curr)) {
        visited.add(curr);
        if (target.test(curr)) {
          terminal = curr;
          break; // or perhaps don't always want shortest path
        }
        for (Folder parent : curr.getParentFolders()) {
          if (!terminator.terminate(curr, parent)) {
            queue.add(parent);
            if (distances.get(parent) == null) {
              distances.put(parent, distances.get(curr) + 1);
              predecessor.put(parent, curr);
            }
          } else {
            terminal = curr;
          }
        }
      }
    }
    // e.g., this will be null if search up to user root was blocked - e.g.,
    // by delettion.
    if (terminal == null) {
      return new RSPath(new ArrayList<>());
    }
    return new RSPath(getPath(terminal, predecessor));
  }

  /**
   * Does BFS to find shortest path to
   *
   * @param u
   * @return List of nodes in parent->child order.
   */
  @Transient
  public RSPath getParentHierarchyForUser(User u, FolderTraversalTerminator terminator) {
    Folder rootFolder = u.getRootFolder();
    try {
      /*
       * make sure we have access to lazy-loaded properties of a root
       * folder
       */
      if (rootFolder != null) {
        rootFolder.getEditInfo();
      }
    } catch (LazyInitializationException e) {
      throw new RuntimeException(
          "can't load properties of user's root folder. "
              + "was the user loaded from db in the same hibernate session?",
          e);
    }

    return getShortestPathToParent(f -> f.equals(rootFolder), terminator);
  }

  protected boolean isUserRoot(User u, BaseRecord curr) {
    return curr.isFolder() && ((Folder) curr).isRootFolder() && curr.getOwner().equals(u);
  }

  private List<BaseRecord> getPath(BaseRecord key, Map<BaseRecord, BaseRecord> predecessor) {
    List<BaseRecord> parents = new ArrayList<>();
    parents.add(key);
    while (predecessor.get(key) != null) {
      BaseRecord val = predecessor.get(key);
      parents.add(val);
      key = val;
    }
    return parents;
  }

  public Long getIconId() {
    return iconId;
  }

  public void setIconId(Long iconId) {
    this.iconId = iconId;
  }

  @Override
  @Transient
  public AbstractEntityPermissionAdapter getPermissionsAdapter() {
    return new RecordPermissionAdapter(this);
  }

  /**
   * Boolean test as to whether a record-folder relation for a particular user is deleted.
   *
   * @param authenticatedUser
   */
  public boolean isDeletedForUser(User authenticatedUser) {
    for (RecordToFolder r2f : getParents()) {
      if (r2f.isRecordInFolderDeleted()
          && r2f.getUserName().equals(authenticatedUser.getUsername())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Converts this object into a simple, non-persistent view representation, without associated
   * objects, suitable for return to a client. <br>
   * Subclasses can override, to add extra information, but must call super.toRecordInfo() as the
   * first line. of the overridden method.
   */
  public RecordInformation toRecordInfo() {
    return new RecordInformation(this);
  }

  /**
   * A temporary property used for display in the UI. This is *not* persisted with the record,
   * sharing status is maintained by {@link RecordGroupSharing} class.
   *
   * @return shared status of the record. Default is {@link SharedStatus#UNSHARED}
   */
  @Transient
  public SharedStatus getSharedStatus() {
    return sharedStatus;
  }

  /** this need to be called by code that knows which user is browsing the record */
  public void setSharedStatus(SharedStatus status) {
    this.sharedStatus = status;
  }

  /**
   * Utility method to get this {@link BaseRecord} as a StructureDcoument when it is certain that
   * this actually is a {@link StructuredDocument}
   *
   * @return
   * @throws IllegalStateException if this object is <em>not</em> as {@link StructuredDocument}
   */
  public StructuredDocument asStrucDoc() {
    if (isStructuredDocument()) {
      return (StructuredDocument) this;
    } else {
      throw new IllegalStateException(this + " is not a structured document");
    }
  }

  public Optional<Object> getFieldByName(String fieldName) throws Exception {
    BaseRecord self = this;
    Map<String, Callable<Object>> fieldsMap =
        new HashMap<>() {
          {
            put("name", self::getName);
            put("owner", self::getOwner);
            put("globalIdentifier", self::getGlobalIdentifier);
            put("creationDate", self::getCreationDate);
            put("modifiedDate", self::getModificationDate);
          }
        };

    if (fieldsMap.containsKey(fieldName)) {
      return Optional.of(fieldsMap.get(fieldName).call());
    }

    return Optional.empty();
  }

  /** */
  @Transient
  public FavoritesStatus getFavoriteStatus() {
    return favoriteStatus;
  }

  /**
   * @param status
   */
  public void setFavoriteStatus(FavoritesStatus status) {
    this.favoriteStatus = status;
  }

  /**
   * Clears all ACLs from this Folder. If <code>recurse == true</code> then descendants will also
   * have their ACLs removed. For non-folders, the value of recurse will have no effect.
   */
  public void clearACL(boolean recurse) {
    this.getSharingACL().clear();
  }

  BaseRecord unionACL(RecordSharingACL other) {
    getSharingACL().unionWith(other);
    return this;
  }

  BaseRecord removeACLs(final List<ACLElement> toRemove) {
    RecordSharingACL removedACL = this.getSharingACL();
    for (ACLElement elToRemove : toRemove) {
      removedACL.removeACLElement(elToRemove);
    }
    return this;
  }

  /**
   * Boolean method to determine if record is auto-sharable. To be auto-sharable it must be either a
   * notebook or structuredDocument, and its owner must have enabled autosharing for at least 1
   * group.
   *
   * @return
   */
  @Transient
  public boolean isAutosharable() {
    return (isNotebook() || isStructuredDocument()) && getOwner().hasAutoshareGroups();
  }

  @Transient
  public boolean isShared() {
    String lastUser = null;
    for (ACLElement el : this.getSharingACL().getAclElements()) {
      if (!ANONYMOUS_USER.equals(el.getUserOrGrpUniqueName())) {
        if (lastUser != null && !lastUser.equals(el.getUserOrGrpUniqueName())) {
          /* We only expect to see a single username if a record hasn't been shared */
          return true;
        }
        lastUser = el.getUserOrGrpUniqueName();
      }
    }
    return false;
  }

  @Transient
  public boolean isPublished() {
    for (ACLElement el : this.getSharingACL().getAclElements()) {
      if (ANONYMOUS_USER.equals(el.getUserOrGrpUniqueName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Boolean test for whether this record is a structured document shared into a notebook.
   * Subclasses can override.
   *
   * @param grp
   * @return
   */
  @Transient
  public boolean isDocumentInSharedNotebook(Group grp) {
    return false;
  }

  /** Constants defining the shared status of a record. */
  public enum SharedStatus {
    /** Shared with another user or group. */
    SHARED,
    /** Not shared, private to an individual (and their PI, if in a group). */
    UNSHARED
  }

  /** Constants defining the favorite status of a record. */
  public enum FavoritesStatus {
    /** */
    NO_FAVORITE,

    /** */
    FAVORITE
  }

  @Override
  public int compareTo(BaseRecord o) {
    // Null-safe against transient (unsaved) records whose id has not yet been assigned:
    // comparing by id directly would NPE. Two transient records fall back to name ordering.
    // Note this ordering is not consistent with equals, so do not rely on it for set identity.
    if (this.id == null && o.getId() == null) {
      return this.getName() == null
          ? 0
          : this.getName().compareTo(o.getName() == null ? "" : o.getName());
    }
    if (this.id == null) return -1;
    if (o.getId() == null) return 1;
    return this.id.compareTo(o.getId());
  }
}

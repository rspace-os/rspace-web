package com.researchspace.model.record;

import static com.researchspace.model.core.RecordType.API_INBOX;
import static com.researchspace.model.core.RecordType.IMPORTS;
import static com.researchspace.model.core.RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT;
import static com.researchspace.model.core.RecordType.SHARED_FOLDER;
import static com.researchspace.model.core.RecordType.SHARED_GROUP_FOLDER_ROOT;
import static com.researchspace.model.core.RecordType.TEMPLATE;
import static com.researchspace.model.record.StructuredDocument.MAX_TAG_LENGTH;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.TaggableElnRecord;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Size;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.function.Predicate;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

@Entity
@XmlRootElement
@Audited
@Indexed
@NoArgsConstructor
@AuditTrailData(auditDomain = AuditDomain.FOLDER)
public class Folder extends BaseRecord implements TaggableElnRecord {

  private static final long serialVersionUID = 7810039088384043137L;

  /** Exports Gallery folder name */
  public static final String EXPORTS_FOLDER_NAME = "PdfDocuments";

  /** Template Gallery folder name */
  public static final String TEMPLATE_MEDIA_FOLDER_NAME = "Templates";

  /** Top-level gallery folder name */
  public static final String MEDIAROOT = "Gallery";

  /** Example folder name */
  public static final String EXAMPLES_FOLDER = "Examples";

  /** Snippets Gallery folder name */
  public static final String SNIPPETS_FOLDER = "Snippets";

  /** Shared folder name for Gallery and Group folders */
  public static final String SHARED_FOLDER_NAME = "Shared";

  /** API Inbox */
  public static final String API_INBOX_FOLDER_NAME = "Api Inbox";

  /** Imports Inbox */
  public static final String IMPORTS_INBOX_FOLDER_NAME = "Imports";

  /** Folder name for records shared between individuals */
  public static final String INDIVIDUAL_SHARE_ITEMS_FLDER_NAME = "IndividualShareItems";

  /** Folder name for records shared amongst collaboration groups */
  public static final String COLLABORATION_GROUPS_FLDER_NAME = "CollaborationGroups";

  /** Folder name for records shared amongst project groups */
  public static final String PROJECT_GROUPS_FOLDER_NAME = "ProjectGroups";

  /** Folder name for records shared amongst lab groups */
  public static final String LAB_GROUPS_FOLDER_NAME = "LabGroups";

  /** Default name for an unnamed folder. */
  public static final String DEFAULT_FOLDER_NAME = "Untitled Folder";

  @Setter private boolean systemFolder;
  @Setter private String docTag;
  @Setter private String tagMetaData;

  public Folder(ImportOverride override) {
    super(override);
  }

  /**
   * Boolean test for whether this folder is a 'special' folder created by the system rather than by
   * a user, that may have special behaviour associated with it; e.g., is a media, root or shared
   * folder
   */
  public boolean isSystemFolder() {
    return systemFolder;
  }

  @Column(length = MAX_TAG_LENGTH)
  @Size(max = MAX_TAG_LENGTH)
  @FullTextField
  public String getDocTag() {
    return docTag;
  }

  @Lob
  public String getTagMetaData() {
    return tagMetaData;
  }

  /**
   * Generates a predicate testing whether a folder is Gallery top-level folder, e.g. one of Images,
   * Documents etc. USeful if you want to test if a target media folder can accept the correct
   * content.
   *
   * @param mediaTypeRootName A GalleryContent type as returned by {@link
   *     MediaUtils#extractFileType(String fileSuffix)}
   */
  public static Predicate<BaseRecord> targetFolderIsCorrectTypeForMedia(String mediaTypeRootName) {
    return folder ->
        folder.hasType(RecordType.SYSTEM)
            && mediaTypeRootName.equals(folder.getName())
            && !folder.hasType(RecordType.ROOT);
  }

  /** Boolean test for whether this folder is the top-level Templates folder */
  @Transient
  public boolean isTemplateFolder() {
    return systemFolder && TEMPLATE_MEDIA_FOLDER_NAME.equals(getName()) && hasType(TEMPLATE);
  }

  /** Boolean test for whether this folder is the API inbox folder */
  @Transient
  public boolean isApiInboxFolder() {
    return systemFolder && API_INBOX_FOLDER_NAME.equals(getName()) && hasType(API_INBOX);
  }

  /** Boolean test for whether this folder is an additional content folder (Imports, API) */
  @Transient
  public boolean isImportedContentFolder() {
    return isApiInboxFolder() || isImportsFolder();
  }

  /** Boolean test for whether this folder is the API inbox folder */
  @Transient
  public boolean isImportsFolder() {
    return systemFolder && IMPORTS_INBOX_FOLDER_NAME.equals(getName()) && hasType(IMPORTS);
  }

  /** Boolean test for whether this folder is the top-level Shared folder */
  @Transient
  public boolean isTopLevelSharedFolder() {
    return systemFolder && SHARED_FOLDER_NAME.equals(getName());
  }

  /** Boolean test for whether this folder is Shared folder, or its sub folder */
  @Transient
  public boolean isSharedFolder() {
    return hasType(SHARED_FOLDER)
        || hasType(INDIVIDUAL_SHARED_FOLDER_ROOT)
        || hasType(SHARED_GROUP_FOLDER_ROOT);
  }

  // checks if existing parents are child.
  private boolean checkParents(Folder curr, BaseRecord child) {

    CycleSafeIterator it = new CycleSafeIterator(curr);
    while (it.hasNext()) {
      BaseRecord br = it.next();
      if (br.equals(child)) {
        return false;
      }
    }
    return !it.isCycleDetected();
  }

  /**
   * Updates the ACL of the child only, not of its content; use FolderManager for that.
   *
   * @param child
   * @param policy
   * @param owner
   * @param aclPolicy
   * @throws IllegalAddChildOperation
   * @see #addChild(BaseRecord child, User owner)
   * @throws IllegalAddChildOperation if adding this child will create a cycle..
   */
  public RecordToFolder addChild(
      BaseRecord child, ChildAddPolicy policy, User owner, ACLPropagationPolicy aclPolicy)
      throws IllegalAddChildOperation {
    // no self edges
    if (this.equals(child)) {
      throw new LocalizedIllegalAddChildOperation("folder.addChild.errors.self", this.toString());
    }
    if (!policy.canAdd(this, child)) {
      throw new LocalizedIllegalAddChildOperation(
          "folder.addChild.errors.policyRejected", policy, String.valueOf(child), this.toString());
    }
    // no cycles for cycles
    if (!checkParents(this, child)) {
      throw new LocalizedIllegalAddChildOperation(
          "folder.addChild.errors.cycle", child.getName(), this.getName());
    }

    RecordToFolder newedge = doAdd(child, owner);
    if (newedge != null) {
      postProcess(child, aclPolicy);
    }
    return newedge;
  }

  /*
   * Performs any actions required after the parent-child relations are
   * established.
   */
  private void postProcess(BaseRecord child, ACLPropagationPolicy aclPolicy) {
    if (isTemplateFolder()) {
      new DefaultPermissionFactory().setUpACLForTemplateFolderChildPermissions(child, getOwner());
    } else if (isImportedContentFolder()) {
      new DefaultPermissionFactory().setUpAclForInboxFolderChildPermissions(child, getOwner());
    }
    aclPolicy.onAdd(this, child);
    updateRecordTypeIfAddingSubfolderToSharedFolder(child);
  }

  private void updateRecordTypeIfAddingSubfolderToSharedFolder(BaseRecord child) {
    if (child.isFolder()) {
      // propagate the fact a folder is shared, if it is being added
      if (!child.hasType(RecordType.NOTEBOOK)
          && (this.hasType(RecordType.SHARED_GROUP_FOLDER_ROOT)
              || this.hasType(RecordType.SHARED_FOLDER))) {
        child.addType(RecordType.SHARED_FOLDER);
      }
    }
  }

  RecordToFolder doAdd(BaseRecord child, User owner) {
    RecordToFolder newedge = new RecordToFolder(child, this, owner.getUsername());
    // the getter, not the field: on a Hibernate proxy the field is an unmapped empty set
    return child.getParents().add(newedge) ? newedge : null;
  }

  /**
   * Adds the child record to this folder, equivalent to addChild (child, ChildAddPolicy.DEFAULT,
   * owner,ACLPropagationPolicy.DEFAULT_POLICY). There are some business rules governing what can be
   * added.
   *
   * <ul>
   *   <li>child cannot be <code>this</code> folder
   *   <li>child cannot be an ancestor of this folder
   *   <li>If this relationship already exists, <code>null</code> is returned
   * </ul>
   *
   * If more exclusion criteria are needed, use the overloaded method that takes a {@link
   * ChildAddPolicy} that can further refine what is allowed to be added.
   *
   * @param child
   * @param owner
   * @return the RecordToFolder relationship, or <code>null</code> if it could not be created.
   * @throws IllegalAddChildOperation
   */
  public RecordToFolder addChild(BaseRecord child, User owner) throws IllegalAddChildOperation {
    return addChild(child, ChildAddPolicy.DEFAULT, owner, ACLPropagationPolicy.DEFAULT_POLICY);
  }

  /** Updates the ACL of the child only, not of its content; use FolderManager for that. */
  public boolean removeChild(BaseRecord child, ACLPropagationPolicy aclPolicy) {
    RecordToFolder toRemove = null;
    // find record in child's parent relations
    for (RecordToFolder reln : child.getParents()) {
      if (reln.getFolder().equals(this)) {
        toRemove = reln;
        break;
      }
    }

    if (toRemove != null) {
      boolean removedOK = child.getParents().remove(toRemove);
      if (removedOK) {
        aclPolicy.onRemove(this, child);
        return true;
      }
    }
    return false;
  }

  public boolean removeChild(BaseRecord child) {
    return removeChild(child, ACLPropagationPolicy.DEFAULT_POLICY);
  }

  /** Copies the folder without its content; FolderManager copies a whole subtree. */
  @Override
  public Folder copy() {
    Folder clone = new Folder();
    copyBasicFolderFields(clone);
    shallowCopyRecordInfo(clone);
    return clone;
  }

  protected void copyBasicFolderFields(Folder clone) {
    clone.setDocTag(getDocTag());
    clone.setTagMetaData(getTagMetaData());
  }

  @Transient
  @Override
  public boolean isFolder() {
    return true;
  }

  /**
   * Returns <code>true</code> if this folder is a user's root (home) folder; <code>false</code>
   * otherwise.
   */
  @Transient
  public boolean isRootFolder() {
    return hasType(RecordType.ROOT);
  }

  /**
   * Convenience method for whether this folder is the root folder for the specified user. This will
   * return <code>true</code> if
   *
   * <ul>
   *   <li>The folder is of type {@link RecordType#ROOT}
   *   <li>The folder is owned by the user
   * </ul>
   *
   * @param userOrGroup
   */
  @Transient
  public boolean isRootFolderForUser(AbstractUserOrGroupImpl userOrGroup) {
    return isRootFolder() && getOwner().getUniqueName().equals(userOrGroup.getUniqueName());
  }

  @Override
  public String toString() {
    return "Folder [ editInfo=" + getEditInfo() + ", deleted=" + deleted + "]";
  }

  @Transient
  public String getRecordInfoType() {
    return isSystemFolder() ? DOCUMENT_CATEGORIES.SYSTEM_FOLDER : DOCUMENT_CATEGORIES.FOLDER;
  }

  @Override
  @Transient
  protected GlobalIdPrefix getGlobalIdPrefix() {
    boolean galleryFolder = hasAncestorOfType(RecordType.ROOT_MEDIA, true);
    return galleryFolder ? GlobalIdPrefix.GF : GlobalIdPrefix.FL;
  }

  public RecordInformation toRecordInfo() {
    RecordInformation info = super.toRecordInfo();
    info.setType(getRecordInfoType());
    return info;
  }
}

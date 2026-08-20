package com.researchspace.model.record;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.join;

import com.researchspace.model.AccessControl;
import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.Version;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.core.UniquelyIdentifiable;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.permissions.AbstractEntityPermissionAdapter;
import com.researchspace.model.permissions.FormPermissionAdapter;
import com.researchspace.model.permissions.PermissionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Validate;
import org.apache.shiro.SecurityUtils;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Filter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
@Entity
@Audited
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(
    name = "RSForm",
    indexes = {
      @Index(columnList = "systemForm", name = "isSystem"),
      @Index(columnList = "stableID", name = "stableid")
    })
@AuditTrailData(auditDomain = AuditDomain.FORM)
public abstract class AbstractForm
    implements Serializable, UniquelyIdentifiable, PermissionsAdaptable {

  private static final long serialVersionUID = -244687216857908083L;
  private static final Logger logger = LoggerFactory.getLogger(AbstractForm.class);

  private EditInfo editInfo;
  private EditStatus editStatus;
  private boolean temporary = false;
  private boolean systemForm = false;
  private FormState publishingState;
  private Version version;
  private AbstractForm previousVersion = null;
  private boolean current = false;
  private long iconId = -1; // using default

  private Long id;
  private String tags;

  private FormType formType = FormType.NORMAL;
  private AccessControl accessControl =
      new AccessControl(PermissionType.WRITE, PermissionType.NONE, PermissionType.NONE);

  private User owner;
  private String stableID = "";
  private List<FieldForm> fieldForms = new ArrayList<>();

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "abstract_form_gen")
  @TableGenerator(
      name = "abstract_form_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  /**
   * Boolean test for whether this form is a system form or not. System forms cannot be
   * edited/published or altered in any way.
   *
   * @return
   */
  public boolean isSystemForm() {
    return systemForm;
  }

  public void setSystemForm(boolean systemForm) {
    this.systemForm = systemForm;
  }

  /**
   * Gets the current edit status for this form, reflecting whether a form is accessible or
   * editable. This is a transient property, and may be <code>null</code> if has not been set
   * explicitly.
   *
   * @return
   */
  @Transient
  public EditStatus getEditStatus() {
    return editStatus;
  }

  public void setEditStatus(EditStatus editStatus) {
    this.editStatus = editStatus;
  }

  public boolean isCurrent() {
    return current;
  }

  /**
   * Any attempt to set or edit this variable in production code should ensure that the subject is
   * authorized with FORM:SHARE authority.
   *
   * @return
   */
  @Embedded
  public AccessControl getAccessControl() {
    return accessControl;
  }

  /**
   * Any attempt to set or edit this variable in production code should ensure that the subject is
   * authorized with FORM:SHARE authority.
   */
  public void setAccessControl(AccessControl accessControl) {
    this.accessControl = accessControl;
  }

  public void setCurrent(boolean current) {
    this.current = current;
  }

  @OneToOne(fetch = FetchType.LAZY, optional = true, cascade = CascadeType.PERSIST)
  public AbstractForm getPreviousVersion() {
    return previousVersion;
  }

  private void setPreviousVersion(AbstractForm previousVersion) {
    this.previousVersion = previousVersion;
  }

  public Long getIconId() {
    return iconId;
  }

  public void setIconId(Long iconId) {
    this.iconId = iconId;
  }

  /** Boolean marker for whether a form is temporary or not. */
  public boolean isTemporary() {
    return temporary;
  }

  public void setTemporary(boolean isTempForm) {
    this.temporary = isTempForm;
  }

  /**
   * Makes this form the current form if the following conditions are satisfied:
   *
   * <ul>
   *   <li>This form is a temporary form
   *   <li><code>previous</code> is the current form version
   *   <li>This form and the argument form aren't the same (equal to each other).
   *   <li>Keeps the access control of the previous version
   * </ul>
   *
   * @param previous The current form version
   * @return
   * @throws <code>previous</code> is <code>null</code> or is this Form.
   */
  public boolean makeCurrentVersion(AbstractForm previous) {
    if (previous == null || previous.equals(this)) {
      throw new IllegalArgumentException();
    }
    if (!isTemporary() || !previous.isCurrent()) {
      return false;
    }
    setTemporary(false);
    setCurrent(true);
    setOwner(previous.getOwner());
    setPreviousVersion(previous);
    setVersion(previous.getVersion().increment());
    previous.setCurrent(false);
    setPublishingState(previous.getPublishingState());
    setAccessControl(previous.getAccessControl());
    previous.setPublishingState(FormState.OLD);
    return true;
  }

  @Embedded
  public Version getVersion() {
    return version;
  }

  public void setVersion(Version version) {
    this.version = version;
  }

  /**
   * In service/ controller layers, don't use this method - use setRequiringVersionIncrement(true)
   * which indicates a 'dirty' state.<br>
   * A version in the 'New state' is not versionable, since by definition it has not been published
   */
  public void incrementVersion() {
    if (!isNewState() && !temporary) {
      setVersion(version.increment());
    }
  }

  /**
   * Default constructor, sets creation/ modification times and a generic title. This is for
   * internal use; services/clients should call the constructor that sets in user and name.
   */
  public AbstractForm() {
    editInfo = new EditInfo();
    setCreationDate(new Date());
    setModificationDate(new Date());
    setName("Untitled");
    setPublishingState(FormState.NEW);
    setVersion(new Version(0L));
    setStableID(editInfo.getCreationDateMillis() + editInfo.getCreatedBy());
  }

  /**
   * Constructor allowing specified values for required fields.
   *
   * @param name
   * @param desc
   * @param createdBy
   * @param formType FormType of this form
   */
  public AbstractForm(String name, String desc, User createdBy, FormType formType) {
    this();
    Validate.noNullElements(new Object[] {name, desc, createdBy, formType});
    setName(name);
    setDescription(desc);
    setCreatedBy(createdBy.getUniqueName());
    setModifiedBy(createdBy.getUniqueName());
    setOwner(createdBy);
    setFormType(formType);
  }

  public void setOwner(User owner) {
    this.owner = owner;
  }

  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  @ManyToOne()
  public User getOwner() {
    return owner;
  }

  /**
   * An identifier for this form across multiple versions.
   *
   * @return A non-null, fixed stable identifier.
   */
  @Column(updatable = false, length = 50)
  public String getStableID() {
    return stableID;
  }

  void setStableID(String stableID) {
    assert (stableID != null);
    this.stableID = stableID;
  }

  /**
   * Publishing states are mutually exclusive.
   *
   * @return
   */
  @Enumerated(EnumType.STRING)
  public FormState getPublishingState() {
    return publishingState;
  }

  /**
   * Cannot set a Form that is published or unpublished into a New state. Cannot alter the state of
   * a system form
   *
   * @param publishingState
   */
  public void setPublishingState(FormState publishingState) {
    if (temporary) {
      return;
    }

    if (this.publishingState == null) {
      this.publishingState = publishingState;
    } else {
      if (publishingState != null && !FormState.NEW.equals(publishingState)) {
        this.publishingState = publishingState;
      }
    }
  }

  /**
   * Boolean test for whether form is published and visible.
   *
   * @return
   */
  @Transient
  public boolean isPublishedAndVisible() {
    return FormState.PUBLISHED.equals(getPublishingState());
  }

  /**
   * Boolean test for whether form has previously been published, but is now hidden from users.
   *
   * @return
   */
  @Transient
  public boolean isPublishedAndHidden() {
    return FormState.UNPUBLISHED.equals(getPublishingState());
  }

  /**
   * Boolean test for whether form is new and has never been published.
   *
   * @return
   */
  @Transient
  public boolean isNewState() {
    return FormState.NEW.equals(getPublishingState());
  }

  /**
   * Gets a {@link SortedSet} of {@link FieldForm} objects sorted by their natural order.<br>
   * To add /remove {@link FieldForm}s from this Form, use the add/removeFieldForm methods in this
   * Form class.
   *
   * @return A possibly empty but non-null {@link List} of FieldForms.
   */
  @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("columnIndex")
  // @Sort(type = SortType.NATURAL)
  // this filter prevents the collection being put in the 2nd level cache
  @Filter(name = "notdeleted") // defined in Field form, only gets undeleted
  // field forms
  // @Cache(usage=CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
  public List<FieldForm> getFieldForms() {
    // sort/order annotatios seem ineffectual. if this is removed, tests
    // fail
    try {
      Collections.sort(fieldForms);
    } catch (Exception e) {
      try {
        logger.error(
            "Unexpected exception for form id {}, {} - {}", getId(), getName(), e.getMessage());
      } catch (Exception e2) {
        logger.error("Exception thrown from logging message! {}", e2.getMessage());
      }
    }
    return fieldForms;
  }

  @Transient
  private SortedSet<FieldForm> getActiveFieldForms() {
    TreeSet<FieldForm> sorted = new TreeSet<>();
    for (FieldForm field : getFieldForms()) {
      if (!field.isDeleted()) {
        sorted.add(field);
      }
    }
    return sorted;
  }

  /*
   * For Hibernate - clients should use add/remove instead.
   */
  public void setFieldForms(List<FieldForm> fieldForms) {
    this.fieldForms = fieldForms;
  }

  /**
   * API method to add a {@link FieldForm}; sets both sides of a relationship. Duplicates are not
   * permitted
   *
   * @param ft
   * @return <code>true </code> if operation succeeded, <code>false</code> if not (for example, if
   *     is a duplicate).
   */
  public boolean addFieldForm(FieldForm ft) {
    if (fieldForms.contains(ft)) {
      return false;
    }
    ft.setForm(this);
    boolean rc = fieldForms.add(ft);
    Collections.sort(fieldForms);
    return rc;
  }

  /**
   * Adds a list or array of {@link FieldForm}s.
   *
   * @param fts
   */
  public void addAllFieldForms(FieldForm... fts) {
    for (FieldForm ft : fts) {
      addFieldForm(ft);
    }
  }

  /**
   * API method to remove a {@link FieldForm}; sets both sides of the relationship. This will remove
   * the association from Hibernate mappings. Probably most frequently we will prefer to use
   * FieldForm#setDeleted.
   *
   * @param ft
   * @return <code>true </code> if operation succeeded.
   */
  public boolean removeFieldForm(FieldForm ft) {
    ft.setForm(this);
    boolean rc = fieldForms.remove(ft);
    if (rc) {
      Collections.sort(fieldForms);
    }
    return rc;
  }

  @Embedded
  public EditInfo getEditInfo() {
    return editInfo;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((editInfo == null) ? 0 : editInfo.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    AbstractForm other = (AbstractForm) obj;
    if (editInfo == null) {
      if (other.editInfo != null) {
        return false;
      }
    } else if (!editInfo.equals(other.editInfo)) {
      return false;
    }
    return true;
  }

  // for hibernate
  public void setEditInfo(EditInfo editInfo) {
    this.editInfo = editInfo;
  }

  @Audited
  @Transient
  @AuditTrailProperty(name = "name")
  public String getName() {
    return getEditInfo().getName();
  }

  public void setName(String name) {
    getEditInfo().setName(name);
  }

  @Transient
  public String getDescription() {
    return getEditInfo().getDescription();
  }

  @Override
  public String toString() {
    return "Form [editInfo=" + editInfo + "]";
  }

  public void setDescription(String description) {
    getEditInfo().setDescription(description);
  }

  /**
   * Stored as long to aviod Time zone issues
   *
   * @return
   */
  @Transient
  public Long getCreationDate() {
    return getEditInfo().getCreationDate().getTime();
  }

  /**
   * Convenience method who works with time stamps
   *
   * @param creationDate
   */
  @Transient
  void setCreationDate(Date creationDate) {
    getEditInfo().setCreationDate(creationDate);
  }

  /** Convenience method who works with time stamps */
  @Transient
  public Date getCreationDateAsDate() {
    return getEditInfo().getCreationDate();
  }

  /**
   * Stored as long to avoid Time zone issues
   *
   * @return
   */
  @Transient
  public Date getModificationDate() {
    return getEditInfo().getModificationDate();
  }

  /** Convenience method who works with time stamps */
  @Transient
  public void setModificationDate(Date modificationDate) {
    getEditInfo().setModificationDate(modificationDate);
  }

  /** Convenience method who works with time stamps */
  @Transient
  public Date getModificationDateAsDate() {
    return getEditInfo().getModificationDate();
  }

  public void setCreatedBy(String createdBy) {
    getEditInfo().setCreatedBy(createdBy);
  }

  @Transient
  public String getCreatedBy() {
    return getEditInfo().getCreatedBy();
  }

  public void setModifiedBy(String modifiedBy) {
    getEditInfo().setModifiedBy(modifiedBy);
  }

  @Transient
  public String getModifiedBy() {
    return getEditInfo().getModifiedBy();
  }

  /**
   * Gets the number of FieldForms defined in this Form that are <em>not</em> marked as deleted.
   *
   * @return an integer >= 0
   */
  @Transient
  public int getNumActiveFields() {
    int rc = 0;
    for (FieldForm ff : fieldForms) {
      if (!ff.isDeleted()) {
        rc++;
      }
    }
    return rc;
  }

  /**
   * Gets the count of all fieldforms that are associated with this one, including those marked as
   * deleted.
   *
   * @return
   */
  @Transient
  public int getNumAllFields() {
    return fieldForms.size();
  }

  /** Convenience method which sets the state to 'PUBLISHED' */
  public void publish() {
    setPublishingState(FormState.PUBLISHED);
  }

  /** Convenience method which sets the state to 'UNPUBLISHED' */
  public void unpublish() {
    if (isSystemForm()) {
      throw new UnsupportedOperationException(
          "Cannot alter the state of system(non-editable) form: "
              + getName()
              + "[id="
              + getId()
              + "]");
    }
    setPublishingState(FormState.UNPUBLISHED);
  }

  /**
   * Copies this form and its constituent Field Forms. The following fields are not copied:
   *
   * <ul>
   *   <li>creation/modification times
   *   <li>Publishing state (will be new )
   *   <li>version (copy's version will be 0)
   * </ul>
   *
   * But, the copy will contain a list of the form fields
   *
   * @return
   */
  public <T extends AbstractForm> T copy(IFormCopyPolicy<T> policy) {
    return policy.copy((T) this);
  }

  /** Form tags */
  public void setTags(String tags) {
    this.tags = tags;
  }

  /**
   * Gets Form tags
   *
   * @return A string of 1 or more tags
   */
  @Column(name = "tmpTag") // for backward compatibility with old field name
  public String getTags() {
    return tags;
  }

  @Override
  @Transient
  public AbstractEntityPermissionAdapter getPermissionsAdapter() {
    return new FormPermissionAdapter(this);
  }

  public void setFormType(FormType formType) {
    this.formType = formType;
  }

  @Enumerated(EnumType.STRING)
  public FormType getFormType() {
    return formType;
  }

  private Map<String, Boolean> inSubjectMenuMap = new ConcurrentHashMap<>();

  @Transient
  public boolean isInSubjectsMenu() {
    String username = (String) SecurityUtils.getSubject().getPrincipal();
    Boolean rc = inSubjectMenuMap.get(username);
    if (rc != null) {
      return rc;
    } else {
      return false;
    }
  }

  /**
   * @param inSubjectMenu the inSubjectMenu to set
   */
  public void setInSubjectMenu(Boolean inSubjectMenu, User u) {
    inSubjectMenuMap.put(u.getUsername(), inSubjectMenu);
  }

  /**
   * Reorder fields
   *
   * @param fieldFormIds A {@link Collection} of active (non-deleted)field form database ids
   * @return this {@link AbstractForm}
   * @throws IllegalArgumentException if
   *     <ul>
   *       <li>If the length of the argument fieldID list != number of current active fields for
   *           this form
   *       <li>If any identifier passed in is not an id for an active fieldform in this Form
   *     </ul>
   */
  public AbstractForm reorderFields(List<Long> fieldFormIds) {
    if (fieldFormIds.size() != getNumActiveFields()) {
      throw new IllegalArgumentException(
          String.format(
              "fieldFormIds should have %d values " + " but only has %d",
              getNumActiveFields(), fieldFormIds.size()));
    }
    getActiveFieldForms().stream().map(t -> t.getId()).collect(Collectors.toList());
    List<Long> knownIds = getActiveFieldForms().stream().map(FieldForm::getId).collect(toList());
    for (Long incomingId : fieldFormIds) {
      if (!knownIds.contains(incomingId)) {
        throw new IllegalArgumentException(
            String.format(
                "Supplied id [%d] is not an active field id for this form;"
                    + " it should belong to this set: [%s]",
                incomingId, join(knownIds, ",")));
      }
    }

    for (int i = 0; i < fieldFormIds.size(); i++) {
      for (FieldForm fieldForm : getFieldForms()) {
        if (fieldForm.getId().equals(fieldFormIds.get(i))) {
          fieldForm.setColumnIndex(i);
        }
      }
    }
    Collections.sort(fieldForms);
    return this;
  }
}

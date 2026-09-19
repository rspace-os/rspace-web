package com.researchspace.model.field;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.UniquelyIdentifiable;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.RSForm;
import jakarta.persistence.Cacheable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.FilterDef;
import org.hibernate.envers.Audited;

/**
 * Base class for Field Forms (i.e., the individual parts of an {@link RSForm}). <br>
 * Ordering is based on column index and is consistent with hashcode() and equals.
 */
@Entity
@Audited
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
/**
 * This filter is to load only non-deleted fields. To use, this filter needs to enabled in the
 * session. Note: this class has a natural ordering based on column index, that is inconsistent with
 * equals.
 */
@FilterDef(name = "notdeleted", defaultCondition = "deleted = 0")
public abstract class FieldForm
    implements Comparable<FieldForm>,
        IFieldForm,
        ValidatingField,
        UniquelyIdentifiable,
        Serializable {

  @Override
  public int compareTo(FieldForm arg0) {
    if (columnIndex > arg0.getColumnIndex()) {
      return 1;
    } else if (columnIndex < arg0.getColumnIndex()) {
      return -1;
    } else {
      int rc = modificationDate.compareTo(arg0.modificationDate);
      if (rc != 0) {
        return rc;
      } else {
        return type.compareTo(arg0.type);
      }
    }
  }

  @Transient
  public String getSummary() {
    return toString();
  }

  final void setDefaultIfPresent(Field nascentField) {
    if (!StringUtils.isEmpty(getDefault())) {
      nascentField.setFieldData(getDefault());
    }
  }

  /** */
  private static final long serialVersionUID = 5313264572558607947L;

  private Long id;
  // private Template template;
  private String name;
  private int columnIndex;
  private Long modificationDate = new Date().getTime();
  private FieldType type;
  private boolean isDeleted = false;

  public boolean isDeleted() {
    return isDeleted;
  }

  /**
   * Marks this field as deleted.
   *
   * @param isDeleted
   */
  public void setDeleted(boolean isDeleted) {
    this.isDeleted = isDeleted;
  }

  private AbstractForm form;
  private IFieldForm tempFieldForm;
  private boolean temporary;

  @Transient
  public boolean isTemporary() {
    return temporary;
  }

  public void setTemporary(boolean temporary) {
    this.temporary = temporary;
  }

  @JsonIgnore
  @OneToOne(
      fetch = FetchType.LAZY,
      targetEntity = FieldForm.class,
      cascade = CascadeType.ALL,
      optional = true)
  public IFieldForm getTempFieldForm() {
    return tempFieldForm;
  }

  public void setTempFieldForm(IFieldForm tempFieldTemplate) {
    this.tempFieldForm = tempFieldTemplate;
    // null test as is optional field and this method is called by hibernate
    if (tempFieldTemplate != null) {
      tempFieldTemplate.setTemporary(true);
    }
  }

  /**
   * Getter for the {@link RSForm} to which this {@link FieldForm} belongs.
   *
   * <p>Never serialised to JSON: the back-reference would recurse into the form's own fieldForms,
   * and no JSON consumer wants the owning form graph.
   *
   * @return
   */
  @JsonIgnore
  @ManyToOne()
  @JoinColumn(nullable = false)
  public AbstractForm getForm() {
    return form;
  }

  public void setForm(AbstractForm template) {
    this.form = template;
  }

  public FieldForm() {
    setModificationDate(new Date().getTime());
  }

  /**
   * Factory method to create a Field from a FieldForm
   *
   * @return
   * @throws IllegalArgumentException if form is not of FormType.NORMAL
   */
  public final Field createNewFieldFromForm() {
    if (form != null) {
      Validate.isTrue(
          FormType.NORMAL.equals(form.getFormType()),
          "Can only create document fields from normal-type forms; this form is of type "
              + form.getFormType());
    }
    Field f = _createNewFieldFromForm();
    setProperties(f);
    return f;
  }

  private void setProperties(AbstractField field) {
    field.setName(getName());
    // does not validate
    if (!StringUtils.isEmpty(getDefault())) {
      field.setData(getDefault());
    }

    if (field.getFieldData() == null) {
      field.setData("");
    }
    field.setColumnIndex(getColumnIndex());
  }

  public abstract Field _createNewFieldFromForm();

  @Transient
  public abstract String getDefault();

  public FieldForm(String name) {
    this();
    this.name = name;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "field_form_gen")
  @TableGenerator(
      name = "field_form_gen",
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

  @Column(nullable = false, length = 50)
  @Size(max = 50)
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getColumnIndex() {
    return columnIndex;
  }

  public void setColumnIndex(int columnIndex) {
    this.columnIndex = columnIndex;
  }

  public Long getModificationDate() {
    return modificationDate;
  }

  public void setModificationDate(Long modificationDate) {
    this.modificationDate = modificationDate;
  }

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public FieldType getType() {
    return type;
  }

  public void setType(FieldType type) {
    this.type = type;
  }

  @Override
  public String toString() {
    return "FieldTemplate [id="
        + id
        + ", name="
        + name
        + ", columnIndex="
        + columnIndex
        + ", modificationDate="
        + modificationDate
        + ", type="
        + type
        + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + columnIndex;
    result = prime * result + ((modificationDate == null) ? 0 : modificationDate.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
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
    FieldForm other = (FieldForm) obj;
    if (columnIndex != other.columnIndex) {
      return false;
    }
    if (modificationDate == null) {
      if (other.modificationDate != null) {
        return false;
      }
    } else if (!modificationDate.equals(other.modificationDate)) {
      return false;
    }

    if (type != other.type) {
      return false;
    }
    return true;
  }

  void copyPropertiesToCopy(FieldForm copy) {
    copy.setColumnIndex(columnIndex);
    copy.setModificationDate(modificationDate);
    copy.setName(name);
    copy.setType(copy.getType());
    copy.setDeleted(isDeleted);
    copy.setMandatory(isMandatory());
  }

  @Override
  @Transient
  public GlobalIdentifier getOid() {
    return new GlobalIdentifier(GlobalIdPrefix.FF, getId());
  }

  private boolean isMandatory;

  public boolean isMandatory() {
    return this.isMandatory;
  }

  public void setMandatory(boolean isMandatory) {
    this.isMandatory = isMandatory;
  }
}

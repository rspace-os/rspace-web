package com.researchspace.model.field;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;
import java.util.Date;
import javassist.util.proxy.ProxyFactory;
import lombok.Setter;
import org.hibernate.envers.Audited;

@MappedSuperclass
@Setter
@Audited
public abstract class AbstractField implements Comparable<AbstractField> {

  private Long modificationDate = new Date().getTime();

  private String name;

  /**
   * Settings this object will validate the data set in via setFieldData() against this field's
   * template.
   *
   * @return
   */
  private boolean isValidating;

  private int columnIndex;
  private Long id;

  /**
   * Gets the column index of the Field. It should always be the same as that of its template.
   *
   * @return
   */
  public int getColumnIndex() {
    return columnIndex;
  }

  @Column(nullable = false, length = 50)
  public String getName() {
    return name;
  }

  // MERGE cascade is required for Hibernate 6's strict merge behaviour. PERSIST is deliberately
  // not cascaded: shallowCopy()-based temp fields carry an already-persisted (detached) FieldForm,
  // and cascading persist() to a detached entity throws PersistentObjectException. FieldForms are
  // always explicitly saved before field creation.
  @ManyToOne(
      optional = false,
      targetEntity = FieldForm.class,
      cascade = {CascadeType.MERGE})
  public IFieldForm getFieldForm() {
    return _getFieldForm();
  }

  /*
   * Subclasses return the FieldTempalte subclass.
   */
  protected abstract IFieldForm _getFieldForm();

  @Transient
  public FieldType getType() {
    return getFieldForm().getType();
  }

  /*
   * For hibernate - delegates to subclass-specific method
   */
  public void setFieldForm(IFieldForm ft) {
    _setFieldForm(ft);
  }

  /*
   * subclasses implement this using subclasses of FieldTemplate
   */
  protected abstract void _setFieldForm(IFieldForm ft);

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
    AbstractField other = (AbstractField) obj;
    if (getFieldForm() == null) {
      if (other.getFieldForm() != null) {
        return false;
      }
    } else if (!getFieldForm().equals(other.getFieldForm())) {
      return false;
    }
    if (modificationDate == null) {
      if (other.modificationDate != null) {
        return false;
      }
    } else if (!modificationDate.equals(other.modificationDate)) {
      return false;
    }
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!name.equals(other.name)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((getFieldForm() == null) ? 0 : getFieldForm().hashCode());
    result = prime * result + ((modificationDate == null) ? 0 : modificationDate.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    return result;
  }

  public Long getModificationDate() {
    return modificationDate;
  }

  @Transient
  public Date getModificationDateAsDate() {
    return modificationDate != null ? new Date(modificationDate) : null;
  }

  /**
   * Boolean test as to whether this object will validate the data set in via setFieldData().
   *
   * @return
   */
  @Transient
  public boolean isValidating() {
    return isValidating;
  }

  public abstract AbstractField shallowCopy();

  @Override
  public int compareTo(AbstractField other) {
    Integer thisColIndx = this.getFieldForm().getColumnIndex();
    Integer otherColIndx = other.getFieldForm().getColumnIndex();
    return thisColIndx.compareTo(otherColIndx);
  }

  /**
   * Convenience boolean test for whether the argument data will validate against the template.<br>
   * This method is equivalent to :
   *
   * <p>
   *
   * <pre>
   * getFieldTemplate().validate(dataToTest).hasErrorMessages()
   * </pre>
   *
   * @param dataToTest
   * @return <code>true </code>if this Field is set to validating mode and the data validates
   *     against the template.
   */
  public boolean validate(String dataToTest) {
    if (!isValidating()) {
      return true;
    } else {
      return !_getFieldForm().validate(dataToTest).hasErrorMessages();
    }
  }

  /*
   * Test for whether this field is an auditing proxy, should be internal.
   */
  public static boolean isAuditingProxy(IFieldForm ft) {
    return ft != null
        && (ProxyFactory.isProxyClass(ft.getClass())
            || ft.getClass().getName().toLowerCase().contains("proxy"));
  }

  /**
   * Convenience method to check if this field is a rich text field or not.
   *
   * @return <code>true</code> if this field is a text field, <code>false</code> otherwise.
   */
  @Transient
  public boolean isTextField() {
    return FieldType.TEXT.equals(getType());
  }

  @Transient
  public abstract String getData();

  public abstract void setData(String fieldData);

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "abstract_field_gen")
  @TableGenerator(
      name = "abstract_field_gen",
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

  @Transient
  public abstract String getFieldData();

  public abstract void setFieldData(String fieldData);

  /** Template method for copying data of a Field. Does not copy relationships or ids. */
  protected void copyFields(AbstractField copy) {
    copy.setFieldData(getFieldData());
    copy.setModificationDate(new Date().getTime());
    copy.setName(getName());
    copy.setColumnIndex(getColumnIndex());
    // copy.setFieldTemplate(getFieldTemplate());
  }
}

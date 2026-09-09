package com.researchspace.model.inventory.field;

import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.InventoryRecordConnectedEntity;
import com.researchspace.model.record.EditInfo;
import com.researchspace.model.record.IActiveUserStrategy;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.Date;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;

/** Sample field is used to hold field data for Samples. */
@Entity
@Getter
@Setter
@EqualsAndHashCode(
    of = {"id", "editInfo"},
    callSuper = false)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Audited
public abstract class ExtraField extends InventoryRecordConnectedEntity implements Serializable {

  private static final long serialVersionUID = 2062310963640792742L;

  private Long id;
  private EditInfo editInfo;
  protected boolean deleted;

  /**
   * The operation-definition key that generated this field, or null when nothing generated it.
   *
   * <p>This is the field's STABLE identity across runs of an operation, which its name is not: a
   * generated name is a localized resolution of this key ("Passage number" from
   * operations.passage.numberField), so matching by name means matching the current locale's
   * wording. A second locale, or a reworded translation, would miss the previous generation's field
   * and reset a computed counter rather than continue it (RSDEV-1231).
   *
   * <p>Null means "not owned by any operation definition": every hand-created field, and every
   * field predating the column. Only the operations endpoint may write a non-null value, enforced
   * at the single write point ({@code ApiExtraFieldsHelper}) rather than by asking every other
   * endpoint to reject one, so a non-null key is a reliable claim that an operation created this
   * field rather than merely a hint.
   *
   * <p>SCOPE: this covers fields an operation adds as EXTRA fields. When the created sample
   * inherits a template field of the same name, {@code
   * SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields} folds the generated
   * content into that inherited {@code InventoryEntityField} and drops the extra field, and that
   * entity has no such column - so a template-based lineage still falls back to matching the
   * localized field name (parallel review, I3). Giving {@code InventoryEntityField} the same column
   * is the follow-up; until then the key fixes the no-template case only.
   */
  private String operationFieldKey;

  public ExtraField() {
    editInfo = new EditInfo();
    setCreationDate(new Date());
    setModificationDate(new Date());
  }

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "extra_field_gen")
  @TableGenerator(
      name = "extra_field_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  @Embedded
  public EditInfo getEditInfo() {
    return editInfo;
  }

  @Transient
  @AuditTrailProperty(name = "name")
  public String getName() {
    return getEditInfo().getName();
  }

  public void setName(String name) {
    getEditInfo().setName(name);
  }

  @Transient
  @FullTextField(analyzer = "structureAnalyzer", name = "fieldData", projectable = Projectable.NO)
  @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "editInfo")))
  public String getData() {
    return getEditInfo().getDescription();
  }

  public void setData(String description) {
    getEditInfo().setDescription(description);
  }

  @Transient
  public Date getCreationDate() {
    return getEditInfo().getCreationDate();
  }

  @Transient
  void setCreationDate(Date creationDate) {
    getEditInfo().setCreationDate(creationDate);
  }

  @Transient
  public Date getModificationDate() {
    return getEditInfo().getModificationDate();
  }

  @Transient
  public void setModificationDate(Date modificationDate) {
    getEditInfo().setModificationDate(modificationDate);
  }

  @Transient
  public String getCreatedBy() {
    return getEditInfo().getCreatedBy();
  }

  public void setCreatedBy(String createdBy) {
    getEditInfo().setCreatedBy(createdBy);
  }

  @Transient
  public String getModifiedBy() {
    return getEditInfo().getModifiedBy();
  }

  public void setModifiedBy(String modifiedBy) {
    getEditInfo().setModifiedBy(modifiedBy);
  }

  public void setModifiedBy(String modifiedBy, IActiveUserStrategy modifyByStategy) {
    modifiedBy = modifyByStategy.getOriginalUser(modifiedBy);
    getEditInfo().setModifiedBy(modifiedBy);
  }

  /**
   * @return type of the field
   */
  @Transient
  public abstract FieldType getType();

  /** Validates provided data, returns error message if invalid */
  @Transient
  public abstract String validateNewData(String data);

  @Transient
  public GlobalIdentifier getOid() {
    return new GlobalIdentifier(GlobalIdPrefix.EF, getId());
  }

  /*
   * Performs shallow copy of data and getInfo fields. Does not set InventoryRecordRelation
   */
  public abstract ExtraField shallowCopy();

  void copyProperties(ExtraField copy) {
    copy.setEditInfo(getEditInfo().shallowCopy());
    copy.setData(getData());
    copy.setDeleted(isDeleted());
    // Carried on the copy: a field copied from a template or a sample keeps its provenance, which
    // is
    // what lets a later operation recognise it as the one its definition generated.
    copy.setOperationFieldKey(getOperationFieldKey());
  }
}

package com.researchspace.model.inventory.field;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.model.field.LocalizedIllegalStateException;
import com.researchspace.model.field.LocalizedUnsupportedOperationException;
import com.researchspace.model.field.ValidatingField;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.SampleEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;
import jakarta.validation.ConstraintViolationException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;

/** Inventory Entity field is used to hold field data for Samples. */
@Entity
@Setter
@Getter
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Audited
public abstract class InventoryEntityField
    implements Serializable, ValidatingField, Comparable<InventoryEntityField> {

  private static final long serialVersionUID = 6951489345943609794L;

  private Long id;

  private String name;
  private FieldType type;
  private Long modificationDate = new Date().getTime();
  private String data;
  private Integer columnIndex;
  private boolean deleted;
  private boolean deleteOnSampleUpdate;
  private boolean deleteOnInstrumentUpdate;
  private boolean mandatory;

  private SampleEntity sample;
  private InstrumentEntity instrumentEntity;

  /**
   * The template field that was used for creating this InstrumentEntity field.
   *
   * <p>The template can be modified independently of samples/instruments created from it, so unless
   * the sample/instrument points to the latest template definition its field properties may differ
   * from template field properties.
   */
  private InventoryEntityField templateField;

  InventoryEntityField(FieldType type, String fieldName) {
    this.type = type;
    this.name = fieldName;
  }

  public static InventoryEntityField fromFieldTypeString(String fieldTypeString) {
    Validate.notNull(fieldTypeString, "cannot create InventoryEntityField for null field type");

    String fieldType = StringUtils.capitalize(fieldTypeString);
    switch (fieldType) {
      case FieldType.STRING_TYPE:
        return new InventoryStringField();
      case FieldType.TEXT_TYPE:
        return new InventoryTextField();
      case FieldType.DATE_TYPE:
        return new InventoryDateField();
      case FieldType.NUMBER_TYPE:
        return new InventoryNumberField();
      case FieldType.TIME_TYPE:
        return new InventoryTimeField();
      case FieldType.ATTACHMENT_TYPE:
        return new InventoryAttachmentField();
      case FieldType.REFERENCE_TYPE:
        return new InventoryReferenceField();
      case FieldType.CHOICE_TYPE:
        return new InventoryChoiceField();
      case FieldType.RADIO_TYPE:
        return new InventoryRadioField();
      case FieldType.URI_TYPE:
        return new InventoryUriField();
      case FieldType.IDENTIFIER_TYPE:
        return new InventoryIdentifierField();
      case FieldType.LINK_TYPE:
        return new InventoryLinkField();
      default:
        throw new IllegalArgumentException(String.format("Unsupported field type %s", fieldType));
    }
  }

  @ManyToOne
  @JoinColumn
  public SampleEntity getSample() {
    return sample;
  }

  @ManyToOne
  @JoinColumn
  public InstrumentEntity getInstrumentEntity() {
    return instrumentEntity;
  }

  /**
   * The template field that was used for creating this inventory entity field.
   *
   * <p>The template can be modified independently of inventory entities created from it, so unless
   * the inventory entities points to the latest template definition its field properties may differ
   * from template field properties.
   */
  @ManyToOne
  public InventoryEntityField getTemplateField() {
    return templateField;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "inventory_entity_field_gen")
  @TableGenerator(
      name = "inventory_entity_field_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  @Column(nullable = false, length = 50)
  public String getName() {
    return name;
  }

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public FieldType getType() {
    return type;
  }

  @Transient
  public Date getModificationDateAsDate() {
    return modificationDate != null ? new Date(modificationDate) : null;
  }

  @Column(name = "data", length = 1000)
  public String getData() {
    return data;
  }

  @Transient
  @FullTextField(analyzer = "structureAnalyzer", name = "fieldData")
  @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "data")))
  public String getFieldData() {
    return getData();
  }

  /**
   * Validate and saves incoming string as field data.
   *
   * @param fieldData
   * @throws IllegalArgumentException if is invalid data
   */
  public void setFieldData(String fieldData) {
    assertFieldDataValid(fieldData);
    setData(fieldData);
  }

  /**
   * Checks if incoming string is a valid data for the field
   *
   * @param fieldData
   * @throws IllegalArgumentException if is invalid data
   */
  public void assertFieldDataValid(String fieldData) {
    ErrorList el = validate(fieldData);
    if (el.hasErrorMessages()) {
      throw new LocalizedIllegalArgumentException(
          "validation.inventoryField.invalidForFieldType", el, fieldData, getType().getType());
    }
  }

  /**
   * Clears this field's value without running validation, used when a field is added to an existing
   * sample during a template-version update and must start empty. The default clears the shared
   * {@code data} column; field types that hold their value in an association (e.g. {@link
   * InventoryLinkField}) override this to clear that association too.
   */
  public void clearValue() {
    setData(null);
  }

  /**
   * Boolean flag to check if field supports storing data as list of options, e.g. as radio or
   * choice fields does.
   */
  @Transient
  public boolean isOptionsStoringField() {
    return false;
  }

  /**
   * Convenient method for retrieving all possible options of radio/choice field. (only implemented
   * by these classes)
   */
  @Transient
  public List<String> getAllOptions() {
    throw new UnsupportedOperationException(getType() + " field doesn't support selected options");
  }

  /**
   * Convenient method for retrieving selected options of choice/radio field. (only implemented by
   * these classes)
   */
  @Transient
  public List<String> getSelectedOptions() {
    throw new UnsupportedOperationException(getType() + " field doesn't support selected options");
  }

  /**
   * Convenient method for setting selected options of choice/radio field. (only implemented by
   * these classes)
   */
  @Transient
  public void setSelectedOptions(List<String> selectedOptions) {
    throw new UnsupportedOperationException(getType() + " field doesn't support selected options");
  }

  /**
   * Convenient method for retrieving field attachment. (only implemented by
   * InventoryAttachmentField class)
   *
   * @return attached inventory file, or null.
   */
  @Transient
  public InventoryFile getAttachedFile() {
    return null;
  }

  /**
   * Convenient method for setting field attachment (only implemented by InventoryAttachmentField
   * class)
   */
  @Transient
  public void setAttachedFile(InventoryFile file) {
    throw new LocalizedUnsupportedOperationException(
        "errors.inventory.field.attachmentUnsupported", getType());
  }

  /**
   * Subclasses can override with validation. this implementation only checks if field is not
   * mandatory and empty. This method can be invoked by client code and is also invoked via {@code
   * setFieldData(data)}
   *
   * @param fieldData
   */
  public ErrorList validate(String fieldData) {
    ErrorList result = new ErrorList();
    boolean isTemplateField =
        (getSample() != null && getSample().isTemplate())
            || getInstrumentEntity() != null && getInstrumentEntity().isTemplate();
    if (isMandatory() && !isTemplateField && !isValidValueForMandatoryField(fieldData)) {
      result.addErrorMsgCode("validation.inventoryField.mandatoryContentMissing", getName());
    }
    return result;
  }

  @Transient
  public boolean isValidValueForMandatoryField(String fieldData) {
    return StringUtils.isNotBlank(fieldData);
  }

  /**
   * Whether a field that newly becomes mandatory during an "update samples/instruments to latest
   * template version" run must already hold a value, failing {@link
   * #updateToLatestTemplateDefinition()} when it does not. True for fields whose value lives in the
   * data column. {@link InventoryLinkField} overrides this to false: a mandatory link is
   * legitimately left unfilled by such a bulk update and is populated by a later, separate link
   * update, so it must not abort the sync (the link target is still enforced when the sample is
   * actually edited and saved).
   */
  @Transient
  protected boolean requiresValueWhenBecomingMandatoryOnTemplateUpdate() {
    return true;
  }

  public abstract InventoryEntityField shallowCopy();

  @Transient
  public GlobalIdentifier getOid() {

    return new GlobalIdentifier(GlobalIdPrefix.SF, getId());
  }

  /**
   * Should this inventory entity field be suggested as a best match for incoming data? To be used
   * for guessing field type out of incoming string.
   *
   * @param data
   * @return
   */
  public boolean isSuggestedFieldForData(String data) {
    return false;
  }

  @Override
  public int compareTo(InventoryEntityField other) {
    return columnIndex.compareTo(other.columnIndex);
  }

  /** Template method for copying data of a Field. Does not copy relationships or ids. */
  protected void copyFields(InventoryEntityField copy) {
    copy.setName(getName());
    copy.setType(getType());
    copy.setModificationDate(new Date().getTime());
    copy.setFieldData(getFieldData());
    copy.setColumnIndex(getColumnIndex());
    copy.setDeleted(isDeleted());
    copy.setMandatory(isMandatory());
  }

  /**
   * Note: don't call this method directly, but rather call
   * <p/>{@link #{Sample.updateToLatestTemplateVersion()} to update whole sample.
   * <p/>{@link #{Instrument.updateToLatestTemplateVersion()} to update whole instrument.
   * <p/>
   * This method applies changes from latest template field: - updates name of the field (if
   * changed) - updates columnIndex of the field (if changed) - applies 'deleted' flag to the field,
   * but only if field value is empty. If field value is not empty, an exception is thrown.
   *
   * @return true if method resulted in any change (modification) to the field
   * @throws IllegalStateException if the field or stored data is not compatible with latest
   *                               template definition, e.g. doesn't have connected template field,
   *                               or doesn't have data and latest field is marked as mandatory.
   */
  public boolean updateToLatestTemplateDefinition() {
    if (templateField == null) {
      throw new LocalizedIllegalStateException(
          "validation.inventoryField.noConnectedTemplate", getName());
    }

    boolean fieldUpdated = false;

    // apply change to field name
    if (!getName().equals(templateField.getName())) {
      setName(templateField.getName());
      fieldUpdated = true;
    }
    // apply change to columnIndex
    if (getColumnIndex() != null && !getColumnIndex().equals(templateField.getColumnIndex())) {
      setColumnIndex(templateField.getColumnIndex());
      fieldUpdated = true;
    }
    // apply field deletion
    if (!isDeleted()
        && templateField.isDeleted()
        && (templateField.isDeleteOnSampleUpdate() || templateField.isDeleteOnInstrumentUpdate())) {
      setDeleted(true);
      fieldUpdated = true;
    }
    if (isMandatory() != templateField.isMandatory()) {
      setMandatory(templateField.isMandatory());
      if (requiresValueWhenBecomingMandatoryOnTemplateUpdate()
          && !isValidValueForMandatoryField(getFieldData())) {
        throw new LocalizedIllegalStateException(
            "validation.inventoryField.mandatoryInLatestTemplate", getName());
      }
      fieldUpdated = true;
    }

    return fieldUpdated;
  }

  /* Managing Sample or Instrument references */

  /**
   * @return inventory record holding this field.
   */
  @Transient
  public InventoryRecord getInventoryRecord() {
    if (sample != null) {
      return sample;
    } else if (instrumentEntity != null) {
      return instrumentEntity;
    }
    return null;
  }

  /** Sets parent. */
  public void setInventoryRecord(InventoryRecord invRec) {
    if (invRec instanceof SampleEntity) {
      sample = (SampleEntity) invRec;
    } else if (invRec instanceof InstrumentEntity) {
      instrumentEntity = (InstrumentEntity) invRec;
    }
  }

  /**
   * @return global id of a record to which this entity is connected to (or null if none)
   */
  @Transient
  public GlobalIdentifier getConnectedRecordOid() {
    InventoryRecord parent = getInventoryRecord();
    return parent != null ? parent.getOid() : null;
  }

  @Transient
  public String getConnectedRecordGlobalIdentifier() {
    GlobalIdentifier parentOid = getConnectedRecordOid();
    return parentOid != null ? parentOid.getIdString() : null;
  }

  @PrePersist
  @PreUpdate
  public void validateBeforeSave() {
    int parentCount = getNonInventoryRecordParentCount();
    parentCount = sample == null ? parentCount : ++parentCount;
    parentCount = instrumentEntity == null ? parentCount : ++parentCount;
    if (parentCount != 1) {
      throw new ConstraintViolationException(
          this.getClass().getSimpleName()
              + " needs to be connected always to one and only one of the following inventory"
              + " entity: sample, sample template, instrument, instrument template",
          null);
    }
  }

  @Transient
  protected int getNonInventoryRecordParentCount() {
    return 0;
  }
}

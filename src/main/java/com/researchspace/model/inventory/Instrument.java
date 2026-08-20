package com.researchspace.model.inventory;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.util.Date;
import java.util.Optional;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;

/**
 * Represents RSpace Inventory Instrument
 */
@Entity
@DiscriminatorValue("Instrument")
@Audited
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Indexed
public class Instrument extends InstrumentEntity {

  /**
   * If this instrument was created from a template, returns the template, else {@code null}.
   * <p>
   * This will be {@code null} if any of the following are true:
   * <ul>
   * <li> This Instrument <em>is</em> a template
   * <li> This Instrument is a free-form instrument created from scratch, not using a template.
   * </ul>
   * This association is lazy-loaded.
   *
   * @return
   */
  private InstrumentTemplate instrumentTemplate;

  /**
   * Version of the template on which this instrument is based on.
   */
  @Setter(value = AccessLevel.PROTECTED)
  private Long templateLinkedVersion;

  public Instrument() {
    super();
  }

  protected Instrument(InstrumentTemplate originInstrumentTemplate, User currentuser) {
    this();
    shallowCopyBasicFields(originInstrumentTemplate, this);
    copy(originInstrumentTemplate, this, this::defaultNameCopy, currentuser);
    this.setInstrumentTemplate(originInstrumentTemplate);
    this.setTemplateLinkedVersion(originInstrumentTemplate.getVersion());
  }

  @Override
  public InstrumentEntity copyToTemplate(User currentUser) {
    return new InstrumentTemplate(this, currentUser);
  }

  @Override
  public InstrumentEntity copyFromTemplate(User currentUser) {
    throw new IllegalStateException(
        "Only an InstrumentTemplate can be used to copy from a template");
  }

  @Transient
  @Override
  public boolean isTemplate() {
    return false;
  }


  @Transient
  @Override
  public GlobalIdPrefix getGlobalIdPrefix() {
    return GlobalIdPrefix.IN;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  public InstrumentTemplate getInstrumentTemplate() {
    return instrumentTemplate;
  }

  @Column(name = "templateLinkedVersion")
  public Long getTemplateLinkedVersion() {
    return templateLinkedVersion;
  }

  @Transient
  @Override
  public InventoryRecordType getType() {
    return InventoryRecordType.INSTRUMENT;
  }

  @Transient
  @GenericField
  @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "instrumentTemplate")))
  public Long getParentTemplateId() {
    if (getInstrumentTemplate() != null) {
      return getInstrumentTemplate().getId();
    }
    return null;
  }


  public boolean updateToLatestTemplateVersion() {
    if (getInstrumentTemplate() == null) {
      throw new IllegalStateException("The instrument is not based on any template");
    }
    Long latestTemplateVersion = getInstrumentTemplate().getVersion();
    if (getTemplateLinkedVersion().equals(latestTemplateVersion)) {
      return false; // instrument already pointing to latest template version
    }

    boolean instrumentUpdated = false;

    // 1. for existing instrument fields - ask field to update to latest definition
    for (InventoryEntityField inventoryEntityField : getActiveFields()) {
      if (inventoryEntityField.getTemplateField() != null) {
        boolean fieldUpdated = inventoryEntityField.updateToLatestTemplateDefinition();
        if (fieldUpdated) {
          inventoryEntityField.setModificationDate(new Date().getTime());
          instrumentUpdated = true;
        }
      }
    }

    // 2. check for new fields in template, create them in the instrument
    for (InventoryEntityField templateField : getInstrumentTemplate().getActiveFields()) {
      if (getFieldByTemplateFieldId(templateField.getId()).isEmpty()) {
        InventoryEntityField addedField = copyAndAddInstrumentField(templateField);
        /* fields added through update to latest template version shouldn't have pre-set value */
        addedField.setFieldData(null);
        instrumentUpdated = true;
      }
    }

    if (instrumentUpdated) {
      refreshActiveFieldsAndColumnIndex();
    }
    setTemplateLinkedVersion(latestTemplateVersion);
    return instrumentUpdated;
  }

  private Optional<InventoryEntityField> getFieldByTemplateFieldId(Long id) {
    return getFields().stream()
        .filter(sf -> id != null && sf.getTemplateField() != null && id.equals(
            sf.getTemplateField().getId()))
        .findFirst();
  }

  @Override
  protected InstrumentEntity shallowCopy() {
    InstrumentEntity copy = new Instrument();
    shallowCopyBasicFields(copy);
    return copy;
  }

  @Override
  public InstrumentEntity copy(User currentUser) {
    Instrument copiedInstrument = super.copy(this::defaultNameCopy, currentUser);
    copiedInstrument.setInstrumentTemplate(getInstrumentTemplate());
    copiedInstrument.setTemplateLinkedVersion(getTemplateLinkedVersion());
    return copiedInstrument;
  }

}
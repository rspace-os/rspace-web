package com.researchspace.model.inventory;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
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
 * Represents RSpace Inventory Sample.
 */
@Entity
@DiscriminatorValue("Sample")
@Audited
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Indexed
public class Sample extends SampleEntity {

  private static final long serialVersionUID = 1L;

  /**
   * Template on which this sample is based on.
   */
  private SampleTemplate sTemplate;

  /**
   * Version of the template on which this sample is based on.
   */
  @Setter(value = AccessLevel.PROTECTED)
  private Long sTemplateLinkedVersion;

  static final Set<String> RESERVED_FIELD_NAMES = Collections.unmodifiableSet(
      Stream.concat(
              InventoryRecord.RESERVED_FIELD_NAMES.stream(),
              Stream.of(
                  "source",
                  "expiry date",
                  "sample template",
                  "storage temperature",
                  "total quantity",
                  "subsamples"))
          .collect(Collectors.toSet()));

  /**
   * for hibernate, record factory & pagination criteria
   */
  public Sample() {
    super();
  }

  protected Sample(SampleTemplate originTemplate, User currentUser) {
    this();
    shallowCopyBasicFields(originTemplate, this);
    originTemplate.copyQuantityInfoTo(this);
    copy(originTemplate, this, this::defaultNameCopy, currentUser);
    setSampleTemplate(originTemplate);
    setSTemplateLinkedVersion(originTemplate.getVersion());
  }

  @Transient
  @Override
  public boolean isTemplate() {
    return false;
  }

  @Transient
  @Override
  public InventoryRecordType getType() {
    return InventoryRecordType.SAMPLE;
  }

  @Transient
  @Override
  public GlobalIdPrefix getGlobalIdPrefix() {
    return GlobalIdPrefix.SA;
  }

  @Override
  public SampleTemplate copyToTemplate(User currentUser) {
    return new SampleTemplate(this, currentUser);
  }

  @Override
  public SampleEntity copyFromTemplate(User currentUser) {
    throw new IllegalStateException("Only a SampleTemplate can be used to copy from a template");
  }

  /**
   * If this sample was created from a template, returns the template, else {@code null}.
   * <p>
   * This will be {@code null} if this Sample is a free-form sample created from scratch, not
   * using a template.
   * <p>
   * This association is lazy-loaded.
   *
   * @return
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "STemplate_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  public SampleTemplate getSTemplate() {
    return sTemplate;
  }

  @Override
  @Transient
  public SampleTemplate getLinkedSampleTemplate() {
    return getSTemplate();
  }

  // for hibernate, does not perform validation so as to support setting lazy proxies with null values.
  void setSTemplate(SampleTemplate template) {
    this.sTemplate = template;
  }

  /**
   * Public setter for the  template used to create this sample
   *
   * @param template
   */
  public void setSampleTemplate(SampleTemplate template) {
    this.sTemplate = template;
  }

  @Transient
  @GenericField
  @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "STemplate")))
  public Long getParentTemplateId() {
    if (getSTemplate() != null) {
      return getSTemplate().getId();
    }
    return null;
  }

  @Override
  @Transient
  public Set<String> getReservedFieldNames() {
    return RESERVED_FIELD_NAMES;
  }

  public boolean updateToLatestTemplateVersion() {
    if (getSTemplate() == null) {
      throw new IllegalStateException("The sample is not based on any template");
    }
    Long latestTemplateVersion = getSTemplate().getVersion();
    if (getSTemplateLinkedVersion().equals(latestTemplateVersion)) {
      return false; // sample already pointing to latest template version
    }

    boolean sampleUpdated = false;

    // 1. update some sample properties directly
    if (!isSubSampleAliasEqualTo(getSTemplate().getSubSampleAlias(),
        getSTemplate().getSubSampleAliasPlural())) {
      setSubSampleAliases(getSTemplate().getSubSampleAlias(),
          getSTemplate().getSubSampleAliasPlural());
      sampleUpdated = true;
    }

    // 2. for existing sample fields - ask field to update to latest definition
    for (InventoryEntityField inventoryEntityField : getActiveFields()) {
      if (inventoryEntityField.getTemplateField() != null) {
        boolean fieldUpdated = inventoryEntityField.updateToLatestTemplateDefinition();
        if (fieldUpdated) {
          inventoryEntityField.setModificationDate(new Date().getTime());
          sampleUpdated = true;
        }
      }
    }

    // 3. check for new fields in template, create them in the sample
    for (InventoryEntityField templateField : getSTemplate().getActiveFields()) {
      if (!getFieldByTemplateFieldId(templateField.getId()).isPresent()) {
        InventoryEntityField addedField = copyAndAddSampleField(templateField);
        /*
         * Fields added by update-to-latest-template-version start with no value. clearValue() drops
         * the value without validation (unlike setFieldData), so we skip mandatory validation here:
         * an existing sample cannot supply a value during a bulk template update, so a newly added
         * mandatory field (e.g. a mandatory Link) is allowed to start empty and is enforced on the
         * next per-sample edit/save. clearValue() also clears association-backed values such as a
         * link field's target, which the copy would otherwise inherit from the template field.
         */
        addedField.clearValue();
        sampleUpdated = true;
      }
    }

    if (sampleUpdated) {
      refreshActiveFieldsAndColumnIndex();
    }
    setSTemplateLinkedVersion(latestTemplateVersion);
    return sampleUpdated;
  }

  private Optional<InventoryEntityField> getFieldByTemplateFieldId(Long id) {
    return getFields().stream()
        .filter(sf -> id != null && sf.getTemplateField() != null && id.equals(
            sf.getTemplateField().getId()))
        .findFirst();
  }

  @Override
  protected SampleEntity shallowCopy() {
    Sample copy = new Sample();
    shallowCopyBasicFields(copy);
    copyQuantityInfoTo(copy);
    return copy;
  }

  /**
   * Duplicates this sample, including fields, core properties, icons and images. Does
   * not copy subsamples, but creates a single sample with same quantity as original.
   *
   * @param currentUser user to set as a creator and owner of the copy
   * @return the newly duplicated sample.
   */
  @Override
  public Sample copy(User currentUser) {
    Sample copiedSample = super.copy(this::defaultNameCopy, currentUser);
    copiedSample.setSampleTemplate(getSTemplate());
    copiedSample.setSTemplateLinkedVersion(getSTemplateLinkedVersion());
    return copiedSample;
  }

}

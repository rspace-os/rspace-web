package com.researchspace.model.inventory;

import static com.researchspace.model.inventory.SampleSource.LAB_CREATED;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.UniquelyIdentifiable;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.units.Quantifiable;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.model.units.ValidTemperature;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;

/**
 * Represents RSpace Inventory SampleEntity (that is Sample or SampleTemplate)
 */
@Entity(name = "SampleEntity")
@Table(name = "Sample")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
/*
 * Single-table inheritance over the legacy "Sample" table, mirroring InstrumentEntity. The
 * discriminator is Hibernate's default DTYPE column (varchar, values "Sample"/"SampleTemplate"),
 * added to Sample and Sample_AUD by changeLog-rsdev-1065.xml and backfilled from the legacy
 * "template" flag. The legacy "template" column is kept but no longer mapped, so HQL must query
 * Sample/SampleTemplate (or use TYPE()), not a "template" property.
 */
@Audited
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@AuditTrailData(auditDomain = AuditDomain.INV_SAMPLE)
@Indexed
public abstract class SampleEntity extends InventoryRecord implements Serializable,
    UniquelyIdentifiable, Quantifiable {

  private static final long serialVersionUID = 1867269597891360704L;

  public static final int SUBSAMPLE_ALIAS_MAX_LENGTH = 30;

  private User owner;

  private QuantityInfo storageTempMin;
  private QuantityInfo storageTempMax;

  private List<SubSample> subSamples = new ArrayList<>();
  private int activeSubSamplesCount;

  @IndexedEmbedded
  private List<InventoryEntityField> fields = new ArrayList<>();

  @IndexedEmbedded(name = "extraFields")
  private List<ExtraField> extraFields = new ArrayList<>();

  @IndexedEmbedded
  private List<Barcode> barcodes = new ArrayList<>();

  private List<DigitalObjectIdentifier> identifiers = new ArrayList<>();

  @IndexedEmbedded(name = "files")
  private List<InventoryFile> files = new ArrayList<>();

  private SampleSource sampleSource = LAB_CREATED;

  private LocalDate expiryDate;

  @Setter(value = AccessLevel.PRIVATE)
  private String subSampleName = SubSampleName.SUBSAMPLE.getDisplayName();
  @Setter(value = AccessLevel.PRIVATE)
  private String subSampleNamePlural = SubSampleName.SUBSAMPLE.getDisplayNamePlural();

  /**
   * 1st field has index = 1
   */
  private int currMaxColIndex = 0;

  private Integer defaultUnitId;

  private QuantityInfo quantityInfo;

  public SampleEntity() {
  }

  @Transient
  public abstract boolean isTemplate();

  @Transient
  public abstract GlobalIdPrefix getGlobalIdPrefix();

  /**
   * Convenience copy method to make a template from a sample. This is the inverse operation to
   * {@code copyFromTemplate}
   *
   * @return a copy of the sample, as a Template.
   * @throws IllegalArgumentException if this SampleEntity <em>is</em> a template
   */
  public abstract SampleEntity copyToTemplate(User currentUser);

  /**
   * Convenience copy method to make a normal sample from a template. Validates that this object
   * <em>is</em> a template, also sets the template and its version into copied sample.
   *
   * @return a copy of a template, a regular sample.
   * @throws IllegalStateException if this SampleEntity is not a template
   */
  public abstract SampleEntity copyFromTemplate(User currentUser);

  /**
   * @param nameMapper A custom name-mapper to generate name for the new copy.
   * @return
   * @see SampleEntity#copy(User)
   */
  protected <T extends SampleEntity> T copy(Function<SampleEntity, String> nameMapper,
      User currentUser) {
    T sampleCopy = this.shallowCopy();
    copy(this, sampleCopy, nameMapper, currentUser);
    return sampleCopy;
  }

  static void copy(SampleEntity origin, SampleEntity destination,
      Function<SampleEntity, String> nameMapper, User currentUser) {
    QuantityInfo toCopyTotal = origin.getTotalQuantity();
    destination.setExpiryDate(origin.getExpiryDate());
    destination.setName(nameMapper.apply(origin));
    destination.setImageFileProperty(origin.getImageFileProperty());
    destination.setThumbnailFileProperty(origin.getThumbnailFileProperty());
    destination.setCreatedBy(currentUser.getUsername());
    destination.setModifiedBy(currentUser.getUsername());
    destination.setOwner(currentUser);
    destination.setStorageTempMax(origin.getStorageTempMax());
    destination.setStorageTempMin(origin.getStorageTempMin());
    destination.setSubSampleName(origin.getSubSampleName());
    destination.setSubSampleNamePlural(origin.getSubSampleNamePlural());
    destination.setDefaultUnitId(origin.getDefaultUnitId());
    destination.setSampleSource(origin.getSampleSource());
    // don't need to set currMaxColIndex as is set by adding fields

    for (InventoryEntityField field : origin.getFields()) {
      destination.copyAndAddSampleField(field);
    }
    origin.createSubSample(nameMapper, toCopyTotal, destination);
  }

  protected void copyQuantityInfoTo(SampleEntity copy) {
    if (getQuantityInfo() != null) {
      copy.setQuantityInfo(getQuantityInfo().copy());
    }
  }

  protected InventoryEntityField copyAndAddSampleField(InventoryEntityField field) {
    InventoryEntityField copiedField = field.shallowCopy();
    /* if copying into a sample set connection to original template field */
    if (!isTemplate()) {
      /* if copying a field belonging to a template, set that field as a template field,
       * but if copying a field belonging to a non-template (i.e. sample), set
       * the original template field as a template field */
      copiedField.setTemplateField(
          field.getSample().isTemplate() ? field : field.getTemplateField());
    }
    addSampleField(copiedField);
    return copiedField;
  }

  private void createSubSample(Function<SampleEntity, String> nameMapper, QuantityInfo toCopyTotal,
      SampleEntity sample) {
    SubSample singleSS = new SubSample(sample);
    sample.addSubSample(singleSS);
    String ssName = InventorySeriesNamingHelper.getSerialNameForSubSample(nameMapper.apply(this), 1,
        1);
    singleSS.setName(ssName);

    QuantityInfo newQuantity = toCopyTotal != null ? toCopyTotal.copy()
        : QuantityInfo.zero(RSUnitDef.getUnitById(getDefaultUnitId()));
    singleSS.setQuantity(newQuantity);
    singleSS.setCreatedBy(sample.getCreatedBy());
    singleSS.setModifiedBy(sample.getModifiedBy());
  }

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @NotNull
  public SampleSource getSampleSource() {
    return sampleSource;
  }

  @NotNull
  @Column(nullable = false)
  public Integer getDefaultUnitId() {
    return defaultUnitId;
  }

  @ManyToOne
  @JoinColumn(nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  @IndexedEmbedded
  @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
  public User getOwner() {
    return owner;
  }

  @Embedded
  @AttributeOverrides({
      @AttributeOverride(name = "unitId", column = @Column(name = "storageTempMinUnitId")),
      @AttributeOverride(name = "numericValue", column = @Column(name = "storageTempMinNumericValue", precision = 19, scale = 3))})
  @ValidTemperature
  public QuantityInfo getStorageTempMin() {
    return storageTempMin;
  }

  @Embedded
  @AttributeOverrides({
      @AttributeOverride(name = "unitId", column = @Column(name = "storageTempMaxUnitId")),
      @AttributeOverride(name = "numericValue", column = @Column(name = "storageTempMaxNumericValue", precision = 19, scale = 3))})
  @ValidTemperature
  public QuantityInfo getStorageTempMax() {
    return storageTempMax;
  }

  /**
   * @return the list of SubSample being part of this Sample
   */
  @OneToMany(mappedBy = "sample", cascade = CascadeType.ALL)
  @Size(min = 1)
  @OrderBy(value = "id")
  public List<SubSample> getSubSamples() {
    return subSamples;
  }

  public void setSubSamples(List<SubSample> subSamples) {
    this.subSamples = subSamples;
    refreshActiveSubSamples();
  }

  private List<SubSample> activeSubSamples;

  @Transient
  public List<SubSample> getActiveSubSamples() {
    if (activeSubSamples == null) {
      activeSubSamples = subSamples.stream()
          .filter(ss -> !ss.isDeleted() || (isDeleted() && ss.isDeletedOnSampleDeletion()))
          .collect(Collectors.toList());
    }
    return activeSubSamples;
  }

  public void refreshActiveSubSamples() {
    activeSubSamples = null;
    getActiveSubSamples();
    activeSubSamplesCount = activeSubSamples.size();
  }

  @Transient
  public List<SubSample> getDeletedSubSamples() {
    return subSamples.stream().filter(ss -> ss.isDeleted())
        .collect(Collectors.toList());
  }

  public boolean hasExactlyOneSubSample() {
    return getActiveSubSamples().size() == 1;
  }

  /**
   * If sample has just one subSample, retrieve it. Otherwise return empty optional.
   */
  @Transient
  public Optional<SubSample> getOnlySubSample() {
    return hasExactlyOneSubSample() ? Optional.of(getActiveSubSamples().get(0)) : Optional.empty();
  }

  /**
   * @return the list of fields of this Sample
   */
  @OneToMany(mappedBy = "sample", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy(value = "columnIndex")
  protected List<InventoryEntityField> getFields() {
    return fields;
  }

  protected void setFields(List<InventoryEntityField> fields) {
    this.fields = fields;
  }

  private List<InventoryEntityField> activeFields;

  /**
   * @return list of non-deleted fields belonging to this Sample, sorted by columnIndex
   */
  @Transient
  public List<InventoryEntityField> getActiveFields() {
    if (activeFields == null) {
      activeFields = getFields().stream().filter(sf -> !sf.isDeleted())
          .sorted().collect(Collectors.toList());
    }
    return activeFields;
  }

  /**
   * Appends a new InventoryEntityField to the list of this sample's fields, incrementing
   * {@code currMaxColIndex} as a side-effect.
   *
   * @param toAdd
   */
  public void addSampleField(InventoryEntityField toAdd) {
    verifyFieldNameAllowed(toAdd.getName());
    currMaxColIndex++;
    if (toAdd.getColumnIndex() == null) {
      toAdd.setColumnIndex(currMaxColIndex);
    }
    toAdd.setInventoryRecord(this);
    fields.add(toAdd);
    refreshActiveFields();
  }

  /**
   * Resets column index property for active fields, so they start from 1 and end with
   * currMaxColIndex.
   */
  public void refreshActiveFieldsAndColumnIndex() {
    currMaxColIndex = 0;
    activeFields = null;
    for (InventoryEntityField sf : getActiveFields()) {
      sf.setColumnIndex(++currMaxColIndex);
    }
  }

  public void deleteSampleField(InventoryEntityField toDelete, boolean deleteOnSampleUpdate) {
    // Kept on the base with a runtime check (not moved to SampleTemplate): legacy behavior
    // must throw for concrete Samples reached via SampleEntity-typed references.
    if (!isTemplate()) {
      throw new IllegalArgumentException(
          "Cannot directly delete field from sample, only from template");
    }
    Optional<InventoryEntityField> fieldToDeleteOpt = getFieldById(toDelete.getId());
    if (!fieldToDeleteOpt.isPresent()) {
      throw new IllegalArgumentException(
          "Trying to delete a field not belonging to current sample");
    }
    InventoryEntityField fieldToDelete = fieldToDeleteOpt.get();
    fieldToDelete.setDeleted(true);
    fieldToDelete.setDeleteOnSampleUpdate(deleteOnSampleUpdate);
    refreshActiveFields();
  }

  public Optional<InventoryEntityField> getFieldById(Long id) {
    return getFields().stream().filter(sf -> id != null && id.equals(sf.getId())).findFirst();
  }

  public List<InventoryEntityField> refreshActiveFields() {
    activeFields = null;
    return getActiveFields();
  }

  @NotNull
  @Column(length = SUBSAMPLE_ALIAS_MAX_LENGTH)
  private String getSubSampleName() {
    return subSampleName;
  }

  @NotNull
  @Column(length = SUBSAMPLE_ALIAS_MAX_LENGTH)
  private String getSubSampleNamePlural() {
    return subSampleNamePlural;
  }

  @Transient
  public String getSubSampleAlias() {
    return getSubSampleName();
  }

  @Transient
  public String getSubSampleAliasPlural() {
    return getSubSampleNamePlural();
  }

  @Transient
  public void setSubSampleAliases(String alias, String aliasPlural) {
    Validate.notBlank(alias, "SubSample alias cannot be blank");
    Validate.notBlank(aliasPlural, "SubSample alias (plural) cannot be blank");
    setSubSampleName(StringUtils.trim(alias));
    setSubSampleNamePlural(StringUtils.trim(aliasPlural));
  }

  @Transient
  public void setSubSampleAliases(SubSampleName name) {
    setSubSampleName(name.getDisplayName());
    setSubSampleNamePlural(name.getDisplayNamePlural());
  }

  @Embedded
  @AttributeOverrides({
      @AttributeOverride(name = "unitId", column = @Column(name = "quantityUnitId")),
      @AttributeOverride(name = "numericValue", column = @Column(name = "quantityNumericValue", precision = 19, scale = 3))
  })
  public QuantityInfo getQuantityInfo() {
    return quantityInfo;
  }

  /* marked 'protected' so concrete subclass methods are used by the code outside */
  protected void setQuantityInfo(QuantityInfo quantityInfo) {
    if (quantityInfo != null && quantityInfo.getUnitId() != null) {
      if (BigDecimal.ZERO.compareTo(quantityInfo.getNumericValue()) > 0) {
        throw new IllegalArgumentException(
            "Trying to set negative record quantity: " + quantityInfo.getNumericValuePlainString());
      }
    }
    this.quantityInfo = quantityInfo;
  }

  @Override
  @Transient
  public Integer getUnitId() {
    return getQuantityInfo() == null ? null : getQuantityInfo().getUnitId();
  }

  @Override
  @Transient
  public BigDecimal getNumericValue() {
    return getQuantityInfo() == null ? null : getQuantityInfo().getNumericValue();
  }

  /**
   * Retrieve total quantity of the Sample. The total value is a sum of all subsample quantities,
   * rounded to 3 fraction digits.
   */
  @Transient
  public QuantityInfo getTotalQuantity() {
    return this.getQuantityInfo();
  }

  /**
   * Set total quantity of the Sample. Can be called only if sample has a single subsample, throws
   * exception otherwise.
   */
  public void setTotalQuantity(QuantityInfo quantityInfo) {
    if (hasExactlyOneSubSample()) {
      this.setQuantityInfo(quantityInfo);
      getActiveSubSamples().get(0).setQuantityInfo(quantityInfo);
    } else {
      throw new IllegalStateException(
          "Can't save total quantity directly in Sample having multiple SubSamples");
    }
  }

  public void recalculateTotalQuantity() {
    QuantityUtils quantityUtils = new QuantityUtils();
    List<QuantityInfo> subSampleQuantities = getActiveSubSamples().stream()
        .filter(ss -> ss.getQuantity() != null)
        .map(ss -> ss.getQuantity()).collect(Collectors.toList());

    if (subSampleQuantities.isEmpty()) {
      setQuantityInfo(null);
      return;
    }
    if (subSampleQuantities.size() == 1) {
      setQuantityInfo(subSampleQuantities.get(0));
      return;
    }

    QuantityInfo totalQuantity = quantityUtils.sum(subSampleQuantities);
    setQuantityInfo(totalQuantity);
  }

  /**
   * @return the list of extra fields of this Sample, including deleted fields.
   */
  @OneToMany(mappedBy = "sample", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy(value = "id")
  @Override
  protected List<ExtraField> getExtraFields() {
    return extraFields;
  }

  protected void setExtraFields(List<ExtraField> extraFields) {
    this.extraFields = extraFields;
    refreshActiveExtraFields();
  }

  /**
   * @return the list of barcodes of this Sample, including deleted fields.
   */
  @OneToMany(mappedBy = "sample", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy(value = "id")
  @Override
  protected List<Barcode> getBarcodes() {
    return barcodes;
  }

  protected void setBarcodes(List<Barcode> barcodes) {
    this.barcodes = barcodes;
    refreshActiveBarcodes();
  }

  @OneToMany(mappedBy = "sample", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy(value = "id")
  @Override
  protected List<DigitalObjectIdentifier> getIdentifiers() {
    return identifiers;
  }

  protected void setIdentifiers(List<DigitalObjectIdentifier> identifiers) {
    this.identifiers = identifiers;
    refreshActiveIdentifiers();
  }

  /**
   * @return the list of files attached to this Sample
   */
  @OneToMany(mappedBy = "sample", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy(value = "id")
  protected List<InventoryFile> getFiles() {
    return files;
  }

  protected void setFiles(List<InventoryFile> files) {
    this.files = files;
    refreshActiveAttachedFiles();
  }

  /**
   * Adds subsample setting both sides of the relationship and refreshing active subsamples.
   *
   * @param subSampleToAdd
   */
  public void addSubSample(SubSample subSampleToAdd) {
    this.subSamples.add(subSampleToAdd);
    subSampleToAdd.setSample(this);
    refreshActiveSubSamples();
  }

  /**
   * Checks if provided alias singular and plural are equal to ones set in this Sample.
   */
  public boolean isSubSampleAliasEqualTo(String subSampleAlias, String subSampleAliasPlural) {
    return getSubSampleAlias().equals(subSampleAlias) && getSubSampleAliasPlural().equals(
        subSampleAliasPlural);
  }

}

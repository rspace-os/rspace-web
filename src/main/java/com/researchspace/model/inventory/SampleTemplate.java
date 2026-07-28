package com.researchspace.model.inventory;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

/**
 * Represents RSpace Inventory Sample Template
 */
@Entity
@DiscriminatorValue("SampleTemplate")
@Audited
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Indexed
public class SampleTemplate extends SampleEntity {

  private static final long serialVersionUID = 1L;

  /**
   * Reserved names for a Sample Template. Mirrors {@code TemplateModel.fieldNamesInUse} in the
   * React UI: the template view is its own namespace. It reserves the base names plus the
   * template-specific labels ({@code source}, {@code expiry date}, {@code subsample alias},
   * {@code quantity units}, {@code fields}, {@code samples}) and, unlike a live Sample, does not
   * reserve the sample-instance labels such as {@code sample template} or {@code subsamples}.
   */
  static final Set<String> SAMPLE_TEMPLATE_RESERVED_FIELD_NAMES = Collections.unmodifiableSet(
      Stream.concat(
              InventoryRecord.RESERVED_FIELD_NAMES.stream(),
              Stream.of(
                  "source",
                  "expiry date",
                  "subsample alias",
                  "quantity units",
                  "fields",
                  "samples"))
          .collect(Collectors.toSet()));

  public SampleTemplate() {
    super();
  }

  protected SampleTemplate(Sample originSample, User currentUser) {
    this();
    shallowCopyBasicFields(originSample, this);
    originSample.copyQuantityInfoTo(this);
    copy(originSample, this, this::defaultNameCopy, currentUser);
  }

  @Override
  public SampleEntity copyToTemplate(User currentUser) {
    throw new IllegalArgumentException("Only a Sample can be copied into a SampleTemplate");
  }

  @Override
  public Sample copyFromTemplate(User currentUser) {
    return new Sample(this, currentUser);
  }

  @Transient
  @Override
  public boolean isTemplate() {
    return true;
  }

  @Transient
  @Override
  public InventoryRecordType getType() {
    return InventoryRecordType.SAMPLE_TEMPLATE;
  }

  @Transient
  @Override
  public GlobalIdPrefix getGlobalIdPrefix() {
    return GlobalIdPrefix.IT;
  }

  @Override
  protected SampleEntity shallowCopy() {
    SampleTemplate copy = new SampleTemplate();
    shallowCopyBasicFields(copy);
    copyQuantityInfoTo(copy);
    return copy;
  }

  @Override
  public SampleTemplate copy(User currentUser) {
    return super.copy(this::defaultNameCopy, currentUser);
  }

  @Override
  @Transient
  public Set<String> getReservedFieldNames() {
    return SAMPLE_TEMPLATE_RESERVED_FIELD_NAMES;
  }

  @Override
  protected void assertCanStoreAttachments() {
    throw new IllegalArgumentException("Sample Templates don't support file attachments yet");
  }

}

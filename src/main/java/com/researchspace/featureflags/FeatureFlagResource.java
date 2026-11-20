package com.researchspace.featureflags;

import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailProperty;
import lombok.Getter;
import lombok.Setter;

/** Caller-specific state for one feature flag. */
@AuditTrailData
public final class FeatureFlagResource {

  @Getter private final String name;

  private final boolean value;

  @AuditTrailProperty(name = "value")
  public boolean isValue() {
    return value;
  }

  @Setter private boolean baselineValue;

  @AuditTrailProperty(name = "baselineValue")
  public boolean isBaselineValue() {
    return baselineValue;
  }

  @Setter private Boolean overrideValue;

  @AuditTrailProperty(name = "overrideValue")
  public Boolean getOverrideValue() {
    return overrideValue;
  }

  private final String source;

  @AuditTrailProperty(name = "source")
  public String getSource() {
    return source;
  }

  @Getter private final boolean canOverride;

  public FeatureFlagResource(
      String name,
      boolean value,
      boolean baselineValue,
      Boolean overrideValue,
      FeatureFlagSource source,
      boolean canOverride) {
    this.name = name;
    this.value = value;
    this.baselineValue = baselineValue;
    this.overrideValue = overrideValue;
    this.source = source.name();
    this.canOverride = canOverride;
  }

  @AuditTrailIdentifier
  public String getAuditTrailIdentifier() {
    return "feature-flags:" + name;
  }
}

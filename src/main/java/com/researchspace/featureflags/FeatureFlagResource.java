package com.researchspace.featureflags;

import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailProperty;

/** Caller-specific REST v2 state for one feature flag. */
@AuditTrailData
public final class FeatureFlagResource {

  private final String name;
  private final boolean value;
  private boolean baselineValue;
  private Boolean overrideValue;
  private final String source;
  private final boolean canOverride;

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

  public String getName() {
    return name;
  }

  @AuditTrailIdentifier
  public String getAuditTrailIdentifier() {
    return "feature-flags:" + name;
  }

  @AuditTrailProperty(name = "value")
  public boolean isValue() {
    return value;
  }

  @AuditTrailProperty(name = "baselineValue")
  public boolean isBaselineValue() {
    return baselineValue;
  }

  public void setBaselineValue(boolean baselineValue) {
    this.baselineValue = baselineValue;
  }

  @AuditTrailProperty(name = "overrideValue")
  public Boolean getOverrideValue() {
    return overrideValue;
  }

  public void setOverrideValue(Boolean overrideValue) {
    this.overrideValue = overrideValue;
  }

  @AuditTrailProperty(name = "source")
  public String getSource() {
    return source;
  }

  public boolean isCanOverride() {
    return canOverride;
  }
}

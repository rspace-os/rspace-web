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

  @Getter(onMethod_ = @AuditTrailProperty(name = "value"))
  private final boolean value;

  @Getter(onMethod_ = @AuditTrailProperty(name = "baselineValue"))
  @Setter
  private boolean baselineValue;

  @Getter(onMethod_ = @AuditTrailProperty(name = "overrideValue"))
  @Setter
  private Boolean overrideValue;

  @Getter(onMethod_ = @AuditTrailProperty(name = "source"))
  private final String source;

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

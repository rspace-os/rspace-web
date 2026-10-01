package com.researchspace.service.impl;

import com.researchspace.featureflags.FeatureFlagResource;
import com.researchspace.model.User;
import java.util.Objects;

/** A feature flag change that the audit listener records after commit. */
public record FeatureFlagResourceChangedEvent(
    User actor, User subject, FeatureFlagResource resource) {

  public FeatureFlagResourceChangedEvent(User actor, FeatureFlagResource resource) {
    this(actor, actor, resource);
  }

  public FeatureFlagResourceChangedEvent {
    Objects.requireNonNull(actor, "Audit actor");
    Objects.requireNonNull(subject, "Audit subject");
    Objects.requireNonNull(resource, "Audited feature flag");
  }
}

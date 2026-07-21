package com.researchspace.featureflags;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "FeatureFlagBaseline")
public class FeatureFlagBaseline implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "flag_name", length = FeatureFlagDefinition.MAX_NAME_LENGTH, nullable = false)
  private String flagName;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  protected FeatureFlagBaseline() {}

  public FeatureFlagBaseline(String flagName, boolean enabled) {
    this.flagName = flagName;
    this.enabled = enabled;
  }

  public String getFlagName() {
    return flagName;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}

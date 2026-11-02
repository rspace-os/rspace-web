package com.researchspace.featureflags;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "FeatureFlagBaseline")
public class FeatureFlagBaseline implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "flag_name", length = FeatureFlagDefinition.MAX_NAME_LENGTH, nullable = false)
  @Getter
  private String flagName;

  @Column(name = "enabled", nullable = false)
  @Getter
  @Setter
  private boolean enabled;

  protected FeatureFlagBaseline() {}

  public FeatureFlagBaseline(String flagName, boolean enabled) {
    this.flagName = flagName;
    this.enabled = enabled;
  }
}

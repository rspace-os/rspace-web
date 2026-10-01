package com.researchspace.featureflags;

import com.researchspace.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "FeatureFlagUserOverride",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_feature_flag_user_flag",
            columnNames = {"user_id", "flag_name"}))
public class FeatureFlagUserOverride implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_feature_flag_override_user"))
  private User user;

  @Column(name = "flag_name", length = FeatureFlagDefinition.MAX_NAME_LENGTH, nullable = false)
  @Getter
  private String flagName;

  @Column(name = "enabled", nullable = false)
  @Getter
  @Setter
  private boolean enabled;

  protected FeatureFlagUserOverride() {}

  public FeatureFlagUserOverride(User user, String flagName, boolean enabled) {
    this.user = user;
    this.flagName = flagName;
    this.enabled = enabled;
  }
}

package com.researchspace.featureflags;

import com.researchspace.model.User;
import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

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
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_feature_flag_override_user"))
  private User user;

  @Column(name = "flag_name", length = FeatureFlagDefinition.MAX_NAME_LENGTH, nullable = false)
  private String flagName;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  protected FeatureFlagUserOverride() {}

  public FeatureFlagUserOverride(User user, String flagName, boolean enabled) {
    this.user = user;
    this.flagName = flagName;
    this.enabled = enabled;
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
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

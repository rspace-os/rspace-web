package com.researchspace.model.booking;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.hibernate.envers.Audited;

/** Scalar booking settings for one future bookable target. */
@Entity
@Audited
public class BookingConfiguration implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long id;
  private boolean enabled;
  private String timeZone;

  @Embedded
  @Access(AccessType.FIELD)
  private BookableTargetReference target;

  private long configurationVersion;

  public BookingConfiguration() {}

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(nullable = false)
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Column(nullable = false)
  @NotBlank(message = "{errors.api.v2.bookingConfiguration.timeZone.required}")
  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  /** Returns the complete type and ID of the configured bookable entity. */
  public BookableTargetReference getTarget() {
    return target;
  }

  /** Replaces the complete bookable target without exposing partial identity setters. */
  public void replaceTarget(BookableTargetReference target) {
    this.target = target;
  }

  @Version
  @Column(nullable = false)
  public long getConfigurationVersion() {
    return configurationVersion;
  }

  public void setConfigurationVersion(long configurationVersion) {
    this.configurationVersion = configurationVersion;
  }

  @Transient
  @AssertTrue(message = "{errors.api.v2.bookingConfiguration.timeZone.invalid}")
  public boolean isTimeZoneValid() {
    if (timeZone == null || timeZone.isBlank()) {
      return true;
    }
    try {
      ZoneId.of(timeZone);
      return true;
    } catch (DateTimeException ex) {
      return false;
    }
  }
}

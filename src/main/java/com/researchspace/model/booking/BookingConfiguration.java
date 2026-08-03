package com.researchspace.model.booking;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailProperty;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Date;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

/** Scalar booking settings for one future bookable target. */
@Entity
@Audited
// TODO: Set auditDomain = AuditDomain.BOOKING once rspace-audit defines that domain and this
// project updates its pinned dependency.
@AuditTrailData
public class BookingConfiguration implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long id;
  private boolean enabled;
  private String timeZone;
  private Date createdAt;
  private Date updatedAt;
  private User createdBy;
  private User updatedBy;

  @Embedded
  @Access(AccessType.FIELD)
  private BookableTargetReference target;

  private long configurationVersion;

  public BookingConfiguration() {}

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @AuditTrailIdentifier
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(nullable = false)
  @AuditTrailProperty(name = "enabled")
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Column(nullable = false)
  @NotBlank(message = "{errors.api.v2.bookingConfiguration.timeZone.required}")
  @AuditTrailProperty(name = "timezone")
  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  @Column(nullable = true, updatable = false)
  public Date getCreatedAt() {
    return createdAt == null ? null : new Date(createdAt.getTime());
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt == null ? null : new Date(createdAt.getTime());
  }

  @Column(nullable = true)
  public Date getUpdatedAt() {
    return updatedAt == null ? null : new Date(updatedAt.getTime());
  }

  public void setUpdatedAt(Date updatedAt) {
    this.updatedAt = updatedAt == null ? null : new Date(updatedAt.getTime());
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "createdBy_id", updatable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  public User getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "updatedBy_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  public User getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(User updatedBy) {
    this.updatedBy = updatedBy;
  }

  /** Returns the complete type and ID of the configured bookable entity. */
  @AuditTrailProperty(name = "target")
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

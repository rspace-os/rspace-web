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
import lombok.Getter;
import lombok.Setter;
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

  @Getter(onMethod_ = {@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)})
  @Setter
  private Long id;

  @Getter(onMethod_ = {@Column(nullable = false), @AuditTrailProperty(name = "enabled")})
  @Setter
  private boolean enabled;

  @Getter(
      onMethod_ = {
        @Column(nullable = false),
        @NotBlank(message = "{errors.api.v2.bookingConfiguration.timeZone.required}"),
        @AuditTrailProperty(name = "timezone")
      })
  @Setter
  private String timeZone;

  private Date createdAt;
  private Date updatedAt;

  /**
   * Eager, because the API publishes this actor on every document.
   *
   * <p>A collection response is rendered after the read transaction has committed, so a lazy proxy
   * here is one the renderer cannot resolve: reading the actor's ID threw {@code
   * LazyInitializationException} and every list and read of this resource answered 500. Fetching it
   * with the row costs one join for a value the default field selection returns anyway.
   */
  @Getter(
      onMethod_ = {
        @ManyToOne(fetch = FetchType.EAGER),
        @JoinColumn(name = "createdBy_id", updatable = false),
        @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
      })
  @Setter
  private User createdBy;

  @Getter(
      onMethod_ = {
        @ManyToOne(fetch = FetchType.EAGER),
        @JoinColumn(name = "updatedBy_id"),
        @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
      })
  @Setter
  private User updatedBy;

  @Embedded
  @Access(AccessType.FIELD)
  private BookableTargetReference target;

  @Getter(onMethod_ = {@Version, @Column(nullable = false)})
  @Setter
  private long configurationVersion;

  public BookingConfiguration() {}

  /** Returns the resource-specific identifier stored in the searchable audit log. */
  @Transient
  @AuditTrailIdentifier
  public String getAuditTrailIdentifier() {
    return id == null ? null : "booking-configurations:" + id;
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

  /** Returns the complete type and ID of the configured bookable entity. */
  @AuditTrailProperty(name = "target")
  public BookableTargetReference getTarget() {
    return target;
  }

  /** Replaces the complete bookable target without exposing partial identity setters. */
  public void replaceTarget(BookableTargetReference target) {
    this.target = target;
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

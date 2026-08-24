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

  @Getter(
      onMethod_ = {@Column(nullable = false), @AuditTrailProperty(name = "slotGranularityMinutes")})
  @Setter
  private long slotGranularityMinutes = BookingSchedulingSettings.DEFAULT_SLOT_GRANULARITY_MINUTES;

  @Getter(
      onMethod_ = {
        @Column(nullable = false, length = 5),
        @AuditTrailProperty(name = "openingStart")
      })
  @Setter
  private String openingStart = BookingSchedulingSettings.DEFAULT_OPENING_START;

  @Getter(
      onMethod_ = {@Column(nullable = false, length = 5), @AuditTrailProperty(name = "openingEnd")})
  @Setter
  private String openingEnd = BookingSchedulingSettings.DEFAULT_OPENING_END;

  @Getter(
      onMethod_ = {@Column(nullable = false), @AuditTrailProperty(name = "bufferBeforeMinutes")})
  @Setter
  private long bufferBeforeMinutes = BookingSchedulingSettings.DEFAULT_BUFFER_MINUTES;

  @Getter(onMethod_ = {@Column(nullable = false), @AuditTrailProperty(name = "bufferAfterMinutes")})
  @Setter
  private long bufferAfterMinutes = BookingSchedulingSettings.DEFAULT_BUFFER_MINUTES;

  @Getter(
      onMethod_ = {
        @Column(nullable = false),
        @AuditTrailProperty(name = "maxBookingDurationMinutes")
      })
  @Setter
  private long maxBookingDurationMinutes =
      BookingSchedulingSettings.DEFAULT_MAX_BOOKING_DURATION_MINUTES;

  @Getter(onMethod_ = {@Column(nullable = false), @AuditTrailProperty(name = "allowDoubleBooking")})
  @Setter
  private boolean allowDoubleBooking = BookingSchedulingSettings.DEFAULT_ALLOW_DOUBLE_BOOKING;

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

  @Transient
  @AssertTrue(message = "{errors.api.v2.bookingConfiguration.granularity.invalid}")
  public boolean isGranularityValid() {
    return BookingSchedulingSettings.isGranularityValid(slotGranularityMinutes);
  }

  @Transient
  @AssertTrue(message = "{errors.api.v2.bookingConfiguration.openingHours.invalid}")
  public boolean isOpeningHoursValid() {
    return BookingSchedulingSettings.areOpeningHoursValid(openingStart, openingEnd);
  }

  @Transient
  @AssertTrue(message = "{errors.api.v2.bookingConfiguration.buffer.invalid}")
  public boolean areBuffersValid() {
    return BookingSchedulingSettings.isBufferValid(bufferBeforeMinutes)
        && BookingSchedulingSettings.isBufferValid(bufferAfterMinutes);
  }

  @Transient
  @AssertTrue(message = "{errors.api.v2.bookingConfiguration.maximumDuration.invalid}")
  public boolean isMaximumDurationValid() {
    return BookingSchedulingSettings.isMaximumDurationValid(
        maxBookingDurationMinutes, slotGranularityMinutes);
  }
}

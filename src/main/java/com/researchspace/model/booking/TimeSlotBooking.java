package com.researchspace.model.booking;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

/** One confirmed or cancelled booking occurrence for a configured target. */
@Entity
@Audited
@AuditTrailData
public class TimeSlotBooking implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Getter(onMethod_ = {@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)})
  @Setter
  private Long id;

  @Getter(
      onMethod_ = {
        @ManyToOne(fetch = FetchType.LAZY, optional = false),
        @JoinColumn(name = "bookingConfiguration_id", nullable = false),
        @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
      })
  @Setter
  private BookingConfiguration bookingConfiguration;

  @Getter(
      onMethod_ = {
        @ManyToOne(fetch = FetchType.LAZY, optional = false),
        @JoinColumn(name = "requester_id", nullable = false),
        @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
      })
  @Setter
  private User requester;

  @Getter(
      onMethod_ = {
        @Enumerated(EnumType.STRING),
        @Column(nullable = false, length = 32),
        @AuditTrailProperty(name = "kind")
      })
  @Setter
  private BookingEventKind kind = BookingEventKind.BOOKING;

  private Date startTime;
  private Date endTime;

  @Getter(onMethod_ = {@Enumerated(EnumType.STRING), @Column(nullable = false, length = 32)})
  @Setter
  private BookingState state;

  @Getter(onMethod_ = {@Column(length = 1000), @AuditTrailProperty(name = "purpose")})
  @Setter
  private String purpose;

  @Getter(onMethod_ = {@Column(nullable = false)})
  @Setter
  private boolean deleted;

  private Date createdAt;
  private Date updatedAt;

  @Getter(onMethod_ = {@Version, @Column(nullable = false)})
  @Setter
  private long version;

  @Getter(
      onMethod_ = {
        @ManyToOne(fetch = FetchType.LAZY),
        @JoinColumn(name = "createdBy_id", updatable = false),
        @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
      })
  @Setter
  private User createdBy;

  @Getter(
      onMethod_ = {
        @ManyToOne(fetch = FetchType.LAZY),
        @JoinColumn(name = "updatedBy_id"),
        @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
      })
  @Setter
  private User updatedBy;

  private transient BookingPrivacy preparedPrivacy = BookingPrivacy.BUSY;
  private transient boolean preparedCanEdit;
  private transient boolean preparedCanViewConfiguration;

  public TimeSlotBooking() {}

  /** Returns the resource-specific identifier stored in the searchable audit log. */
  @Transient
  @AuditTrailIdentifier
  public String getAuditTrailIdentifier() {
    return id == null ? null : "bookings:" + id;
  }

  @Column(nullable = false)
  @AuditTrailProperty(name = "start")
  public Date getStartTime() {
    return copy(startTime);
  }

  public void setStartTime(Date startTime) {
    this.startTime = copy(startTime);
  }

  @Column(nullable = false)
  @AuditTrailProperty(name = "end")
  public Date getEndTime() {
    return copy(endTime);
  }

  public void setEndTime(Date endTime) {
    this.endTime = copy(endTime);
  }

  @Column(nullable = true, updatable = false)
  public Date getCreatedAt() {
    return copy(createdAt);
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = copy(createdAt);
  }

  @Column(nullable = true)
  public Date getUpdatedAt() {
    return copy(updatedAt);
  }

  public void setUpdatedAt(Date updatedAt) {
    this.updatedAt = copy(updatedAt);
  }

  /** Prepares the safe response values for the current actor without changing persisted data. */
  public void prepareView(BookingPrivacy privacy, boolean canEdit, boolean canViewConfiguration) {
    preparedPrivacy = privacy == null ? BookingPrivacy.BUSY : privacy;
    preparedCanEdit = preparedPrivacy == BookingPrivacy.FULL && canEdit;
    preparedCanViewConfiguration = canViewConfiguration;
  }

  /** Returns the detail level prepared by the manager for this response. */
  @Transient
  public BookingPrivacy getPrivacy() {
    return preparedPrivacy;
  }

  /** Returns whether the current actor may edit this booking. */
  @Transient
  public boolean isCanEdit() {
    return preparedCanEdit;
  }

  /** Returns whether the current actor may navigate to the parent Booking configuration. */
  @Transient
  public boolean isCanViewConfiguration() {
    return preparedCanViewConfiguration;
  }

  /** Returns the target configuration's IANA timezone without duplicating it in this row. */
  @Transient
  public String getTimeZone() {
    return bookingConfiguration == null ? null : bookingConfiguration.getTimeZone();
  }

  /** Returns purpose only when the manager prepared a full response. */
  @Transient
  public String getVisiblePurpose() {
    return preparedPrivacy == BookingPrivacy.FULL ? purpose : null;
  }

  /** Returns a non-sensitive requester label only when the manager prepared a full response. */
  @Transient
  public String getVisibleBookedBy() {
    if (preparedPrivacy != BookingPrivacy.FULL || requester == null) {
      return null;
    }
    return requester.getFullName() + " (" + requester.getUsername() + ")";
  }

  /** Returns the actual creator's non-sensitive display label. */
  @Transient
  public String getVisibleCreatedBy() {
    if (createdBy == null) {
      return null;
    }
    return createdBy.getFullName() + " (" + createdBy.getUsername() + ")";
  }

  private static Date copy(Date value) {
    return value == null ? null : new Date(value.getTime());
  }
}

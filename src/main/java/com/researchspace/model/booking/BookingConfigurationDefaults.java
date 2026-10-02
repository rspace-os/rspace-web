package com.researchspace.model.booking;

import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import jakarta.validation.constraints.AssertTrue;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

/** Singleton global Booking defaults for scheduling creation and display preferences. */
@Entity
@Audited
@AuditTrailData
public class BookingConfigurationDefaults implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  public static final long SINGLETON_ID = 1L;

  @Getter(onMethod_ = {@Id})
  @Setter
  private Long id;

  @Getter(
      onMethod_ = {@Column(nullable = false), @AuditTrailProperty(name = "slotGranularityMinutes")})
  @Setter
  private long slotGranularityMinutes;

  @Getter(
      onMethod_ = {
        @Column(nullable = false, length = 5),
        @AuditTrailProperty(name = "openingStart")
      })
  @Setter
  private String openingStart;

  @Getter(
      onMethod_ = {@Column(nullable = false, length = 5), @AuditTrailProperty(name = "openingEnd")})
  @Setter
  private String openingEnd;

  @Getter(
      onMethod_ = {@Column(nullable = false), @AuditTrailProperty(name = "bufferBeforeMinutes")})
  @Setter
  private long bufferBeforeMinutes;

  @Getter(onMethod_ = {@Column(nullable = false), @AuditTrailProperty(name = "bufferAfterMinutes")})
  @Setter
  private long bufferAfterMinutes;

  @Getter(
      onMethod_ = {
        @Column(nullable = false),
        @AuditTrailProperty(name = "maxBookingDurationMinutes")
      })
  @Setter
  private long maxBookingDurationMinutes;

  @Getter(onMethod_ = {@Column(nullable = false), @AuditTrailProperty(name = "allowDoubleBooking")})
  @Setter
  private boolean allowDoubleBooking;

  @Getter(
      onMethod_ = {
        @Column(nullable = false, length = 5),
        @AuditTrailProperty(name = "availabilityWindowStart")
      })
  @Setter
  private String availabilityWindowStart;

  @Getter(
      onMethod_ = {
        @Column(nullable = false, length = 5),
        @AuditTrailProperty(name = "availabilityWindowEnd")
      })
  @Setter
  private String availabilityWindowEnd;

  @Getter(
      onMethod_ = {
        @Enumerated(EnumType.STRING),
        @Column(nullable = false, length = 32),
        @AuditTrailProperty(name = "timezoneMode")
      })
  @Setter
  private BookingTimezoneMode timezoneMode;

  @Getter(onMethod_ = {@Column(length = 255), @AuditTrailProperty(name = "customTimezone")})
  @Setter
  private String customTimezone;

  @Getter(onMethod_ = {@Version, @Column(nullable = false)})
  @Setter
  private long configurationVersion;

  @Getter(
      onMethod_ = {
        @Enumerated(EnumType.STRING),
        @Column(nullable = false, length = 32),
        @AuditTrailProperty(name = "defaultSharedWith")
      })
  @Setter
  private BookingDefaultSharedWith defaultSharedWith = BookingDefaultSharedWith.ALL_USERS;

  private Set<BookingDefaultAccessGrantee> selectedAccessGrantees = new LinkedHashSet<>();

  public BookingConfigurationDefaults() {}

  @OneToMany(
      mappedBy = "defaults",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("id")
  public Set<BookingDefaultAccessGrantee> getSelectedAccessGrantees() {
    return selectedAccessGrantees;
  }

  @SuppressWarnings("unused")
  private void setSelectedAccessGrantees(Set<BookingDefaultAccessGrantee> selectedAccessGrantees) {
    this.selectedAccessGrantees = selectedAccessGrantees;
  }

  /** Adds a selected default grantee and establishes both sides of the relationship. */
  public void addSelectedAccessGrantee(BookingDefaultAccessGrantee grantee) {
    Objects.requireNonNull(grantee, "grantee");
    if (selectedAccessGrantees.stream()
        .anyMatch(existing -> existing.getGranteeKey().equals(grantee.getGranteeKey()))) {
      throw new IllegalArgumentException(
          "Duplicate Booking default grantee: " + grantee.getGranteeKey());
    }
    grantee.attachTo(this);
    selectedAccessGrantees.add(grantee);
  }

  /** Removes a selected default grantee from this aggregate. */
  public void removeSelectedAccessGrantee(BookingDefaultAccessGrantee grantee) {
    if (selectedAccessGrantees.remove(grantee)) {
      grantee.detachFrom(this);
    }
  }

  /** Replaces the selected creation-default grantees while preserving aggregate ownership. */
  public void replaceSelectedAccessGrantees(Collection<BookingDefaultAccessGrantee> replacement) {
    Objects.requireNonNull(replacement, "replacement");
    new LinkedHashSet<>(selectedAccessGrantees).forEach(this::removeSelectedAccessGrantee);
    replacement.forEach(this::addSelectedAccessGrantee);
  }

  /** Returns the stable identifier used by the searchable audit log. */
  @Transient
  @AuditTrailIdentifier
  public String getAuditTrailIdentifier() {
    return id == null ? null : "booking-settings:" + id;
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

  @Transient
  @AssertTrue(message = "{errors.api.v2.bookingDisplayPreferences.availabilityWindow.invalid}")
  public boolean isAvailabilityWindowValid() {
    return BookingDisplaySettings.from(this).hasValidAvailabilityWindow();
  }

  @Transient
  @AssertTrue(message = "{errors.api.v2.bookingDisplayPreferences.timeZone.invalid}")
  public boolean isDisplayTimezoneValid() {
    return BookingDisplaySettings.from(this).hasValidTimezoneChoice();
  }

  @Transient
  @AssertTrue(message = "{errors.api.v2.bookingConfiguration.defaultSharing.invalid}")
  public boolean isDefaultSharingValid() {
    return defaultSharedWith != null
        && (defaultSharedWith == BookingDefaultSharedWith.SELECTED
            ? !selectedAccessGrantees.isEmpty()
            : selectedAccessGrantees.isEmpty());
  }
}

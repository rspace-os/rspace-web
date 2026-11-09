package com.researchspace.model.booking;

import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import jakarta.validation.constraints.AssertTrue;
import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

/** Singleton global Booking defaults for scheduling creation and display preferences. */
@Entity
@Audited
@AuditTrailData(auditDomain = AuditDomain.BOOKING)
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
        @Enumerated(EnumType.STRING),
        @Column(nullable = false, length = 8),
        @AuditTrailProperty(name = "incrementUnit")
      })
  @Setter
  private BookingIncrementUnit incrementUnit = BookingSchedulingSettings.DEFAULT_INCREMENT_UNIT;

  @Getter(
      onMethod_ = {
        @Convert(converter = BookingOpeningHoursConverter.class),
        @Column(nullable = false, length = 1024),
        @AuditTrailProperty(name = "openingHours")
      })
  private List<BookingOpeningHoursEntry> openingHours =
      BookingSchedulingSettings.DEFAULT_OPENING_HOURS;

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

  @Getter(
      onMethod_ = {
        @Enumerated(EnumType.STRING),
        @Column(nullable = false, length = 32),
        @AuditTrailProperty(name = "timeFormat")
      })
  @Setter
  private BookingTimeFormat timeFormat = BookingTimeFormat.AUTOMATIC;

  @Getter(onMethod_ = {@Version, @Column(nullable = false)})
  @Setter
  private long configurationVersion;

  public BookingConfigurationDefaults() {}

  /** Returns the stable identifier used by the searchable audit log. */
  @Transient
  @AuditTrailIdentifier
  public String getAuditTrailIdentifier() {
    return id == null ? null : "booking-settings:" + id;
  }

  @Transient
  @AssertTrue(message = "{errors.api.v2.bookingConfiguration.granularity.invalid}")
  public boolean isGranularityValid() {
    return BookingSchedulingSettings.isGranularityValid(slotGranularityMinutes, incrementUnit);
  }

  @Transient
  @AssertTrue(message = "{errors.api.v2.bookingConfiguration.openingHours.invalid}")
  public boolean isOpeningHoursValid() {
    return BookingSchedulingSettings.areOpeningHoursValid(openingHours);
  }

  /** Replaces the complete schedule, stored in ascending weekday order. */
  public void setOpeningHours(List<BookingOpeningHoursEntry> openingHours) {
    this.openingHours =
        openingHours == null
            ? null
            : openingHours.stream()
                .sorted(
                    Comparator.nullsLast(
                        Comparator.comparingInt(BookingOpeningHoursEntry::dayOfWeek)))
                .toList();
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
        maxBookingDurationMinutes, slotGranularityMinutes, incrementUnit);
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
}

package com.researchspace.model.booking;

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
import lombok.Setter;
import org.hibernate.envers.Audited;

/** Singleton global Booking defaults for scheduling creation and display preferences. */
@Entity
@Audited
@AuditTrailData
public class BookingConfigurationDefaults implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  public static final long SINGLETON_ID = 1L;

  @Setter private Long id;

  @Id
  public Long getId() {
    return id;
  }

  @Setter private long slotGranularityMinutes;

  @Column(nullable = false)
  @AuditTrailProperty(name = "slotGranularityMinutes")
  public long getSlotGranularityMinutes() {
    return slotGranularityMinutes;
  }

  @Setter
  private BookingIncrementUnit incrementUnit = BookingSchedulingSettings.DEFAULT_INCREMENT_UNIT;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  @AuditTrailProperty(name = "incrementUnit")
  public BookingIncrementUnit getIncrementUnit() {
    return incrementUnit;
  }

  private List<BookingOpeningHoursEntry> openingHours =
      BookingSchedulingSettings.DEFAULT_OPENING_HOURS;

  @Convert(converter = BookingOpeningHoursConverter.class)
  @Column(nullable = false, length = 1024)
  @AuditTrailProperty(name = "openingHours")
  public List<BookingOpeningHoursEntry> getOpeningHours() {
    return openingHours;
  }

  @Setter private long bufferBeforeMinutes;

  @Column(nullable = false)
  @AuditTrailProperty(name = "bufferBeforeMinutes")
  public long getBufferBeforeMinutes() {
    return bufferBeforeMinutes;
  }

  @Setter private long bufferAfterMinutes;

  @Column(nullable = false)
  @AuditTrailProperty(name = "bufferAfterMinutes")
  public long getBufferAfterMinutes() {
    return bufferAfterMinutes;
  }

  @Setter private long maxBookingDurationMinutes;

  @Column(nullable = false)
  @AuditTrailProperty(name = "maxBookingDurationMinutes")
  public long getMaxBookingDurationMinutes() {
    return maxBookingDurationMinutes;
  }

  @Setter private boolean allowDoubleBooking;

  @Column(nullable = false)
  @AuditTrailProperty(name = "allowDoubleBooking")
  public boolean isAllowDoubleBooking() {
    return allowDoubleBooking;
  }

  @Setter private String availabilityWindowStart;

  @Column(nullable = false, length = 5)
  @AuditTrailProperty(name = "availabilityWindowStart")
  public String getAvailabilityWindowStart() {
    return availabilityWindowStart;
  }

  @Setter private String availabilityWindowEnd;

  @Column(nullable = false, length = 5)
  @AuditTrailProperty(name = "availabilityWindowEnd")
  public String getAvailabilityWindowEnd() {
    return availabilityWindowEnd;
  }

  @Setter private BookingTimezoneMode timezoneMode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  @AuditTrailProperty(name = "timezoneMode")
  public BookingTimezoneMode getTimezoneMode() {
    return timezoneMode;
  }

  @Setter private String customTimezone;

  @Column(length = 255)
  @AuditTrailProperty(name = "customTimezone")
  public String getCustomTimezone() {
    return customTimezone;
  }

  @Setter private long configurationVersion;

  @Version
  @Column(nullable = false)
  public long getConfigurationVersion() {
    return configurationVersion;
  }

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

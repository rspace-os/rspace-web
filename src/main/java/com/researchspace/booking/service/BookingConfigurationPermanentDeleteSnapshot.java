package com.researchspace.booking.service;

import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingConfigurationState;
import java.time.Instant;

/** Immutable audit payload captured before a booking configuration is permanently removed. */
@AuditTrailData
public record BookingConfigurationPermanentDeleteSnapshot(
    long configurationId,
    long configurationVersion,
    BookableTargetReference target,
    String targetName,
    BookingConfigurationState priorState,
    int bookingCount,
    int subscriptionCount,
    int assignmentCount,
    Instant deletedAt) {

  @AuditTrailIdentifier
  public String getAuditTrailIdentifier() {
    return "booking-configurations:" + configurationId;
  }

  @AuditTrailProperty(name = "configurationVersion")
  public long getConfigurationVersion() {
    return configurationVersion;
  }

  @AuditTrailProperty(name = "target")
  public BookableTargetReference getTarget() {
    return target;
  }

  @AuditTrailProperty(name = "targetName")
  public String getTargetName() {
    return targetName;
  }

  @AuditTrailProperty(name = "state")
  public BookingConfigurationState getPriorState() {
    return priorState;
  }

  @AuditTrailProperty(name = "removedBookings")
  public int getBookingCount() {
    return bookingCount;
  }

  @AuditTrailProperty(name = "removedSubscriptions")
  public int getSubscriptionCount() {
    return subscriptionCount;
  }

  @AuditTrailProperty(name = "removedAssignments")
  public int getAssignmentCount() {
    return assignmentCount;
  }

  @AuditTrailProperty(name = "deletedAt")
  public String getDeletedAt() {
    return deletedAt.toString();
  }
}

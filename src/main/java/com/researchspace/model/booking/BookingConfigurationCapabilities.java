package com.researchspace.model.booking;

/** Server-owned caller capabilities published on a Booking configuration document. */
public record BookingConfigurationCapabilities(
    boolean canEditConfiguration,
    boolean canArchiveConfiguration,
    boolean canViewAudit,
    boolean canViewAccess,
    boolean canManageAssignments,
    boolean canManageOwners,
    boolean canCreateBooking,
    boolean canManageOwnBookings,
    boolean canManageAllEvents,
    boolean canCreateBlockout,
    boolean canSubscribeCalendar,
    boolean canLeaveConfiguration) {}

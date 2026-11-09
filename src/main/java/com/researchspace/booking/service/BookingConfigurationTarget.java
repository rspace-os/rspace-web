package com.researchspace.booking.service;

/** Safe Instrument summary offered as a Booking-configuration creation target. */
public record BookingConfigurationTarget(long id, String globalId, String name, boolean deleted) {}

package com.researchspace.model.booking;

/** System-administrator repair signal for a configuration with no effective Owner. */
public record BookingOwnerHealth(boolean hasEffectiveOwner) {}

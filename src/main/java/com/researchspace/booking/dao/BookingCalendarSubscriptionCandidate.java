package com.researchspace.booking.dao;

/** Subscription identity used to revalidate inherited access without loading related rows. */
public record BookingCalendarSubscriptionCandidate(
    Long subscriptionId, Long userId, Long configurationId) {}

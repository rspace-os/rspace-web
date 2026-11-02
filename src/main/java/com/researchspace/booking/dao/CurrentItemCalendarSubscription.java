package com.researchspace.booking.dao;

import java.util.Date;

/** Current locking-read projection of an item calendar subscription. */
public record CurrentItemCalendarSubscription(
    Long id,
    Long userId,
    Long configurationId,
    String tokenHash,
    String rawToken,
    Date updatedAt) {}

package com.researchspace.booking.service;

import com.researchspace.model.User;
import java.time.ZoneId;

/** Recipient state selected from one booking-notification eligibility snapshot. */
record BookingNotificationRecipient(User user, ZoneId displayZone) {}

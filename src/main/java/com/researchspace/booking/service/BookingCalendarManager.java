package com.researchspace.booking.service;

import com.researchspace.model.User;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

/** Manages item-scoped calendar credentials, feeds, and individual downloads. */
public interface BookingCalendarManager {

  record Status(boolean active, Date updatedAt, String subscriptionUrl) {

    public Status {
      updatedAt = copy(updatedAt);
    }

    @Override
    public Date updatedAt() {
      return copy(updatedAt);
    }

    private static Date copy(Date value) {
      return value == null ? null : new Date(value.getTime());
    }
  }

  record Created(Status status, String subscriptionUrl) {}

  sealed interface FeedResult permits Available, NotFound, AtCapacity, Oversized {}

  record Available(byte[] body) implements FeedResult {

    public Available {
      body = body.clone();
    }

    @Override
    public byte[] body() {
      return body.clone();
    }
  }

  record NotFound() implements FeedResult {}

  record AtCapacity() implements FeedResult {}

  record Oversized() implements FeedResult {}

  record Download(byte[] body) {

    public Download {
      body = body.clone();
    }

    @Override
    public byte[] body() {
      return body.clone();
    }
  }

  /** Returns the caller's active subscription for a readable configuration, including its URL. */
  Status status(Long configurationId, User subject, User actor);

  /** Creates or replaces the caller's credential and returns its subscription URL. */
  Created createOrRotate(Long configurationId, User subject, User actor);

  /** Revokes only the caller's credential for an existing configuration. */
  void revoke(Long configurationId, User subject, User actor);

  /** Returns the caller's user-wide booking calendar subscription. */
  Status userStatus(User subject, User actor);

  /** Creates or replaces the caller's user-wide booking calendar credential. */
  Created createOrRotateUser(User subject, User actor);

  /** Revokes the caller's user-wide booking calendar credential. */
  void revokeUser(User subject, User actor);

  /** Revokes every credential for a configuration as an authorized system administrator. */
  int resetForConfiguration(Long configurationId, User subject, User actor);

  /** Generates a one-off calendar download for a readable confirmed booking. */
  Optional<Download> download(Long bookingId, User subject, Locale locale);

  /** Resolves a bearer credential and generates the owner's current privacy-shaped feed. */
  FeedResult feed(String rawToken, Locale locale, Date refreshedAt);
}

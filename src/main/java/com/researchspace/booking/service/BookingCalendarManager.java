package com.researchspace.booking.service;

import com.researchspace.model.User;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

/** Manages item-scoped calendar credentials, feeds, and individual downloads. */
public interface BookingCalendarManager {

  record Status(boolean active, Date updatedAt) {

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

  Status status(Long configurationId, User subject, User actor);

  Created createOrRotate(Long configurationId, User subject, User actor);

  void revoke(Long configurationId, User subject, User actor);

  int resetForConfiguration(Long configurationId, User subject, User actor);

  Optional<Download> download(Long bookingId, User subject, Locale locale);

  FeedResult feed(String rawToken, Locale locale, Date refreshedAt);
}

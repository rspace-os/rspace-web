package com.researchspace.booking.service;

import com.researchspace.model.User;
import org.apache.commons.codec.digest.DigestUtils;

/** Canonical create identity, independent of the mutable booking row. */
final class BookingCreateIdentity {
  private BookingCreateIdentity() {}

  static String fingerprint(TimeSlotBookingManager.Create create, User subject) {
    String canonical =
        subject.getId()
            + "\n"
            + create.target().reference().type()
            + "\n"
            + create.target().reference().id()
            + "\n"
            + create.kind()
            + "\n"
            + create.start().getTime()
            + "\n"
            + create.end().getTime()
            + "\n"
            + (create.purpose() == null
                ? "null"
                : create.purpose().length() + ":" + create.purpose());
    return DigestUtils.sha256Hex(canonical);
  }
}

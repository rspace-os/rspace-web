package com.researchspace.booking.service;

import com.researchspace.model.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}

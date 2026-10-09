package com.researchspace.model.audittrail;

import java.util.regex.Pattern;

/** The type of entity that has been accessed by the operation to be audited. */
public enum AuditDomain {
  MESSAGING,
  RECORD,
  FOLDER,
  NOTEBOOK,
  MEDIA,
  USER,
  GROUP,
  COMMUNITY,
  FORM,
  AUDIT,
  /** Inventory domains */
  INV_SAMPLE,
  INV_SUBSAMPLE,
  INV_CONTAINER,
  INV_INSTRUMENT,
  /** Requests for inventory items, currently sample requests */
  REQUEST,
  BOOKING,
  /** A general term for workspace/ all resources */
  WORKSPACE,
  /** Default fall-through domain */
  UNKNOWN;

  /** Exact identifier syntax used by booking resources in the audit trail. */
  public static final String BOOKING_IDENTIFIER_PATTERN =
      "(?:bookings|booking-configurations|booking-settings):[0-9]+";

  private static final Pattern BOOKING_IDENTIFIER = Pattern.compile(BOOKING_IDENTIFIER_PATTERN);

  /** Returns whether {@code identifier} is an exact booking audit resource ID. */
  public static boolean isBookingIdentifier(String identifier) {
    return identifier != null && BOOKING_IDENTIFIER.matcher(identifier).matches();
  }

  /**
   * Maps historical fall-through events to the booking domain using only their top-level resource
   * identifier. Other domains and nested booking references keep their recorded domain.
   */
  public static AuditDomain normalizeLegacyBookingDomain(AuditDomain recordedDomain, Object id) {
    return recordedDomain == UNKNOWN
            && id instanceof String identifier
            && isBookingIdentifier(identifier)
        ? BOOKING
        : recordedDomain;
  }
}

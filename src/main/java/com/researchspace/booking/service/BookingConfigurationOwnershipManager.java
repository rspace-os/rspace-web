package com.researchspace.booking.service;

import com.researchspace.model.User;

/** Coordinates the optional Booking side of an Instrument ownership transfer. */
public interface BookingConfigurationOwnershipManager {

  /** Transfers Booking ownership when the Instrument has a configuration; otherwise no-ops. */
  void transferInstrumentOwnership(
      Long instrumentId, User outgoingOwner, User incomingOwner, User subject, User actor);
}

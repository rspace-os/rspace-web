package com.researchspace.service;

import com.researchspace.model.comms.CommunicationStatus;

/** Maps persisted {@link CommunicationStatus} values to localized display-message keys. */
public final class CommunicationStatusMessages {

  private CommunicationStatusMessages() {}

  /**
   * The switch is exhaustive, so adding a {@link CommunicationStatus} constant upstream is a
   * compile error here until a mapping (and catalogue entry) is added.
   */
  public static String keyFor(CommunicationStatus status) {
    return switch (status) {
      case NEW -> "messages.status.new";
      case REJECTED -> "messages.status.rejected";
      case ACCEPTED -> "messages.status.accepted";
      case CANCELLED -> "messages.status.cancelled";
      case COMPLETED -> "messages.status.completed";
      case REPLIED -> "messages.status.replied";
    };
  }
}

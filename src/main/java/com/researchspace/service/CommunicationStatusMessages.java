package com.researchspace.service;

import com.researchspace.model.comms.CommunicationStatus;

public final class CommunicationStatusMessages {

  private CommunicationStatusMessages() {}

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

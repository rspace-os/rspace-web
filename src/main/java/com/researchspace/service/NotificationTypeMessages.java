package com.researchspace.service;

import com.researchspace.model.comms.NotificationType;

public final class NotificationTypeMessages {

  private NotificationTypeMessages() {}

  /**
   * @throws IllegalArgumentException if a new {@link NotificationType} constant has been added
   *     upstream without a corresponding entry here and in the catalogue.
   */
  public static String keyFor(NotificationType type) {
    return switch (type) {
      case NOTIFICATION_DOCUMENT_EDITED -> "notificationType.documentEdited";
      case NOTIFICATION_DOCUMENT_SHARED -> "notificationType.documentShared";
      case NOTIFICATION_DOCUMENT_UNSHARED -> "notificationType.documentUnshared";
      case NOTIFICATION_DOCUMENT_DELETED -> "notificationType.documentDeleted";
      case NOTIFICATION_REQUEST_STATUS_CHANGE -> "notificationType.requestStatusChange";
      case PROCESS_COMPLETED -> "notificationType.processCompleted";
      case PROCESS_FAILED -> "notificationType.processFailed";
      case ARCHIVE_EXPORT_COMPLETED -> "notificationType.archiveExportCompleted";
      case NOTIFICATION_BOOKING_CREATED -> "notificationType.bookingCreated";
      case NOTIFICATION_BOOKING_CANCELLED -> "notificationType.bookingCancelled";
      default ->
          throw new IllegalArgumentException("No message key mapped for NotificationType " + type);
    };
  }
}

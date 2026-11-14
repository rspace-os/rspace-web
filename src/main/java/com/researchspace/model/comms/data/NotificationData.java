package com.researchspace.model.comms.data;

import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.comms.NotificationType;
import org.apache.commons.lang3.StringUtils;

public abstract class NotificationData {

  public static NotificationData createFromJson(
      NotificationType notificationType, String notificationDataJson) {
    if (!StringUtils.isEmpty(notificationDataJson)) {
      if (notificationType == NotificationType.NOTIFICATION_BOOKING_CREATED
          || notificationType == NotificationType.NOTIFICATION_BOOKING_CANCELLED
          || notificationType == NotificationType.NOTIFICATION_BOOKING_CHANGED) {
        return JacksonUtil.fromJson(notificationDataJson, OwnerBookingNotificationData.class);
      }
      if (NotificationType.ARCHIVE_EXPORT_COMPLETED.equals(notificationType)) {
        return JacksonUtil.fromJson(notificationDataJson, ArchiveExportNotificationData.class);
      }
    }
    return null;
  }

  public String toJson() {
    return JacksonUtil.toJsonWithoutEmptyFields(this);
  }
}

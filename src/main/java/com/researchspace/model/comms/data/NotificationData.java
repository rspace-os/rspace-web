package com.researchspace.model.comms.data;

import org.apache.commons.lang3.StringUtils;

import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.comms.NotificationType;

public abstract class NotificationData {

	public static NotificationData createFromJson(NotificationType notificationType, String notificationDataJson) {
		if (!StringUtils.isEmpty(notificationDataJson)) {
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

package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.data.NotificationData;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Configures how/whether notifications are sent. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationConfig {

  /**
   * Static factory to get notification config for record deleted events
   *
   * @param notificationTargetsOverride
   * @return
   */
  public static NotificationConfig documentDeleted(Set<User> notificationTargetsOverride) {
    return new NotificationConfig(
        NotificationType.NOTIFICATION_DOCUMENT_DELETED,
        null,
        true,
        CommunicationNotifyPolicy.ALWAYS_NOTIFY,
        false,
        notificationTargetsOverride);
  }

  private NotificationType notificationType;

  private NotificationData notificationData;

  /**
   * Whether the notification should be broadcastable (<code>true</code>) or just using internal
   * messaging (<code>false</code>)
   */
  private boolean broadcast;

  /** An optional alternative policy to the default NotifyOnceOnly policy, */
  private CommunicationNotifyPolicy policyOverride;

  /**
   * Whether authorisation to view a document that is the subject of the notification is required.
   */
  private boolean recordAuthorisationRequired;

  @Builder.Default private Set<User> notificationTargetsOverride = new HashSet<>();
}

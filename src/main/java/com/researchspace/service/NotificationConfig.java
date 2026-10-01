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
        false,
        notificationTargetsOverride);
  }

  /** Backward-compatible constructor with the default event-preference behavior. */
  public NotificationConfig(
      NotificationType notificationType,
      NotificationData notificationData,
      boolean broadcast,
      CommunicationNotifyPolicy policyOverride,
      boolean recordAuthorisationRequired,
      Set<User> notificationTargetsOverride) {
    this(
        notificationType,
        notificationData,
        broadcast,
        policyOverride,
        recordAuthorisationRequired,
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

  /**
   * Whether the notification event preference should be bypassed for the configured recipients.
   * This is a server-side override for narrowly defined notification flows; it does not affect
   * email preferences.
   */
  @Builder.Default private boolean notificationEventPreferenceOverride = false;

  @Builder.Default private Set<User> notificationTargetsOverride = new HashSet<>();
}

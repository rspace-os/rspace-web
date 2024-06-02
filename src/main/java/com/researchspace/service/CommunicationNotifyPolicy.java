package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.record.BaseRecord;
import java.util.Set;

/** Strategy for calculating who can be notified out of a set of potential targets */
public interface CommunicationNotifyPolicy {
  /**
   * Boolean calculation as to whether a notification should be sent to the supplied set of
   * potentialTargets.
   *
   * @param originator
   * @param type
   * @param record optional, can be <code>null</code> if this notification does not involve records.
   * @param userNames
   * @return
   */
  boolean shouldCreateNotificationFor(
      User originator, NotificationType type, BaseRecord record, Set<String> potentialTargets);

  /** Policy that always returns <code>true</code>. */
  CommunicationNotifyPolicy ALWAYS_NOTIFY =
      new CommunicationNotifyPolicy() {
        @Override
        public boolean shouldCreateNotificationFor(
            User originator,
            NotificationType type,
            BaseRecord record,
            Set<String> potentialTargets) {
          return true;
        }
      };
}

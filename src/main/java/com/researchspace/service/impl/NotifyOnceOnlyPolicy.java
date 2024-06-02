package com.researchspace.service.impl;

import com.researchspace.dao.CommunicationDao;
import com.researchspace.model.User;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.CommunicationNotifyPolicy;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Only notifies a user once that a record has been edited, even though it may be edited multiple
 * times before a target checks their notifications. More specifically this will <b>NOT</b> send a
 * notification if there is already an unread notification:
 *
 * <ul>
 *   <li>from the same user AND
 *   <li>Of the same Notification type AND
 *   <li>Concerning the same record. AND
 *   <li>is in NEW status.
 * </ul>
 *
 * If any of the above conditions are not simultaneously <code>true</code>, a new notification will
 * be sent.
 *
 * <p>This policy therefore avoids filling a target's inbox with a notification each time someone
 * saves a document that the target is listening to.
 *
 * <p>This policy uses database lookups and is assumed to be called within a transactional Hibernate
 * session (i.e., called from a service method).
 */
@Component("notifyOnceOnly")
public class NotifyOnceOnlyPolicy implements CommunicationNotifyPolicy {
  @Autowired private CommunicationDao commDao;

  @Override
  public boolean shouldCreateNotificationFor(
      User originator, NotificationType type, BaseRecord record, Set<String> potentialTargets) {

    if (commDao.findExistingNotificationFor(originator, type, record, potentialTargets)) {
      return false;
    }
    return true;
  }
}

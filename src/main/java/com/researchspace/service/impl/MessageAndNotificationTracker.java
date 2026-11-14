package com.researchspace.service.impl;

import com.researchspace.model.comms.MessageType;
import com.researchspace.model.dtos.NotificationStatus;
import com.researchspace.service.IMessageAndNotificationTracker;
import com.researchspace.webapp.messaging.NotificationMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Tracks message and notification counts for users. When new notifications or messages are
 * generated within the system, this service publishes the updated counts to a topic for each user.
 */
@Service
public class MessageAndNotificationTracker implements IMessageAndNotificationTracker {

  @Autowired private SimpMessagingTemplate messagingTemplate;

  private static final int MAX_QUERY_ATTEMPTS = 3;

  private static class UserUpdates {
    long notificationVersion;
    long messageVersion;
    long specialMessageVersion;
  }

  private final Map<Long, UserUpdates> updates = new ConcurrentHashMap<>();

  private UserUpdates updatesFor(Long userId) {
    return updates.computeIfAbsent(userId, ignored -> new UserUpdates());
  }

  private Map<Long, Integer> userToNotification = new ConcurrentHashMap<>();
  private Map<Long, Integer> userToMessage = new ConcurrentHashMap<>();
  private Map<Long, Integer> userToSpecialMessage = new ConcurrentHashMap<>();

  public void sendNotificationUpdate(Long userId) {
    UserUpdates state = updatesFor(userId);
    synchronized (state) {
      NotificationMessage notification =
          new NotificationMessage(
              getNotificationCountFor(userId),
              getMessageCountFor(userId),
              getSpecialMessageCountFor(userId));
      sendNotificationUpdate(userId, notification);
    }
  }

  public int changeUserNotificationCount(Long userId, Integer incrementAmount) {
    UserUpdates state = updatesFor(userId);
    synchronized (state) {
      state.notificationVersion++;
      int newCount = doChangeCount(userId, incrementAmount, userToNotification);
      sendNotificationUpdate(userId);
      return newCount;
    }
  }

  @Override
  public void refreshNotificationCount(Long userId, IntSupplier committedCount) {
    UserUpdates state = updatesFor(userId);
    for (int attempt = 0; attempt < MAX_QUERY_ATTEMPTS; attempt++) {
      long version;
      synchronized (state) {
        version = state.notificationVersion;
      }
      int count = committedCount.getAsInt();
      synchronized (state) {
        if (state.notificationVersion != version) {
          // A newer publication landed during the query; requery instead of overwriting it.
          continue;
        }
        userToNotification.put(userId, count);
        state.notificationVersion++;
        sendNotificationUpdate(userId);
        return;
      }
    }
  }

  public void clearUserNotificationCount(Long userId) {
    UserUpdates state = updatesFor(userId);
    synchronized (state) {
      state.notificationVersion++;
      userToNotification.put(userId, 0);
      sendNotificationUpdate(userId);
    }
  }

  public Boolean userHasNewNotifications(Long userId) {
    Integer rc = userToNotification.get(userId);
    if (rc == null || rc == 0) {
      return Boolean.FALSE;
    }
    return Boolean.TRUE;
  }

  public Integer getNotificationCountFor(Long userId) {
    return doGetCount(userId, userToNotification);
  }

  public Boolean userHasActiveMessages(Long userId) {
    return doActiveMessages(userId, userToMessage);
  }

  public Integer getMessageCountFor(Long userId) {
    return doGetCount(userId, userToMessage);
  }

  @Override
  public Integer getSpecialMessageCountFor(Long userId) {
    return doGetCount(userId, userToSpecialMessage);
  }

  @Override
  public void updateMessageCount(Long id, int increment, MessageType messageType) {
    if (messageType.isStandardType()) {
      changeUserMessageCount(id, increment);
    } else {
      changeUserSpecialMsgCount(id, increment);
    }
  }

  private int changeUserSpecialMsgCount(Long userId, Integer incrementAmount) {
    UserUpdates state = updatesFor(userId);
    synchronized (state) {
      state.specialMessageVersion++;
      int newCount = doChangeCount(userId, incrementAmount, userToSpecialMessage);
      sendNotificationUpdate(userId);
      return newCount;
    }
  }

  private int changeUserMessageCount(Long userId, int incrementAmount) {
    UserUpdates state = updatesFor(userId);
    synchronized (state) {
      state.messageVersion++;
      int newCount = doChangeCount(userId, incrementAmount, userToMessage);
      sendNotificationUpdate(userId);
      return newCount;
    }
  }

  private int doChangeCount(Long userId, int incrementAmount, Map<Long, Integer> userToCount) {
    Integer crrCount = userToCount.get(userId);
    if (crrCount == null) {
      userToCount.put(userId, incrementAmount);
      return incrementAmount;
    } else {
      int newAmount = crrCount + incrementAmount;
      // never < 0
      newAmount = (newAmount < 0) ? 0 : newAmount;
      userToCount.put(userId, newAmount);
      return newAmount;
    }
  }

  private Integer doGetCount(Long userId, Map<Long, Integer> userToCount) {
    Integer rc = userToCount.get(userId);
    if (rc == null || rc == 0) {
      rc = 0;
    }
    return rc;
  }

  private boolean doActiveMessages(Long userId, Map<Long, Integer> userToCount) {
    Integer rc = userToCount.get(userId);
    if (rc == null || rc == 0) {
      return Boolean.FALSE;
    }
    return Boolean.TRUE;
  }

  @Override
  public void initCount(Long userId, Supplier<NotificationStatus> statusQuery) {
    UserUpdates state = updatesFor(userId);
    long notificationVersion;
    synchronized (state) {
      notificationVersion = state.notificationVersion;
    }
    for (int attempt = 0; attempt < MAX_QUERY_ATTEMPTS; attempt++) {
      long messageVersion;
      long specialMessageVersion;
      synchronized (state) {
        messageVersion = state.messageVersion;
        specialMessageVersion = state.specialMessageVersion;
      }
      NotificationStatus notificationStatus = statusQuery.get();
      synchronized (state) {
        if ((state.messageVersion != messageVersion
                || state.specialMessageVersion != specialMessageVersion)
            && attempt < MAX_QUERY_ATTEMPTS - 1) {
          continue;
        }
        if (state.notificationVersion == notificationVersion) {
          userToNotification.put(userId, notificationStatus.getNotificationCount());
          state.notificationVersion++;
        }
        // Message deltas may have no baseline yet; always install the latest query result.
        userToMessage.put(userId, notificationStatus.getMessageCount());
        state.messageVersion++;
        userToSpecialMessage.put(userId, notificationStatus.getSpecialMessageCount());
        state.specialMessageVersion++;
        sendNotificationUpdate(userId);
        return;
      }
    }
  }

  public void sendNotificationUpdate(Long userId, NotificationMessage notification) {
    messagingTemplate.convertAndSend("/topic/notifications/" + userId, notification);
  }
}

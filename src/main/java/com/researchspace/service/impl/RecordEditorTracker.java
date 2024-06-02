package com.researchspace.service.impl;

import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.core.UniquelyIdentifiable;
import com.researchspace.model.record.Record;
import com.researchspace.session.UserSessionKey;
import com.researchspace.session.UserSessionTracker;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service("recordTracker")
public class RecordEditorTracker {

  private Object editlock = new Object();
  private Object viewlock = new Object();

  Logger log = LoggerFactory.getLogger(RecordEditorTracker.class);
  private Map<Long, Map<UserSessionKey, Integer>> recordEditing =
      new ConcurrentHashMap<Long, Map<UserSessionKey, Integer>>();

  private Map<Long, Set<String>> recordViewing = new ConcurrentHashMap<Long, Set<String>>();

  /**
   * Will request an edit lock on the record, and obtain a lock if another user is not currently
   * editing and the requesting user is currently active. <br>
   * This is an atomic operation. If the Edit lock is obtained, an {@link EditStatus} of type
   * EDIT_MODE is returned.<br>
   * A user who acquires an edit lock on a document can release the lock by calling unlockRecord().
   * <br>
   * If this is never called, the edit-lock will be removed when the user logs out (this behaviour
   * is external to this method though, and depends on 'activeUsers' holding an up-to-date list of
   * active users).
   *
   * @param record
   * @param userChecking
   * @param activeUsers
   * @return An {@link EditStatus} object
   */
  public EditStatus attemptToEdit(
      Long recordId,
      User userChecking,
      UserSessionTracker activeUsers,
      Supplier<String> sessionIDProvider) {

    EditStatus editStatus;
    Map<UserSessionKey, Integer> usersEditing = null;

    // synchronize as little as possible. We don't synch on the underlying
    // map in order that it will be accessible to other methods.
    synchronized (editlock) {
      if (recordEditing.containsKey(recordId)) {

        usersEditing = recordEditing.get(recordId);
        UserSessionKey currentUserSessionKey = (UserSessionKey) usersEditing.keySet().toArray()[0];
        String currentUserEditing = currentUserSessionKey.getUsername();

        if (!currentUserEditing.equals(userChecking.getUsername())
            && activeUsers.getActiveUsers().contains(currentUserEditing)) {
          return EditStatus.CANNOT_EDIT_OTHER_EDITING;
        }

        UserSessionKey key =
            new UserSessionKey(userChecking.getUsername(), sessionIDProvider.get());
        Integer count = 0;
        if (usersEditing.containsKey(key)) {
          count = usersEditing.get(key);
        }
        usersEditing.put(key, count + 1);

      } else {
        usersEditing = new HashMap<UserSessionKey, Integer>();
        UserSessionKey key =
            new UserSessionKey(userChecking.getUsername(), sessionIDProvider.get());
        usersEditing.put(key, 1);
      }

      recordEditing.put(recordId, usersEditing);
      editStatus = EditStatus.EDIT_MODE;

      log.info(
          "edit locked obtained for {}, edit count for record [{}] is {}",
          userChecking.getUsername(),
          recordId,
          recordEditing.get(recordId));
    } // end synchronized block

    return editStatus;
  }

  /**
   * Releases the edit lock
   *
   * @param edited
   * @param user
   * @return The user's username or empty string if this user wasn't actually editing the document.
   */
  public String unlockRecord(
      UniquelyIdentifiable edited, User user, Supplier<String> sessionIDProvider) {

    Map<UserSessionKey, Integer> usersEditing = null;

    synchronized (editlock) {
      if (user != null && edited != null) {
        if (recordEditing.containsKey(edited.getId())) {

          usersEditing = recordEditing.get(edited.getId());
          UserSessionKey key = new UserSessionKey(user.getUsername(), sessionIDProvider.get());

          if (usersEditing.containsKey(key)) {
            Integer count = usersEditing.get(key);
            count--;
            if (count == 0) {
              usersEditing.remove(key);
              if (usersEditing.isEmpty()) {
                recordEditing.remove(edited.getId());
              }
            } else {
              usersEditing.put(key, count);
              recordEditing.put(edited.getId(), usersEditing);
            }
            return user.getUsername();
          }
        }
      }
    } // end synchronized block

    return "";
  }

  public void removeLockedRecordInSession(String sessionId) {
    synchronized (editlock) {
      for (Entry<Long, Map<UserSessionKey, Integer>> entry : recordEditing.entrySet()) {
        Map<UserSessionKey, Integer> usersEditing = entry.getValue();
        for (UserSessionKey key : usersEditing.keySet()) {
          if (key.getSession().equals(sessionId)) {
            String username = key.getUsername();
            UserSessionKey usersessionkey = new UserSessionKey(username, sessionId);
            usersEditing.remove(usersessionkey);
            if (usersEditing.isEmpty()) {
              recordEditing.remove(entry.getKey());
            }
          }
        }
      }
    } // end synchronized block
  }

  /**
   * Gets the current editor for the specified record. This may be the current subject
   *
   * @param recordId The database id of the current record
   * @return The username of the current editor, or <code>null</code> if there is no current editor
   */
  public String getEditingUserForRecord(Long recordId) {
    synchronized (editlock) {
      if (recordEditing.containsKey(recordId)) {
        Map<UserSessionKey, Integer> usersEditing = recordEditing.get(recordId);
        UserSessionKey currentUserSessionKey = (UserSessionKey) usersEditing.keySet().toArray()[0];
        String currentUserEditing = currentUserSessionKey.getUsername();
        return currentUserEditing;
      }
      return null;
    }
  }

  /**
   * Boolean test for whether a user is editing a record. This may be out-of-date as it is not
   * synchronized with the edit lock/remove cycle
   *
   * @param record
   * @return The Username of the editing user - might be <code>empty</code> or the current user.
   */
  public Optional<String> isEditing(UniquelyIdentifiable record) {
    String currentUserEditing = null;
    synchronized (editlock) {
      if (recordEditing.containsKey(record.getId())) {
        Map<UserSessionKey, Integer> usersEditing = recordEditing.get(record.getId());
        UserSessionKey currentUserSessionKey = (UserSessionKey) usersEditing.keySet().toArray()[0];
        currentUserEditing = currentUserSessionKey.getUsername();
      }
    }
    return Optional.ofNullable(currentUserEditing);
  }

  /**
   * Boolean test for whether someone else is editing a record.
   *
   * @param record
   * @param username of current sessionuser/subject
   * @return <code>true</code> if another user has edit lock on <code>record</code>
   */
  public Boolean isSomeoneElseEditing(UniquelyIdentifiable record, String subjectUsername) {
    return isEditing(record).map(s -> !s.equals(subjectUsername)).orElse(Boolean.FALSE);
  }

  /**
   * NOT USED Adds a user to list of viewers of a record. Currently there is no internal mechanism
   * to enforce tha a user is a viewer OR an editor but not both.
   *
   * @param r
   * @param username
   */
  public void addViewerToRecord(UniquelyIdentifiable r, String username) {
    synchronized (viewlock) {
      recordViewing.putIfAbsent(r.getId(), new HashSet<String>());
      Set<String> viewers = recordViewing.get(r.getId());
      viewers.add(username);
    }
  }

  /**
   * NOT USED Removes a user from the list of viewers of a record. Currently there is no internal
   * mechanism to enforce tha a user is a viewer OR an editor but not both.
   *
   * @param r
   * @param username
   */
  public void removeViewerFromRecord(UniquelyIdentifiable r, String username) {
    recordViewing.putIfAbsent(r.getId(), new HashSet<String>());
    synchronized (viewlock) {
      Set<String> viewers = recordViewing.get(r.getId());
      viewers.remove(username);
      if (viewers.size() == 0) {
        recordViewing.remove(r.getId());
      }
    }
  }

  /**
   * NOT USED Returns a <b>read-only</b> view of the current list of viewers for the specified
   * record.<br>
   * To modify the record's viewers, use the methods {@link #removeViewerFromRecord(Record, User)}
   * and {@link #addViewerToRecord(Record, User)} in this class. <br>
   * Attempting to modify the returned list will result in a runtime Exception.
   *
   * @param r
   * @return A <code>Set </code> of the current list of viewers.
   */
  @SuppressWarnings("unchecked")
  public Set<String> getViewersForRecord(Long recordId) {
    Set<String> viewers = recordViewing.get(recordId);
    if (viewers == null) {
      return Collections.unmodifiableSet(Collections.EMPTY_SET);
    }
    return Collections.unmodifiableSet(viewers);
  }
}

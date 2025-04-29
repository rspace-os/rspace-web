package com.researchspace.analytics.service;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.core.Person;
import javax.servlet.http.HttpServletRequest;
import org.springframework.context.event.EventListener;

/** Registering events for analytics. */
public interface AnalyticsManager {

  String RECORD_CREATED_EVENT_SPEL =
      "#event != null and #event.auditAction.name() =='CREATE'"
          + " and #event.auditedObject instanceof T(com.researchspace.model.record.BaseRecord)";

  /**
   * To be called whenever a new non-temporary user is created in RSpace.
   *
   * <p>This is to track users identities in Analytics.
   *
   * @param user the created user
   */
  void userCreated(User user);

  /**
   * To be called whenever action of existing RSpace user triggered invitation of an external user
   * whose email was not yet in a system.
   *
   * <p>This is to track viral coefficient number.
   *
   * @param inviter existing RSpace user
   * @param invitee new user to whom invitation was sent
   * @param req
   */
  void joinGroupInvitationSent(User inviter, User invitee, HttpServletRequest req);

  void shareDocInvitationSent(User inviter, User invitee, HttpServletRequest req);

  /**
   * To be called whenever external user sign up for an account on cloud instance, either following
   * invitation link, or after clicking 'signup' from login page.
   *
   * <p>This is to track viral coefficient number.
   *
   * @param user the user who signed up
   * @param signupFromInvitation
   * @param req
   */
  void userSignedUp(User user, boolean signupFromInvitation, HttpServletRequest req);

  /**
   * To be called after user log in.
   *
   * <p>This is to get an idea of how often people log in.
   *
   * @param user who logged in
   * @param req
   */
  void userLoggedIn(User user, HttpServletRequest req);

  /**
   * To be called after user log out.
   *
   * <p>This is to get an idea how long the session last.
   *
   * @param user who logged out
   * @param req
   */
  void userLoggedOut(User user, HttpServletRequest req);

  /**
   * To be called after user preferences change. For example, Chameleon tours are enabled or
   * disabled.
   *
   * @param user whose preferences changed
   * @param req
   */
  void usersPreferencesChanged(User user, HttpServletRequest req);

  /**
   * Reacts to RecordCreated events
   *
   * @param event
   */
  // if refactoring the name of HistoricalEvent, remember to alter spel to new name
  @EventListener(condition = RECORD_CREATED_EVENT_SPEL)
  void recordCreated(HistoricalEvent event);

  /**
   * Registers specific calls to public API made with API Key, or fact of generating OAuth token
   * through username/password flow.
   */
  void apiAccessed(User user, boolean apiKeyAccess, HttpServletRequest req);

  /**
   * To calculate and upload file usage for every user in the system
   *
   * <p>Notifies segment about current file/document usage.
   */
  void uploadUsersDiskUsage();

  /**
   * Returns string representing userId as known by the analytics, which can also be used as opaque
   * user identifier.
   *
   * @param user
   * @return analyticsUserId or null if license service is unavailable
   */
  String getAnalyticsUserId(Person user);

  /**
   * Returns the key used by analytics server to identify the call. If analytics is not enabled
   * returns null instead.
   *
   * @return analytics server key or null if not available
   */
  String getAnalyticsServerKey();

  /**
   * Tracks a chat app event identified by its {@link AnalyticsEvent} type.
   *
   * @param user
   * @param eventType
   * @param event
   */
  void trackChatApp(User user, String eventType, AnalyticsEvent event);

  /**
   * Request analytics client to send currently stored messages to segment. The method executes
   * asynchronously.
   */
  void flushAnalyticsClient();

  /**
   * Notify some usage of DMPs
   *
   * @param user
   */
  void dmpsViewed(User user);
}

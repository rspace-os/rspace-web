package com.researchspace.analytics.service.impl;

import static com.researchspace.session.SessionAttributeUtils.FIRST_LOGIN;

import com.researchspace.admin.service.SysAdminManager;
import com.researchspace.admin.service.UserUsageInfo;
import com.researchspace.analytics.service.AnalyticsEvent;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import com.researchspace.auth.BaseLoginHelperImpl;
import com.researchspace.core.util.DateUtil;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.licensews.LicenseServerUnavailableException;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.core.Person;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.events.GroupEventType;
import com.researchspace.model.events.GroupMembershipEvent;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.GroupManager;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.LicenseService;
import com.segment.analytics.Analytics;
import com.segment.analytics.messages.IdentifyMessage;
import com.segment.analytics.messages.TrackMessage;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Setter(AccessLevel.PROTECTED) // For testing
@Service("analyticsManager")
public class AnalyticsManagerImpl implements AnalyticsManager {
  public static final Long EVENT_NEVER_HAPPENED = -100L;

  private static final Logger log = LoggerFactory.getLogger(AnalyticsManagerImpl.class);
  public static final String ANALYTICS_SEGMENT_TYPE = "segment";

  private @Autowired LicenseService licenseService;
  private @Autowired SysAdminManager sysAdminManager;
  private @Autowired @Lazy IPropertyHolder properties;
  private @Autowired @Lazy GroupManager groupManager;
  private @Autowired @Lazy IntegrationsHandler integrationsHandler;

  @Value("${analytics.enabled}")
  private String analyticsEnabled;

  @Value("${analytics.server.host}")
  private String analyticsServerHost;

  @Value("${analytics.server.key}")
  private String analyticsServerKey;

  @Value("${analytics.server.type}")
  private String analyticsServerType;

  private Analytics analyticsClient;

  /*
   * initialise analytics client when all deployment properties are loaded AND
   * analytics.enabled property is set to 'true' AND the analytics server type is 'segment'
   */
  @PostConstruct
  private void initialiseSegmentAnalyticsClientIfAllPropertiesAvailable() {
    if (Boolean.parseBoolean(analyticsEnabled)
        && ANALYTICS_SEGMENT_TYPE.equals(analyticsServerType)
        && StringUtils.isNotBlank(analyticsServerKey)
        && StringUtils.isNotBlank(analyticsServerHost)) {
      SegmentAnalyticsLogAdapter analyticsLogger = new SegmentAnalyticsLogAdapter();
      analyticsClient =
          Analytics.builder(analyticsServerKey)
              .endpoint(analyticsServerHost)
              .log(analyticsLogger)
              .build();
    }
  }

  @Override
  public void userCreated(User user) {
    String userId = returnIfUserNull(user, "ignoring userCreated event");
    if (userId == null) {
      return;
    }
    sendSegmentIdentifyEvent(user, userId);
  }

  @Override
  public void joinGroupInvitationSent(User inviter, User invitee, HttpServletRequest req) {
    invitationSent(inviter, invitee, AnalyticsEvent.JOIN_GROUP_INVITE, req);
  }

  @Override
  public void shareDocInvitationSent(User inviter, User invitee, HttpServletRequest req) {
    invitationSent(inviter, invitee, AnalyticsEvent.SHARE_DOC_INVITE, req);
  }

  public void invitationSent(
      User inviter, User invitee, AnalyticsEvent invitationEvent, HttpServletRequest req) {
    String userId = returnIfUserNull(inviter, "ignoring invitation event");
    if (userId == null) {
      return;
    }

    log.info("passing invitation event to analytics server");

    Map<String, Object> props = new HashMap<>();
    props.put(AnalyticsProperty.EMAIL.getLabel(), inviter.getEmail());
    props.put(AnalyticsProperty.ROLES.getLabel(), inviter.getRolesNamesAsString());
    props.put(AnalyticsProperty.INSTITUTION.getLabel(), inviter.getAffiliation());

    props.put(AnalyticsProperty.CLIENT_REMOTE_ADDR.getLabel(), getClientRemoteAddress(req));
    props.put(AnalyticsProperty.INVITED_EMAIL.getLabel(), invitee.getEmail());

    track(userId, invitationEvent.getLabel(), props);
  }

  @Override
  public void userSignedUp(User user, boolean signupFromInvitation, HttpServletRequest req) {
    String userId = returnIfUserNull(user, "ignoring signup event");
    if (userId == null) {
      return;
    }

    log.info("passing signup event to analytics server");

    Map<String, Object> props = new HashMap<>();
    props.put(AnalyticsProperty.EMAIL.getLabel(), user.getEmail());
    props.put(AnalyticsProperty.INSTITUTION.getLabel(), user.getAffiliation());

    props.put(AnalyticsProperty.SIGNUP_FROM_INVITATION.getLabel(), signupFromInvitation);
    props.put(AnalyticsProperty.CLIENT_REMOTE_ADDR.getLabel(), getClientRemoteAddress(req));

    track(userId, AnalyticsEvent.SIGNUP.getLabel(), props);
  }

  @Override
  public void userLoggedIn(User user, HttpServletRequest req) {
    String userId = returnIfUserNull(user, "ignoring login event");
    if (userId == null) {
      return;
    }
    log.info("passing login event to analytics server");

    Map<String, Object> props = new HashMap<>();
    props.put(AnalyticsProperty.CLIENT_REMOTE_ADDR.getLabel(), getClientRemoteAddress(req));

    track(userId, AnalyticsEvent.LOGIN.getLabel(), props);
    sendSegmentIdentifyEvent(req.getSession(), user, userId);
  }

  private String returnIfUserNull(Person user, String logMsg) {
    log.info("user  is {} - id is {}", user.toString(), user.getId());
    String userId = null;
    if (isAnalyticsEnabled()) {
      userId = getAnalyticsUserId(user);
    }
    if (userId == null) {
      log.info(logMsg);
    }
    return userId;
  }

  @Override
  public void userLoggedOut(User user, HttpServletRequest req) {
    String userId = returnIfUserNull(user, "ignoring logout event");
    if (userId == null) {
      return;
    }

    log.info("passing logout event to analytics server");

    Map<String, Object> props = new HashMap<>();
    props.put(AnalyticsProperty.CLIENT_REMOTE_ADDR.getLabel(), getClientRemoteAddress(req));

    track(userId, AnalyticsEvent.LOGOUT.getLabel(), props);
  }

  @Override
  public void usersPreferencesChanged(User user, HttpServletRequest req) {
    String userId = returnIfUserNull(user, "ignoring users preferences changed event");
    if (userId == null) {
      return;
    }
    sendSegmentIdentifyEvent(req.getSession(), user, userId);
  }

  public void recordCreated(HistoricalEvent event) {
    log.info("Record created!");
    doRecordCreated((BaseRecord) event.getAuditedObject(), event.getSubject());
  }

  @Override
  public void publicApiUsed(User user, HttpServletRequest req) {
    String userId = returnIfUserNull(user, "ignoring api used event");
    if (userId == null) {
      return;
    }
    String requestURI = req.getRequestURI();
    Map<String, Object> props = new HashMap<>();
    props.put(AnalyticsProperty.API_METHOD.getLabel(), req.getMethod());
    props.put(AnalyticsProperty.API_URI.getLabel(), requestURI);
    props.put(AnalyticsProperty.RSPACE_URL.getLabel(), properties.getServerUrl());
    props.put(
        AnalyticsProperty.API_AUTHENTICATED_BY.getLabel(),
        user.getAuthenticatedBy() != null ? user.getAuthenticatedBy().toString() : null);
    AnalyticsEvent event = AnalyticsEvent.PUBLIC_API_USED;
    track(userId, event.getLabel(), props);
  }

  private boolean isInventoryApiRequest(String requestURI) {
    return requestURI.contains(BaseApiInventoryController.API_INVENTORY_V1)
        || requestURI.startsWith("/app/api/v1/units")
        || requestURI.startsWith("/app/api/v1/forms");
  }

  private void doRecordCreated(BaseRecord record, Person creator) {
    String userId = returnIfUserNull(creator, "ignoring record created event");
    if (userId == null) {
      return;
    }

    log.info("passing record created event to analytics server");

    Map<String, Object> props = new HashMap<>();
    props.put(AnalyticsProperty.RS_RECORD_TYPE.getLabel(), getRecordTypeName(record));

    String formName = "";
    if (record.isStructuredDocument()) {
      StructuredDocument recordSDoc = (StructuredDocument) record;
      if (recordSDoc.getForm() != null) {
        formName = recordSDoc.getFormName();
      }
    }
    props.put(AnalyticsProperty.RS_FORM.getLabel(), formName);

    track(userId, AnalyticsEvent.RECORD_CREATE.getLabel(), props);
  }

  private String getRecordTypeName(BaseRecord record) {
    String recordType;

    if (record.hasType(RecordType.FOLDER)) {
      recordType = RecordType.FOLDER.getString();
    } else if (record.hasType(RecordType.NOTEBOOK)) {
      recordType = RecordType.NOTEBOOK.getString();
    } else if (record.hasType(RecordType.TEMPLATE)) {
      recordType = RecordType.TEMPLATE.getString();
    } else if (record.hasType(RecordType.SNIPPET)) {
      recordType = RecordType.SNIPPET.getString();
    } else if (record.isStructuredDocument()) {
      recordType = RecordType.NORMAL.getString();
      if (record.isNotebookEntry()) {
        recordType += "-NB";
      }
    } else {
      recordType = record.getType();
    }
    return recordType;
  }

  @Override
  public void uploadUsersDiskUsage() {
    if (!isAnalyticsEnabled()) {
      log.debug("analytics not enabled, ignoring upload users disk usage request");
      return;
    }

    log.info("passing disk usage information to analytics server");

    PaginationCriteria<User> allUsersPgCrit = PaginationCriteria.createDefaultForClass(User.class);
    allUsersPgCrit.setResultsPerPage(Integer.MAX_VALUE);
    allUsersPgCrit.setOrderBy(SysAdminManager.ORDER_BY_FILE_USAGE);

    User dummySysadmin = new User();
    dummySysadmin.addRole(Role.SYSTEM_ROLE);

    ISearchResults<UserUsageInfo> userUsageInfo =
        sysAdminManager.getUserUsageInfo(dummySysadmin, allUsersPgCrit);
    List<UserUsageInfo> results = userUsageInfo.getResults();

    for (UserUsageInfo usageInfo : results) {
      uploadDiskUsageForUser(usageInfo);
    }
  }

  private void uploadDiskUsageForUser(UserUsageInfo usageInfo) {
    String userId = getAnalyticsUserId(usageInfo.getUserInfo().getId());
    Map<String, Object> props = new HashMap<>();
    Long fileUsageInKB = usageInfo.getFileUsage() / 1024;
    props.put(AnalyticsProperty.DISK_USAGE.getLabel(), fileUsageInKB);
    track(userId, AnalyticsEvent.DISK_USAGE.getLabel(), props);
  }

  @Override
  public void trackChatApp(User user, String eventType, AnalyticsEvent event) {
    log.info(
        "Logging chat event for user {} [{}] for event {}",
        user.getUsername(),
        user.getId(),
        event.getLabel());
    String userId = returnIfUserNull(user, "ignoring chat event " + event.getLabel());
    if (userId == null) {
      return;
    }
    log.info("Person is {} with id {}", user, ((Person) user).getId());
    Map<String, Object> props = new HashMap<>();
    props.put(AnalyticsProperty.CHAT_EVENT_TYPE.getLabel(), eventType);
    log.info("User id is {}", userId);
    track(userId, event.getLabel(), props);
  }

  @Override
  public void dmpsViewed(User user) {
    String userId = returnIfUserNull(user, "ignoring dmp usage event");
    if (userId == null) {
      return;
    }
    track(userId, AnalyticsEvent.DMP_USED.getLabel(), Collections.emptyMap());
  }

  @Override
  public String getAnalyticsUserId(Person user) {
    return getAnalyticsUserId(user.getId());
  }

  private String getAnalyticsUserId(Object userId) {
    String serverUniqueId;
    try {
      serverUniqueId = licenseService.getServerUniqueId().orElse("Unknown-server-uniqueID");
    } catch (LicenseServerUnavailableException e) {
      log.warn("license server unavailable, couldn't generate analyticsUserId");
      return null;
    }
    log.info("Generated user id of {}@{}", userId, serverUniqueId);
    return userId + "@" + serverUniqueId;
  }

  @Override
  public String getAnalyticsServerKey() {
    if (!isAnalyticsEnabled()) {
      log.debug("analytics not enabled, getAnalyticsServerKey returning null");
      return null;
    }
    return analyticsServerKey;
  }

  private void sendSegmentIdentifyEvent(User user, String userId) {
    sendSegmentIdentifyEvent(null, user, userId);
  }

  private void sendSegmentIdentifyEvent(HttpSession session, User user, String userId) {
    log.info("passing identify event to analytics server");

    Map<String, Object> identifyTraits = new HashMap<>();
    identifyTraits.put(AnalyticsProperty.FIRST_NAME.getLabel(), user.getFirstName());
    identifyTraits.put(AnalyticsProperty.LAST_NAME.getLabel(), user.getLastName());
    identifyTraits.put(AnalyticsProperty.EMAIL.getLabel(), user.getEmail());
    identifyTraits.put(AnalyticsProperty.ROLE.getLabel(), user.toPublicInfo().getRole());
    identifyTraits.put(AnalyticsProperty.RS_USERNAME.getLabel(), user.getUsername());
    identifyTraits.put(AnalyticsProperty.USER_ID.getLabel(), userId);
    identifyTraits.put(AnalyticsProperty.IN_GROUP.getLabel(), user.getGroups().size() > 0);
    identifyTraits.put(AnalyticsProperty.AUTOSHARE_ENABLED.getLabel(), user.hasAutoshareGroups());
    identifyTraits.put(AnalyticsProperty.RSPACE_VERSION.getLabel(), properties.getVersionMessage());
    identifyTraits.put(AnalyticsProperty.RSPACE_URL.getLabel(), properties.getServerUrl());
    identifyTraits.put(
        AnalyticsProperty.CREATED_AT.getLabel(),
        DateUtil.convertDateToISOFormat(user.getCreationDate(), null));
    identifyTraits.put(
        AnalyticsProperty.INSTITUTION.getLabel(),
        user.getAffiliation() != null ? user.getAffiliation() : "unknown");
    identifyTraits.put(
        AnalyticsProperty.CUSTOMER_NAME.getLabel(),
        licenseService.getCustomerName().orElse("UNKNOWN_CUSTOMER"));

    List<GroupMembershipEvent> events = groupManager.getGroupEventsForUser(user, user);
    identifyTraits.put(
        AnalyticsProperty.DAYS_SINCE_LAST_GROUP_JOIN.getLabel(),
        getDaysSinceLastEvent(events, GroupEventType.JOIN));
    identifyTraits.put(
        AnalyticsProperty.DAYS_SINCE_LAST_AUTOSHARE.getLabel(),
        getDaysSinceLastEvent(events, GroupEventType.ENABLED_AUTOSHARING));

    IntegrationInfo info =
        integrationsHandler.getIntegration(user, IntegrationsHandler.ONBOARDING_APP_NAME);
    identifyTraits.put(
        AnalyticsProperty.ONBOARDING_ENABLED.getLabel(),
        info != null && info.isAvailable() && info.isEnabled());

    if (session != null) {
      identifyTraits.put(
          AnalyticsProperty.RECENTLY_SIGNED_UP.getLabel(),
          ((Boolean) session.getAttribute(BaseLoginHelperImpl.RECENT_SIGNUP_ATTR)
              || ((Boolean) session.getAttribute(FIRST_LOGIN))));
    }

    identify(userId, identifyTraits);
  }

  private Long getDaysSinceLastEvent(
      List<GroupMembershipEvent> events, GroupEventType groupEventType) {
    return events.stream()
        .filter(e -> groupEventType.equals(e.getGroupEventType()))
        .min((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
        .map(this::getDaysAgo)
        .orElse(EVENT_NEVER_HAPPENED);
  }

  private Long getDaysAgo(GroupMembershipEvent e) {
    long diffInMillis = Math.abs((new Date()).getTime() - e.getTimestamp().getTime());
    return TimeUnit.DAYS.convert(diffInMillis, TimeUnit.MILLISECONDS);
  }

  void track(String userId, String label, Map<String, Object> props) {
    analyticsClient.enqueue(TrackMessage.builder(label).userId(userId).properties(props));
  }

  void identify(String userId, Map<String, Object> identifyTraits) {
    analyticsClient.enqueue(IdentifyMessage.builder().userId(userId).traits(identifyTraits));
  }

  private boolean isAnalyticsEnabled() {
    return analyticsClient != null;
  }

  private String getClientRemoteAddress(HttpServletRequest req) {
    return RequestUtil.remoteAddr(req);
  }

  @Override
  public void flushAnalyticsClient() {
    analyticsClient.flush();
  }
}

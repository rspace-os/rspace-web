package com.researchspace.analytics.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.admin.service.SysAdminManager;
import com.researchspace.admin.service.UserUsageInfo;
import com.researchspace.analytics.service.AnalyticsEvent;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.auth.BaseLoginHelperImpl;
import com.researchspace.core.util.DateUtil;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.events.GroupEventType;
import com.researchspace.model.events.GroupMembershipEvent;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.GroupManager;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.LicenseService;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.segment.analytics.Analytics;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.util.ReflectionUtils;

/** Unit tests covering analytics. */
@RunWith(ConditionalTestRunner.class)
public class AnalyticsManagerTest extends SpringTransactionalTest {

  static final String SERVER_ID = "some-server-id";

  /* Test-spy class for stubbing out calls to Segment and record method invocations */
  static class AnalyticsManagerImplTSS extends AnalyticsManagerImpl {
    String userId, label;
    Map<String, Object> props;
    Map<String, Object> traits;
    boolean identified = false;

    @Override
    void identify(String userId, Map<String, Object> identifyTraits) {
      this.userId = userId;
      this.identified = true;
      this.traits = identifyTraits;
    }

    @Override
    void track(String userId, String label, Map<String, Object> props) {
      this.userId = userId;
      this.label = label;
      this.props = props;
    }
  }

  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock private SysAdminManager mockSysAdminManager;
  @Mock private GroupManager groupManager;
  @Mock private LicenseService licenseService;
  @Autowired private IPropertyHolder propertyHolder;
  @Autowired private IntegrationsHandler integrationsHandler;

  private AnalyticsManager analyticsManager;

  private AnalyticsManagerImplTSS analyticsManagerImplTSS;
  private User testUser;
  private MockHttpServletRequest mockRequest;
  private final RecordFactory recordFactory = new RecordFactory();

  @Before
  public void setUp() {
    analyticsManagerImplTSS = new AnalyticsManagerImplTSS();
    Analytics dummyAnalyticsClient = Analytics.builder("any").build();
    analyticsManagerImplTSS.setAnalyticsClient(dummyAnalyticsClient);
    analyticsManagerImplTSS.setLicenseService(licenseService);
    analyticsManagerImplTSS.setSysAdminManager(mockSysAdminManager);
    analyticsManagerImplTSS.setGroupManager(groupManager);
    analyticsManagerImplTSS.setProperties(propertyHolder);
    analyticsManagerImplTSS.setIntegrationsHandler(integrationsHandler);
    analyticsManager = analyticsManagerImplTSS;

    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("analyticsUser"), "ROLE_PI");

    mockRequest = new MockHttpServletRequest();
    mockRequest.setRemoteAddr("127.0.0.1");
    MockHttpSession mockHttpSession = new MockHttpSession();
    mockHttpSession.setAttribute(BaseLoginHelperImpl.RECENT_SIGNUP_ATTR, true);
    mockHttpSession.setAttribute(SessionAttributeUtils.FIRST_LOGIN, true);
    mockRequest.setSession(mockHttpSession);
  }

  @Test
  public void analyticsUserIdFormatCheck() {
    when(licenseService.getServerUniqueId()).thenReturn(Optional.of(SERVER_ID));
    String analyticsUserId = analyticsManager.getAnalyticsUserId(testUser);

    assertTrue(
        "analyticsUserId should start with user id, but was: " + analyticsUserId,
        analyticsUserId.startsWith("" + testUser.getId()));
    assertTrue(
        "analyticsUserId should end with license id, but was: " + analyticsUserId,
        analyticsUserId.endsWith(SERVER_ID));
  }

  @Test
  public void analyticsServerKeyFormatCheck() {
    final String testKey = "testKey1234!@#$";
    ((AnalyticsManagerImpl) analyticsManager).setAnalyticsServerKey(testKey);
    assertEquals(testKey, analyticsManager.getAnalyticsServerKey());
  }

  @Test
  public void testUserCreatedEvent() {
    String customerName = "A Customer";
    when(licenseService.getCustomerName()).thenReturn(Optional.of(customerName));

    final String expectedUserId = analyticsManager.getAnalyticsUserId(testUser);

    final Map<String, Object> expectedTraits = new HashMap<>();
    expectedTraits.put(AnalyticsProperty.FIRST_NAME.getLabel(), testUser.getFirstName());
    expectedTraits.put(AnalyticsProperty.LAST_NAME.getLabel(), testUser.getLastName());
    expectedTraits.put(AnalyticsProperty.EMAIL.getLabel(), testUser.getEmail());
    expectedTraits.put(AnalyticsProperty.ROLE.getLabel(), testUser.toPublicInfo().getRole());
    expectedTraits.put(AnalyticsProperty.RS_USERNAME.getLabel(), testUser.getUsername());
    expectedTraits.put(AnalyticsProperty.USER_ID.getLabel(), expectedUserId);
    expectedTraits.put(AnalyticsProperty.IN_GROUP.getLabel(), testUser.getGroups().size() > 0);
    expectedTraits.put(
        AnalyticsProperty.AUTOSHARE_ENABLED.getLabel(), testUser.hasAutoshareGroups());
    expectedTraits.put(
        AnalyticsProperty.RSPACE_VERSION.getLabel(), propertyHolder.getVersionMessage());
    expectedTraits.put(AnalyticsProperty.RSPACE_URL.getLabel(), propertyHolder.getServerUrl());
    expectedTraits.put(AnalyticsProperty.ONBOARDING_ENABLED.getLabel(), false);
    expectedTraits.put(
        AnalyticsProperty.CREATED_AT.getLabel(),
        DateUtil.convertDateToISOFormat(testUser.getCreationDate(), null));
    expectedTraits.put(AnalyticsProperty.INSTITUTION.getLabel(), testUser.getAffiliation());
    expectedTraits.put(AnalyticsProperty.CUSTOMER_NAME.getLabel(), customerName);
    expectedTraits.put(
        AnalyticsProperty.DAYS_SINCE_LAST_GROUP_JOIN.getLabel(),
        AnalyticsManagerImpl.EVENT_NEVER_HAPPENED);
    expectedTraits.put(
        AnalyticsProperty.DAYS_SINCE_LAST_AUTOSHARE.getLabel(),
        AnalyticsManagerImpl.EVENT_NEVER_HAPPENED);

    analyticsManager.userCreated(testUser);

    assertEquals(expectedUserId, analyticsManagerImplTSS.userId);
    assertEquals(expectedTraits, analyticsManagerImplTSS.traits);
  }

  @Test
  public void testDaysSinceLastEvent() {
    User user = createAndSaveUserIfNotExists("pi", "ROLE_PI");
    Group grp = TestFactory.createAnyGroup(user);

    GroupMembershipEvent e49 = setEventAsNDaysAgo(user, grp, 49);
    GroupMembershipEvent e17 = setEventAsNDaysAgo(user, grp, 17);
    GroupMembershipEvent e35 = setEventAsNDaysAgo(user, grp, 35);

    List<GroupMembershipEvent> rc = TransformerUtils.toList(e49, e17, e35);
    Mockito.when(groupManager.getGroupEventsForUser(user, user)).thenReturn(rc);

    analyticsManager.userLoggedIn(user, mockRequest);

    assertEquals(
        17L,
        analyticsManagerImplTSS.traits.get(
            AnalyticsProperty.DAYS_SINCE_LAST_GROUP_JOIN.getLabel()));

    analyticsManager.userLoggedIn(user, mockRequest);
  }

  private GroupMembershipEvent setEventAsNDaysAgo(User user, Group group, int daysAgo) {
    GroupMembershipEvent e = new GroupMembershipEvent(user, group, GroupEventType.JOIN);
    Field timestampField = ReflectionUtils.findField(GroupMembershipEvent.class, "timestamp");
    Objects.requireNonNull(timestampField).setAccessible(true);
    Instant past = Instant.now().minus(daysAgo, ChronoUnit.DAYS).minus(25, ChronoUnit.SECONDS);
    ReflectionUtils.setField(timestampField, e, new Date(past.toEpochMilli()));
    return e;
  }

  @Test
  public void testInvitationSentEvent() {
    User inviter = testUser;
    User invitee = new User();
    invitee.setEmail("invitee@email.com");

    final String expectedUserId = analyticsManager.getAnalyticsUserId(testUser);

    final Map<String, Object> expectedProps = new HashMap<>();
    expectedProps.put(AnalyticsProperty.EMAIL.getLabel(), inviter.getEmail());
    expectedProps.put(AnalyticsProperty.ROLES.getLabel(), inviter.getRolesNamesAsString());
    expectedProps.put(AnalyticsProperty.INSTITUTION.getLabel(), inviter.getAffiliation());

    expectedProps.put(AnalyticsProperty.CLIENT_REMOTE_ADDR.getLabel(), mockRequest.getRemoteAddr());
    expectedProps.put(AnalyticsProperty.INVITED_EMAIL.getLabel(), invitee.getEmail());
    analyticsManager.joinGroupInvitationSent(inviter, invitee, mockRequest);
    assertEquals(expectedUserId, analyticsManagerImplTSS.userId);
    assertEquals(AnalyticsEvent.JOIN_GROUP_INVITE.getLabel(), analyticsManagerImplTSS.label);
    assertEquals(expectedProps, analyticsManagerImplTSS.props);

    analyticsManager.shareDocInvitationSent(inviter, invitee, mockRequest);
    assertEquals(expectedUserId, analyticsManagerImplTSS.userId);
    assertEquals(AnalyticsEvent.SHARE_DOC_INVITE.getLabel(), analyticsManagerImplTSS.label);
    assertEquals(expectedProps, analyticsManagerImplTSS.props);
  }

  @Test
  public void testUserSignupEvent() {
    final String expectedUserId = analyticsManager.getAnalyticsUserId(testUser);

    final Map<String, Object> expectedProps = new HashMap<>();
    expectedProps.put(AnalyticsProperty.EMAIL.getLabel(), testUser.getEmail());
    expectedProps.put(AnalyticsProperty.INSTITUTION.getLabel(), testUser.getAffiliation());

    expectedProps.put(AnalyticsProperty.SIGNUP_FROM_INVITATION.getLabel(), true);
    expectedProps.put(AnalyticsProperty.CLIENT_REMOTE_ADDR.getLabel(), mockRequest.getRemoteAddr());

    analyticsManager.userSignedUp(testUser, true, mockRequest);

    assertEquals(expectedUserId, analyticsManagerImplTSS.userId);
    assertEquals(AnalyticsEvent.SIGNUP.getLabel(), analyticsManagerImplTSS.label);
    assertEquals(expectedProps, analyticsManagerImplTSS.props);
  }

  @Test
  public void testUserLoginEvent() {
    final String expectedUserId = analyticsManager.getAnalyticsUserId(testUser);

    final Map<String, Object> expectedProps = new HashMap<>();
    expectedProps.put(AnalyticsProperty.CLIENT_REMOTE_ADDR.getLabel(), mockRequest.getRemoteAddr());

    analyticsManager.userLoggedIn(testUser, mockRequest);

    assertEquals(expectedUserId, analyticsManagerImplTSS.userId);
    assertEquals(AnalyticsEvent.LOGIN.getLabel(), analyticsManagerImplTSS.label);
    assertEquals(expectedProps, analyticsManagerImplTSS.props);
    assertTrue(analyticsManagerImplTSS.identified);
  }

  @Test
  public void testUserLogoutEvent() {
    final String expectedUserId = analyticsManager.getAnalyticsUserId(testUser);

    final Map<String, Object> expectedProps = new HashMap<>();
    expectedProps.put(AnalyticsProperty.CLIENT_REMOTE_ADDR.getLabel(), mockRequest.getRemoteAddr());

    analyticsManager.userLoggedOut(testUser, mockRequest);

    assertEquals(expectedUserId, analyticsManagerImplTSS.userId);
    assertEquals(AnalyticsEvent.LOGOUT.getLabel(), analyticsManagerImplTSS.label);
    assertEquals(expectedProps, analyticsManagerImplTSS.props);
  }

  @Test
  public void testRecordCreatedEvent() {
    final String TEST_FORM_NAME = "testForm";
    final String TEST_DOC_NAME = "testDoc";

    final String expectedUserId = analyticsManager.getAnalyticsUserId(testUser);

    final Map<String, Object> expectedProps = new HashMap<>();
    expectedProps.put(AnalyticsProperty.RS_RECORD_TYPE.getLabel(), RecordType.NORMAL.getString());
    expectedProps.put(AnalyticsProperty.RS_FORM.getLabel(), TEST_FORM_NAME);

    RSForm testForm = recordFactory.createExperimentForm(TEST_FORM_NAME, "", testUser);
    StructuredDocument testDoc =
        recordFactory.createStructuredDocument(TEST_DOC_NAME, testUser, testForm);
    analyticsManager.recordCreated(new GenericEvent(testUser, testDoc, AuditAction.CREATE));

    assertEquals(expectedUserId, analyticsManagerImplTSS.userId);
    assertEquals(AnalyticsEvent.RECORD_CREATE.getLabel(), analyticsManagerImplTSS.label);
    assertEquals(expectedProps, analyticsManagerImplTSS.props);
  }

  @Test
  public void isChatAppUsed() {
    final String expectedUserId = analyticsManager.getAnalyticsUserId(testUser);
    analyticsManager.trackChatApp(testUser, "message_post", AnalyticsEvent.SLACK_USED);
    assertEquals(expectedUserId, analyticsManagerImplTSS.userId);
  }

  @Test
  public void isApiUsed() {
    final String expectedUserId = analyticsManager.getAnalyticsUserId(testUser);
    MockHttpServletRequest mockRequest = new MockHttpServletRequest();

    mockRequest.setRequestURI("/api/inventory/v1/samples");
    analyticsManager.apiAccessed(testUser, true, mockRequest);
    assertEquals(expectedUserId, analyticsManagerImplTSS.userId);
    assertEquals("apiKeyUsed", analyticsManagerImplTSS.label);
    assertEquals("/api/inventory/v1/samples", analyticsManagerImplTSS.props.get("apiUri"));

    mockRequest.setRequestURI("/api/v1/documents");
    analyticsManager.apiAccessed(testUser, false, mockRequest);
    assertEquals(expectedUserId, analyticsManagerImplTSS.userId);
    assertEquals("apiOAuthTokenGenerated", analyticsManagerImplTSS.label);
    assertEquals("/api/v1/documents", analyticsManagerImplTSS.props.get("apiUri"));
  }

  @Test
  public void testUploadDiskUsage() {
    final SearchResultsImpl<UserUsageInfo> mockSearchResults = makeUserUsageSearchResults();

    final Map<String, Object> expectedProps = new HashMap<>();
    expectedProps.put(AnalyticsProperty.DISK_USAGE.getLabel(), 2L);

    when(mockSysAdminManager.getUserUsageInfo(
            Mockito.any(User.class), Mockito.any(PaginationCriteria.class)))
        .thenReturn(mockSearchResults);
    when(licenseService.getServerUniqueId()).thenReturn(Optional.of(SERVER_ID));

    analyticsManager.uploadUsersDiskUsage();
    // format is <userId>@<serverId>
    assertEquals(String.format("2@%s", SERVER_ID), analyticsManagerImplTSS.userId);
    assertEquals(AnalyticsEvent.DISK_USAGE.getLabel(), analyticsManagerImplTSS.label);
    assertEquals(expectedProps, analyticsManagerImplTSS.props);
  }

  private SearchResultsImpl<UserUsageInfo> makeUserUsageSearchResults() {
    User user1 = new User();
    user1.setId(1L);
    UserUsageInfo userUsageInfo1 = new UserUsageInfo(user1);
    userUsageInfo1.setFileUsage(25L); // 25 B = 0 kB

    User user2 = new User();
    user2.setId(2L);
    UserUsageInfo userUsageInfo2 = new UserUsageInfo(user2);
    userUsageInfo2.setFileUsage(2049L); // 2049 B = 2 kB

    ArrayList<UserUsageInfo> mockUsages = new ArrayList<>();
    mockUsages.add(userUsageInfo1);
    mockUsages.add(userUsageInfo2);

    return new SearchResultsImpl<>(mockUsages, 1, 2L);
  }

  @Test
  public void analyticsCallsSilentlyIgnoredIfAnalyticsNotEnabled() {
    analyticsManagerImplTSS.setAnalyticsClient(null);

    analyticsManager.userCreated(testUser);
    analyticsManager.joinGroupInvitationSent(testUser, testUser, new MockHttpServletRequest());
    analyticsManager.shareDocInvitationSent(testUser, testUser, new MockHttpServletRequest());
    analyticsManager.userSignedUp(testUser, false, new MockHttpServletRequest());
    analyticsManager.userLoggedIn(testUser, new MockHttpServletRequest());
    analyticsManager.userLoggedOut(testUser, new MockHttpServletRequest());
    analyticsManager.recordCreated(
        new GenericEvent(
            testUser, recordFactory.createFolder("testFolder", testUser), AuditAction.CREATE));
    analyticsManager.uploadUsersDiskUsage();
    analyticsManager.trackChatApp(testUser, "message_post", AnalyticsEvent.SLACK_USED);

    assertNull(analyticsManagerImplTSS.userId);
    assertNull(analyticsManagerImplTSS.label);
    assertNull(analyticsManagerImplTSS.props);
    assertFalse(analyticsManagerImplTSS.identified);
  }

  @Test
  public void recordCreatedEventHandlerSpel() {
    // 'root' is special variable for context object when evaluating SPEL
    String spel = AnalyticsManager.RECORD_CREATED_EVENT_SPEL.replaceAll("event", "root");
    ExpressionParser parser = new SpelExpressionParser();
    // display the value of item.name property
    Expression exp = parser.parseExpression(spel);

    ArchiveResult result = new ArchiveResult();
    User anyUser = TestFactory.createAnyUser("any");
    GenericEvent notMatching = new GenericEvent(anyUser, result, AuditAction.EXPORT);
    StandardEvaluationContext itemContext = new StandardEvaluationContext(notMatching);
    assertFalse(exp.getValue(itemContext, Boolean.class));
    // not match
    GenericEvent matching =
        new GenericEvent(anyUser, TestFactory.createAnySD(), AuditAction.CREATE);
    StandardEvaluationContext itemContext2 = new StandardEvaluationContext(matching);
    assertTrue(exp.getValue(itemContext2, Boolean.class));
  }

  @Test
  public void whenNoLicenseSeverThenEventsReportedWithDefaults() {
    when(licenseService.getServerUniqueId()).thenReturn(Optional.empty());
    when(licenseService.getCustomerName()).thenReturn(Optional.empty());

    analyticsManager.userCreated(testUser);

    assertEquals(
        String.format("%s@Unknown-server-uniqueID", testUser.getId()),
        analyticsManagerImplTSS.userId);
    assertEquals("UNKNOWN_CUSTOMER", analyticsManagerImplTSS.traits.get("customerName"));
  }
}

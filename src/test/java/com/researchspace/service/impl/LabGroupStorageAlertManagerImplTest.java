package com.researchspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.DatabaseUsageByGroupGroupByResult;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.dao.GroupDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.CommunicationNotifyPolicy;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.NotificationConfig;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.UserLocaleService;
import com.researchspace.testutils.TestFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabGroupStorageAlertManagerImplTest {

  private static final long MB = 1_000_000L;
  private static final Instant CHECKED_AT = Instant.parse("2026-11-19T03:30:00Z");
  private static final SystemPropertyName THRESHOLD =
      SystemPropertyName.LABGROUP_STORAGE_ALERT_THRESHOLD_MB;

  @Mock SystemPropertyManager systemPropertyManager;
  @Mock FileMetadataDao fileMetadataDao;
  @Mock GroupDao groupDao;
  @Mock UserDao userDao;
  @Mock CommunicationManager communicationManager;
  @Mock UserLocaleService userLocaleService;
  @Captor ArgumentCaptor<NotificationConfig> config;
  @Captor ArgumentCaptor<String> message;

  private LabGroupStorageAlertManagerImpl manager;
  private User sysadmin1;
  private User secondSysadmin;

  @BeforeEach
  void setUp() {
    manager =
        new LabGroupStorageAlertManagerImpl(
            systemPropertyManager,
            fileMetadataDao,
            groupDao,
            userDao,
            communicationManager,
            new MessageSourceUtils(new JsonMessageSource()),
            userLocaleService,
            Clock.fixed(CHECKED_AT, ZoneOffset.UTC));
    sysadmin1 = sysadmin(Constants.SYSADMIN_UNAME);
    secondSysadmin = sysadmin("sysadmin2");
  }

  // ---- AC2: who is told, and what ----

  @Test
  void groupAtTheThresholdIsReportedOnceToEveryEnabledSysadmin() {
    User disabled = sysadmin("gone");
    disabled.setEnabled(false);
    storedThresholdIs("100");
    usageIs(usage(7L, 100 * MB));
    alreadyReported();
    Group smithLab = labGroup(7L, "Smith Lab", pi("Ada", "Smith"));
    groupsAre(smithLab);
    sysadminsAre(sysadmin1, secondSysadmin, disabled);

    manager.checkLabGroups();

    verify(communicationManager)
        .notify(same(sysadmin1), isNull(), config.capture(), message.capture());
    assertEquals(
        "The members of Lab Group Smith Lab (PI: Ada Smith) are using 100 MB of file storage,"
            + " which has reached the storage alert threshold of 100 MB."
            + " Checked on 19 Nov 2026, 03:30 UTC.",
        message.getValue());
    NotificationConfig sent = config.getValue();
    assertEquals(NotificationType.LAB_GROUP_STORAGE_ALERT, sent.getNotificationType());
    assertThat(sent.getNotificationTargetsOverride())
        .containsExactlyInAnyOrder(sysadmin1, secondSysadmin);
    assertTrue(sent.isBroadcast());
    assertTrue(sent.isNotificationEventPreferenceOverride());
    assertTrue(sent.isIncludeOriginator());
    assertEquals(CommunicationNotifyPolicy.ALWAYS_NOTIFY, sent.getPolicyOverride());
    verify(groupDao).setStorageAlertSent(Set.of(7L), true);
  }

  @Test
  void groupsCrossingInTheSameCheckEachGetTheirOwnAlert() {
    storedThresholdIs("100");
    usageIs(usage(7L, 120 * MB), usage(8L, 150 * MB));
    alreadyReported();
    groupsAre(
        labGroup(7L, "Smith Lab", pi("Ada", "Smith")),
        labGroup(8L, "Jones Lab", pi("Bo", "Jones")));
    sysadminsAre(sysadmin1);

    manager.checkLabGroups();

    verify(communicationManager, times(2)).notify(any(), isNull(), any(), message.capture());
    assertThat(message.getAllValues())
        .satisfiesExactlyInAnyOrder(
            text -> assertThat(text).contains("Smith Lab").doesNotContain("Jones Lab"),
            text -> assertThat(text).contains("Jones Lab").doesNotContain("Smith Lab"));
    verify(groupDao).setStorageAlertSent(Set.of(7L, 8L), true);
  }

  @Test
  void usageAndThresholdAreShownAsInTheGroupsList() {
    storedThresholdIs("1000");
    usageIs(usage(7L, 1_234_560_000L));
    alreadyReported();
    groupsAre(labGroup(7L, "Smith Lab", pi("Ada", "Smith")));
    sysadminsAre(sysadmin1);

    manager.checkLabGroups();

    verify(communicationManager).notify(any(), isNull(), any(), message.capture());
    assertThat(message.getValue())
        .contains("are using 1,234.6 MB of file storage")
        .contains("threshold of 1,000 MB");
  }

  @Test
  void everyPiIsNamedInAlphabeticalOrder() {
    storedThresholdIs("1");
    usageIs(usage(7L, 5 * MB));
    alreadyReported();
    Group group = labGroup(7L, "Two Heads", pi("Zoe", "Young"));
    group.addMember(pi("Ada", "Smith"), RoleInGroup.PI);
    groupsAre(group);
    sysadminsAre(sysadmin1);

    manager.checkLabGroups();

    verify(communicationManager).notify(any(), isNull(), any(), message.capture());
    assertThat(message.getValue()).contains("(PI: Ada Smith, Zoe Young)");
  }

  @Test
  void piNamesAreOrderedWithoutRegardToCase() {
    storedThresholdIs("1");
    usageIs(usage(7L, 5 * MB));
    alreadyReported();
    Group group = labGroup(7L, "Mixed Case", pi("Bob", "Smith"));
    group.addMember(pi("alice", "Young"), RoleInGroup.PI);
    groupsAre(group);
    sysadminsAre(sysadmin1);

    manager.checkLabGroups();

    verify(communicationManager).notify(any(), isNull(), any(), message.capture());
    assertThat(message.getValue()).contains("(PI: alice Young, Bob Smith)");
  }

  @Test
  void groupAndPiNamesAreEscapedInTheMessage() {
    storedThresholdIs("1");
    usageIs(usage(7L, 5 * MB));
    alreadyReported();
    groupsAre(labGroup(7L, "<script>alert(1)</script>", pi("Ada", "<b>Smith</b>")));
    sysadminsAre(sysadmin1);

    manager.checkLabGroups();

    verify(communicationManager).notify(any(), isNull(), any(), message.capture());
    assertThat(message.getValue())
        .contains("&lt;script&gt;alert(1)&lt;/script&gt;", "Ada &lt;b&gt;Smith&lt;/b&gt;")
        .doesNotContain("<script>", "<b>");
  }

  @Test
  void nothingIsReportedOrMarkedWhenThereIsNoEnabledSysadmin() {
    User disabled = sysadmin("gone");
    disabled.setEnabled(false);
    storedThresholdIs("1");
    usageIs(usage(7L, 5 * MB));
    alreadyReported();
    sysadminsAre(disabled);

    manager.checkLabGroups();

    verifyNoInteractions(communicationManager);
    verify(groupDao, never()).setStorageAlertSent(any(), anyBoolean());
  }

  // ---- AC3: once per crossing ----

  @Test
  void groupBelowTheThresholdIsNotReported() {
    storedThresholdIs("100");
    usageIs(usage(7L, 100 * MB - 1));
    alreadyReported();

    manager.checkLabGroups();

    verifyNoInteractions(communicationManager);
    verify(groupDao, never()).setStorageAlertSent(any(), anyBoolean());
  }

  @Test
  void groupAlreadyReportedIsNotReportedAgain() {
    storedThresholdIs("100");
    usageIs(usage(7L, 500 * MB));
    alreadyReported(7L);

    manager.checkLabGroups();

    verifyNoInteractions(communicationManager);
    verify(groupDao, never()).setStorageAlertSent(any(), anyBoolean());
  }

  @Test
  void groupThatDropsBelowTheThresholdIsReArmed() {
    storedThresholdIs("100");
    usageIs(usage(7L, 40 * MB));
    alreadyReported(7L);

    manager.checkLabGroups();

    verify(groupDao).setStorageAlertSent(Set.of(7L), false);
    verifyNoInteractions(communicationManager);
  }

  @Test
  void flaggedGroupMissingFromTheUsageFigureIsReArmed() {
    storedThresholdIs("100");
    usageIs();
    alreadyReported(7L);

    manager.checkLabGroups();

    verify(groupDao).setStorageAlertSent(Set.of(7L), false);
    verifyNoInteractions(communicationManager);
  }

  // ---- AC4: setting, lowering, raising, removing ----

  @Test
  void loweringReportsOnlyGroupsNotYetReported() {
    usageIs(usage(7L, 200 * MB), usage(8L, 60 * MB), usage(9L, 10 * MB));
    alreadyReported(7L);
    groupsAre(labGroup(8L, "Jones Lab", pi("Bo", "Jones")));
    sysadminsAre(sysadmin1);

    Optional<Long> saved = manager.setThresholdMb("50", sysadmin1);

    assertEquals(Optional.of(50L), saved);
    verify(groupDao).getGroups(Set.of(8L));
    verify(communicationManager).notify(any(), isNull(), any(), message.capture());
    assertThat(message.getValue()).contains("Jones Lab", "threshold of 50 MB");
    verify(groupDao, never()).setStorageAlertSent(any(), eq(false));
    InOrder evaluatedThenSaved = inOrder(groupDao, systemPropertyManager);
    evaluatedThenSaved.verify(groupDao).setStorageAlertSent(Set.of(8L), true);
    evaluatedThenSaved.verify(systemPropertyManager).save(THRESHOLD, "50", sysadmin1);
  }

  @Test
  void raisingReArmsAReportedGroupNowBelowWithoutReportingIt() {
    usageIs(usage(7L, 120 * MB));
    alreadyReported(7L);

    manager.setThresholdMb("200", sysadmin1);

    verify(systemPropertyManager).save(THRESHOLD, "200", sysadmin1);
    verify(groupDao).setStorageAlertSent(Set.of(7L), false);
    verifyNoInteractions(communicationManager);
  }

  @Test
  void raisingStillReportsAGroupThatWasNotYetReported() {
    usageIs(usage(8L, 250 * MB));
    alreadyReported();
    groupsAre(labGroup(8L, "Jones Lab", pi("Bo", "Jones")));
    sysadminsAre(sysadmin1);

    manager.setThresholdMb("200", sysadmin1);

    verify(communicationManager).notify(any(), isNull(), any(), message.capture());
    assertThat(message.getValue()).contains("Jones Lab", "threshold of 200 MB");
    verify(groupDao).setStorageAlertSent(Set.of(8L), true);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void removingTheThresholdStopsTheChecksAndForgetsReportedGroups(String blank) {
    alreadyReported(7L);

    Optional<Long> saved = manager.setThresholdMb(blank, sysadmin1);

    assertEquals(Optional.empty(), saved);
    verify(systemPropertyManager).save(THRESHOLD, "", sysadmin1);
    verify(groupDao).setStorageAlertSent(Set.of(7L), false);
    verifyNoInteractions(fileMetadataDao, communicationManager);
  }

  @Test
  void thresholdIsTrimmedBeforeItIsSaved() {
    usageIs();
    alreadyReported();

    assertEquals(Optional.of(100L), manager.setThresholdMb(" 100 ", sysadmin1));

    verify(systemPropertyManager).save(THRESHOLD, "100", sysadmin1);
  }

  @Test
  void largestAllowedThresholdIsAccepted() {
    usageIs();
    alreadyReported();

    assertEquals(Optional.of(999_999_999_999L), manager.setThresholdMb("999999999999", sysadmin1));

    verify(systemPropertyManager).save(THRESHOLD, "999999999999", sysadmin1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-5", "+5", "1.5", "1e3", "abc", "12 3", "1000000000000"})
  void invalidThresholdIsRefusedAndNothingIsSaved(String invalid) {
    LocalizedIllegalArgumentException refused =
        assertThrows(
            LocalizedIllegalArgumentException.class,
            () -> manager.setThresholdMb(invalid, sysadmin1));

    assertEquals("system.groupList.storageAlert.errors.invalidThreshold", refused.getMessage());
    verifyNoInteractions(systemPropertyManager, fileMetadataDao, groupDao, communicationManager);
  }

  // ---- AC5: off by default ----

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "12.5", "abc", "0", "-5"})
  void unreadableStoredThresholdCountsAsOff(String stored) {
    storedThresholdIs(stored);
    alreadyReported();

    assertEquals(Optional.empty(), manager.getThresholdMb());
    manager.checkLabGroups();

    verifyNoInteractions(fileMetadataDao, communicationManager);
    verify(groupDao, never()).setStorageAlertSent(any(), anyBoolean());
  }

  @Test
  void missingPropertyRowCountsAsOff() {
    when(systemPropertyManager.findByName(THRESHOLD)).thenReturn(null);

    assertEquals(Optional.empty(), manager.getThresholdMb());
  }

  @Test
  void scheduledCheckReArmsReportedGroupsWhileTheAlertIsOff() {
    storedThresholdIs("");
    alreadyReported(7L);

    manager.checkLabGroups();

    verify(groupDao).setStorageAlertSent(Set.of(7L), false);
    verifyNoInteractions(fileMetadataDao, communicationManager);
  }

  // ---- helpers ----

  private void storedThresholdIs(String value) {
    SystemPropertyValue stored = mock(SystemPropertyValue.class);
    when(stored.getValue()).thenReturn(value);
    when(systemPropertyManager.findByName(THRESHOLD)).thenReturn(stored);
  }

  private void usageIs(DatabaseUsageByGroupGroupByResult... rows) {
    when(fileMetadataDao.getTotalFileUsageForAllLabGroups()).thenReturn(List.of(rows));
  }

  private static DatabaseUsageByGroupGroupByResult usage(Long groupId, long bytes) {
    return new DatabaseUsageByGroupGroupByResult(groupId, (double) bytes);
  }

  private void alreadyReported(Long... groupIds) {
    when(groupDao.getStorageAlertSentGroupIds()).thenReturn(List.of(groupIds));
  }

  private void groupsAre(Group... groups) {
    when(groupDao.getGroups(anyCollection())).thenReturn(List.of(groups));
  }

  /** Also stubs the instance locale and the originator: both are only reached when alerting. */
  private void sysadminsAre(User... users) {
    when(userDao.listUsersByRole(eq(Role.SYSTEM_ROLE), any()))
        .thenReturn(new SearchResultsImpl<>(List.of(users), 0, users.length));
    boolean anyEnabled = Arrays.stream(users).anyMatch(User::isEnabled);
    if (anyEnabled) {
      when(userDao.getUserByUsername(Constants.SYSADMIN_UNAME)).thenReturn(sysadmin1);
      when(userLocaleService.getLocale()).thenReturn(Locale.forLanguageTag("en-US"));
    }
  }

  private static User sysadmin(String username) {
    User user = TestFactory.createAnyUser(username);
    user.addRole(Role.SYSTEM_ROLE);
    return user;
  }

  private static User pi(String firstName, String lastName) {
    User user = TestFactory.createAnyUser((firstName + lastName).replaceAll("\\W", ""));
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.addRole(Role.PI_ROLE);
    return user;
  }

  private static Group labGroup(Long id, String displayName, User pi) {
    Group group = TestFactory.createAnyGroup(pi);
    group.setId(id);
    group.setDisplayName(displayName);
    return group;
  }
}

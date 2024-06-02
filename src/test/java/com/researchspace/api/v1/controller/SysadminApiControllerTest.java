package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.model.record.TestFactory.createAnyGroup;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static com.researchspace.model.record.TestFactory.createNUsers;
import static com.researchspace.testutils.MockAndStubUtils.modifyUserCreationDate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.SysadminApiController.GroupApiPost;
import com.researchspace.api.v1.controller.SysadminApiController.UserGroupPost;
import com.researchspace.api.v1.model.ApiGroupInfo;
import com.researchspace.api.v1.model.ApiSysadminUserSearchResult;
import com.researchspace.api.v1.model.ApiUserGroupInfo;
import com.researchspace.api.v1.model.ApiUserPost;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.auth.WhiteListIPChecker;
import com.researchspace.core.testutil.JavaxValidatorTest;
import com.researchspace.core.util.DateUtil;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.licensews.LicenseServerUnavailableException;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.IGroupCreationStrategy;
import com.researchspace.service.UserDeletionManager;
import com.researchspace.service.UserDeletionPolicy;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.UserExportHandler;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

public class SysadminApiControllerTest extends JavaxValidatorTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock UserManager userMgr;
  @Mock UserDeletionManager userDelMgr;
  @Mock WhiteListIPChecker checker;
  @Mock IPropertyHolder properties;
  @Mock UserExportHandler userExportHandler;
  @Mock IGroupCreationStrategy grpStrategy;
  @Mock AuditTrailService auditService;
  @Mock UserEnablementUtils userEnablementUtils;
  @Mock IContentInitializer init;

  @Captor ArgumentCaptor<User> userCaptor;

  @InjectMocks SysadminApiController controller;
  User user = TestFactory.createAnyUser("any");
  User sysadmin = TestFactory.createAnyUser("sysadmin");
  MockHttpServletResponse response;
  MockHttpServletRequest request;

  @Before
  public void setUp() throws Exception {
    response = new MockHttpServletResponse();
    sysadmin.addRole(Role.SYSTEM_ROLE);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void userMustBeSysadmin() throws Exception {
    assertRequestRejectedAuthException(user);
    user.addRole(Role.PI_ROLE);
    assertRequestRejectedAuthException(user);
    user.addRole(Role.ADMIN_ROLE);
    assertRequestRejectedAuthException(user);
    user.addRole(Role.SYSTEM_ROLE);
    setUpMocksForUserListSuccess(user);
    assertNotNull(basicRequest(user));
  }

  @Test
  public void ipWhiteListMustBeTrue() throws Exception {
    mockWhiteListedIP(false, sysadmin);
    assertRequestRejectedAuthException(sysadmin);
  }

  @Test
  public void deleteAcceptDatesOlderThan1Year() throws Exception {
    mockWhiteListedIP(true, sysadmin);
    when(properties.getDeleteUserResourcesImmediately()).thenReturn(true);
    User toDelete = createAnyUserWithId();
    toDelete.setTempAccount(true);
    setCreationDate2yearsAgo(toDelete);
    basicDeleteTempUserRequest(sysadmin, toDelete);
    assertDeletionManagerInvoked();
  }

  @Test
  public void deleteRejectNonTempAccounts() throws Exception {
    mockWhiteListedIP(true, sysadmin);
    User toDelete = createAnyUserWithId();
    toDelete.setTempAccount(false);
    setCreationDate2yearsAgo(toDelete);
    assertExceptionThrown(
        () -> basicDeleteTempUserRequest(sysadmin, toDelete), IllegalArgumentException.class);
    assertDeletionManagerNotInvoked();
  }

  @Test
  public void deleteAnyUserAcceptsNonTempAccounts() throws Exception {
    mockWhiteListedIP(true, sysadmin);
    when(properties.getDeleteUserResourcesImmediately()).thenReturn(true);
    User toDelete = createAnyUserWithId();
    toDelete.setTempAccount(false);
    setCreationDate2yearsAgo(toDelete);
    when(userDelMgr.isUserRemovable(
            Mockito.eq(toDelete.getId()),
            Mockito.any(UserDeletionPolicy.class),
            Mockito.eq(sysadmin)))
        .thenReturn(new ServiceOperationResult<User>(toDelete, Boolean.TRUE, "OK"));
    toDelete.setContentInitialized(true);
    Future<ArchiveResult> exportResult = mockExportResult();
    Mockito.when(
            userExportHandler.doUserArchive(
                Mockito.any(), Mockito.any(), Mockito.eq(sysadmin), Mockito.any()))
        .thenReturn(exportResult);
    mockServerUrl();
    // test delete
    basicDeleteAnyUserRequest(sysadmin, toDelete);
    // then
    assertDeletionManagerInvoked();
    assertExportInvoked();
  }

  private Future<ArchiveResult> mockExportResult() throws InterruptedException, ExecutionException {
    Future<ArchiveResult> res = Mockito.mock(Future.class);
    when(res.get()).thenReturn(new ArchiveResult());
    return res;
  }

  private void assertExportInvoked() throws Exception {
    Mockito.verify(userExportHandler)
        .doUserArchive(Mockito.any(), Mockito.any(), Mockito.eq(sysadmin), Mockito.any());
  }

  @Test
  public void deleteRejectifCreationDatesLessThan1year() throws Exception {
    mockWhiteListedIP(true, sysadmin);
    User toDelete = createAnyUserWithId();
    toDelete.setTempAccount(true);
    assertExceptionThrown(
        () -> basicDeleteTempUserRequest(sysadmin, toDelete), IllegalArgumentException.class);
    assertDeletionManagerNotInvoked();
  }

  @Test
  public void rejectDatesLessThan1Year() throws Exception {
    mockWhiteListedIP(true, sysadmin);
    assertUserListRequestRejected(
        sysadmin,
        new ApiSystemUserSearchConfig(yearMinusADayAgo(), null, true),
        IllegalArgumentException.class);
  }

  private User createAnyUserWithId() {
    User toDelete = createNUsers(1, "user").get(0);
    toDelete.setId(1L);
    return toDelete;
  }

  private void setCreationDate2yearsAgo(User toDelete) {
    modifyUserCreationDate(toDelete, DateUtil.localDateToDateUTC(LocalDate.now().minusYears(2)));
  }

  private void basicDeleteTempUserRequest(User sysadmin2, User toDelete) {
    setUpMocksForDeletingUser(sysadmin2, toDelete);
    controller.deleteTempUserOlderThan1Year(request, toDelete.getId(), sysadmin2);
  }

  private void basicDeleteAnyUserRequest(User sysadmin2, User toDelete) {
    setUpMocksForDeletingUser(sysadmin2, toDelete);
    controller.deleteAnyUserOlderThan1Year(request, nDaysAgo(2 * 365), toDelete.getId(), sysadmin2);
  }

  private Date nDaysAgo(int nDaysAgo) {
    return new Date(Instant.now().minus(nDaysAgo, ChronoUnit.DAYS).toEpochMilli());
  }

  private void setUpMocksForDeletingUser(User sysadmin2, User toDelete) {
    when(userMgr.get(toDelete.getId())).thenReturn(toDelete);
    when(userDelMgr.removeUser(
            Mockito.eq(toDelete.getId()),
            Mockito.any(UserDeletionPolicy.class),
            Mockito.eq(sysadmin2)))
        .thenReturn(new ServiceOperationResult<User>(toDelete, Boolean.TRUE, "OK"));
  }

  private void setUpMocksForUserListSuccess(User subject) {
    mockWhiteListedIP(true, subject);
    mockServerUrl();
    mockNUsers(subject, 1);
  }

  private LocalDate yearMinusADayAgo() {
    return LocalDate.now().minusYears(1).plusDays(1);
  }

  private LocalDate yearAndADayAgo() {
    return LocalDate.now().minusYears(1).minusDays(1);
  }

  private ApiSysadminUserSearchResult basicRequest(User u, ApiSystemUserSearchConfig srchConfig)
      throws BindException {
    return controller.getUsers(
        request,
        new SysadminUserPaginationCriteria(),
        srchConfig,
        new BeanPropertyBindingResult(null, "object"),
        u);
  }

  private ApiSysadminUserSearchResult basicRequest(User u) throws BindException {
    return basicRequest(u, new ApiSystemUserSearchConfig());
  }

  private void mockWhiteListedIP(boolean returnValue, User subject) {
    when(checker.isRequestWhitelisted(
            Mockito.eq(request), Mockito.eq(subject), Mockito.any(Logger.class)))
        .thenReturn(returnValue);
  }

  private void mockServerUrl() {
    when(properties.getServerUrl()).thenReturn("http://a.b.com");
  }

  private void assertRequestRejectedAuthException(User subject) throws Exception {
    assertExceptionThrown(() -> basicRequest(subject), AuthorizationException.class);
  }

  private void assertUserListRequestRejected(
      User subject, ApiSystemUserSearchConfig srchConfig, Class<? extends Exception> clazz)
      throws Exception {
    assertExceptionThrown(() -> basicRequest(subject, srchConfig), clazz);
  }

  ISearchResults<User> mockNUsers(User subject, int numUsers) {
    List<User> users = createNUsers(numUsers, "user");
    ISearchResults<User> res = new SearchResultsImpl<>(users, 0, numUsers, numUsers);
    when(userMgr.getViewableUsers(Mockito.eq(subject), Mockito.any(PaginationCriteria.class)))
        .thenReturn(res);
    return res;
  }

  private void assertDeletionManagerNotInvoked() {
    verify(userDelMgr, never())
        .removeUser(
            Mockito.anyLong(), Mockito.any(UserDeletionPolicy.class), Mockito.any(User.class));
  }

  private void assertDeletionManagerInvoked() {
    verify(userDelMgr)
        .removeUser(
            Mockito.anyLong(), Mockito.any(UserDeletionPolicy.class), Mockito.any(User.class));
    verify(userDelMgr)
        .deleteRemovedUserFilestoreResources(
            Mockito.anyLong(), Mockito.anyBoolean(), Mockito.any(User.class));
    verify(auditService).notify(Mockito.any(GenericEvent.class));
  }

  @Test
  public void validateGroupPostRequiresAtLeast1Pi() throws BindException {
    GroupApiPost grpApiPost = new GroupApiPost();
    grpApiPost.setUsers(toList(new UserGroupPost("any", "PI")));
    when(userMgr.userExists(Mockito.anyString())).thenReturn(true);
    User internalUser = TestFactory.createAnyUser("any");
    when(userMgr.getUserByUsername("any")).thenReturn(internalUser);
    mockWhiteListedIP(true, sysadmin);
    // requires PI
    assertIllegalArgumentException(
        () ->
            controller.createGroup(
                request, grpApiPost, new BeanPropertyBindingResult(grpApiPost, "bean"), sysadmin));
    verifyZeroInteractions(grpStrategy);

    // but with >=1 PI, succeeds
    internalUser.addRole(Role.PI_ROLE);
    when(grpStrategy.createAndSaveGroup(
            Mockito.any(Group.class), Mockito.any(User.class), Mockito.anyList()))
        .thenReturn(createAnyGroup(internalUser));

    ApiGroupInfo grpApiGroup =
        controller.createGroup(
            request, grpApiPost, new BeanPropertyBindingResult(grpApiPost, "bean"), sysadmin);
    assertNotNull(grpApiGroup);
  }

  @Test
  public void validateGroupPost() {
    // validation based on annotations
    GroupApiPost grpPostPost = new GroupApiPost();
    final int initialErrorCount = 2;
    assertNErrors(grpPostPost, initialErrorCount, true);
    grpPostPost.setDisplayName("any");
    assertNErrors(grpPostPost, initialErrorCount - 1, true);
    grpPostPost.setUsers(toList(new UserGroupPost("any", "PI")));
    assertValid(grpPostPost);

    grpPostPost.setUsers(toList(new UserGroupPost("any", "UNKNOWN_ROLE")));
    assertNErrors(grpPostPost, 1, true);
  }

  @Test
  public void validateUserPost() {
    // validation based on annotations
    ApiUserPost userPost = new ApiUserPost();
    final int initialErrorCount = 6;
    assertNErrors(userPost, initialErrorCount);
    userPost.setEmail("something@somewhere.com");
    assertNErrors(userPost, initialErrorCount - 1);

    userPost.setUsername("something");
    assertNErrors(userPost, initialErrorCount - 2);

    userPost.setFirstName("1st");
    assertNErrors(userPost, initialErrorCount - 3);

    userPost.setLastName("lasst");
    assertNErrors(userPost, initialErrorCount - 4);

    userPost.setRole("ROLE_PI");
    assertNErrors(userPost, initialErrorCount - 5);

    userPost.setPassword("somevalidpassword");
    assertValid(userPost);
  }

  /** Tests that when creating a group with multiple users that are PIs, the correct one gets set */
  @Test
  public void correctUserSetAsPi() throws BindException {
    User pi1 = TestFactory.createAnyUser("pi1");
    User pi2 = TestFactory.createAnyUser("pi2");
    User pi3PiOfGroup = TestFactory.createAnyUser("pi3");
    pi1.addRole(Role.PI_ROLE);
    pi2.addRole(Role.PI_ROLE);
    pi3PiOfGroup.addRole(Role.PI_ROLE);

    when(userMgr.userExists(Mockito.anyString())).thenReturn(true);
    when(userMgr.getUserByUsername("pi1")).thenReturn(pi1);
    when(userMgr.getUserByUsername("pi2")).thenReturn(pi2);
    when(userMgr.getUserByUsername("pi3")).thenReturn(pi3PiOfGroup);
    mockWhiteListedIP(true, sysadmin);

    GroupApiPost grpApiPost = new GroupApiPost();
    grpApiPost.setUsers(
        toList(
            new UserGroupPost("pi1", "DEFAULT"),
            new UserGroupPost("pi2", "DEFAULT"),
            new UserGroupPost(pi3PiOfGroup.getUsername(), "PI")));

    when(grpStrategy.createAndSaveGroup(
            Mockito.any(Group.class), userCaptor.capture(), Mockito.anyList()))
        .thenReturn(createAnyGroup(pi3PiOfGroup, pi1, pi2));

    ApiGroupInfo groupInfo =
        controller.createGroup(
            request, grpApiPost, new BeanPropertyBindingResult(grpApiPost, "bean"), sysadmin);

    assertEquals(pi3PiOfGroup.getUsername(), userCaptor.getValue().getUsername());
    List<ApiUserGroupInfo> groupMembers = groupInfo.getMembers();
    assertEquals("USER", getGroupRoleFor(groupMembers, "pi1"));
    assertEquals("USER", getGroupRoleFor(groupMembers, "pi2"));
    assertEquals("PI", getGroupRoleFor(groupMembers, "pi3"));
  }

  private String getGroupRoleFor(List<ApiUserGroupInfo> groupMembers, String username) {
    return groupMembers.stream()
        .filter(u -> u.getUsername().equals(username))
        .findFirst()
        .get()
        .getRole();
  }

  @Test
  public void enablingUserAccountThatWasDisabled() throws Exception {
    // given
    mockWhiteListedIP(true, sysadmin);
    User userToEnable = createAnyUser("disabled_to_enable");
    userToEnable.setEnabled(false);
    userToEnable.addRole(Role.USER_ROLE);
    when(userMgr.get(userToEnable.getId())).thenReturn(userToEnable);
    when(userMgr.save(userToEnable)).thenReturn(userToEnable);

    // when
    controller.enableUser(request, sysadmin, userToEnable.getId());

    // then
    verify(userMgr).save(userToEnable);
    verify(userEnablementUtils).checkLicenseForUserInRole(1, Role.USER_ROLE);
    verify(userEnablementUtils).auditUserEnablementChangeEvent(true, userToEnable);
    verify(userEnablementUtils).notifyByEmailUserEnablementChange(userToEnable, sysadmin, true);
  }

  @Test
  public void enablingUserAccountThatWasAlreadyEnabled() throws Exception {
    // given
    mockWhiteListedIP(true, sysadmin);
    User userToEnable = createAnyUser("enable_to_enable");
    userToEnable.setEnabled(true);
    userToEnable.addRole(Role.USER_ROLE);
    when(userMgr.get(userToEnable.getId())).thenReturn(userToEnable);
    when(userMgr.save(userToEnable)).thenReturn(userToEnable);

    // when
    controller.enableUser(request, sysadmin, userToEnable.getId());

    // then
    verify(userMgr, times(0)).save(userToEnable);
    verifyNoInteractions(userEnablementUtils);
  }

  @Test
  public void raiseExceptionEnablingUserAccountWithoutHavingLicenseForRole() throws Exception {
    // given
    mockWhiteListedIP(true, sysadmin);
    User userToEnable = createAnyUser("disabled_to_enable");
    userToEnable.setEnabled(false);
    userToEnable.addRole(Role.USER_ROLE);
    when(userMgr.get(userToEnable.getId())).thenReturn(userToEnable);
    when(userMgr.save(userToEnable)).thenReturn(userToEnable);
    doThrow(new LicenseServerUnavailableException())
        .when(userEnablementUtils)
        .checkLicenseForUserInRole(anyInt(), any(Role.class));

    // when
    assertExceptionThrown(
        () -> controller.enableUser(request, sysadmin, userToEnable.getId()),
        LicenseServerUnavailableException.class);

    // then
    verify(userMgr, times(0)).save(userToEnable);
    verify(userEnablementUtils, times(0)).auditUserEnablementChangeEvent(true, userToEnable);
    verify(userEnablementUtils, times(0))
        .notifyByEmailUserEnablementChange(userToEnable, sysadmin, true);
  }

  @Test
  public void disablingUserAccountThatWasEnabled() throws Exception {
    // given
    mockWhiteListedIP(true, sysadmin);
    User userToDisable = createAnyUser("enabled_to_disable");
    userToDisable.setEnabled(true);
    userToDisable.addRole(Role.USER_ROLE);
    when(userMgr.get(userToDisable.getId())).thenReturn(userToDisable);
    when(userMgr.save(userToDisable)).thenReturn(userToDisable);

    // when
    controller.disableUser(request, sysadmin, userToDisable.getId());

    // then
    verify(userMgr).save(userToDisable);
    verify(userEnablementUtils, times(0)).checkLicenseForUserInRole(1, Role.USER_ROLE);
    verify(userEnablementUtils).auditUserEnablementChangeEvent(false, userToDisable);
    verify(userEnablementUtils).notifyByEmailUserEnablementChange(userToDisable, sysadmin, false);
  }

  @Test
  public void disablingUserAccountThatWasAlreadyDisabled() throws Exception {
    // given
    mockWhiteListedIP(true, sysadmin);
    User disableToDisable = createAnyUser("disable_to_disable");
    disableToDisable.setEnabled(false);
    disableToDisable.addRole(Role.USER_ROLE);
    when(userMgr.get(disableToDisable.getId())).thenReturn(disableToDisable);
    when(userMgr.save(disableToDisable)).thenReturn(disableToDisable);

    // when
    controller.disableUser(request, sysadmin, disableToDisable.getId());

    // then
    verify(userMgr, times(0)).save(disableToDisable);
    verifyNoInteractions(userEnablementUtils);
  }

  @Test
  public void doNotRaiseExceptionDisablingUserAccountWhenNoLicense() throws Exception {
    // given
    mockWhiteListedIP(true, sysadmin);
    User userToEnable = createAnyUser("disabled_to_enable");
    userToEnable.setEnabled(false);
    userToEnable.addRole(Role.USER_ROLE);
    when(userMgr.get(userToEnable.getId())).thenReturn(userToEnable);
    when(userMgr.save(userToEnable)).thenReturn(userToEnable);
    doThrow(new LicenseServerUnavailableException())
        .when(userEnablementUtils)
        .checkLicenseForUserInRole(anyInt(), any(Role.class));

    // when
    controller.disableUser(request, sysadmin, userToEnable.getId());

    // then
    verify(userMgr, times(0)).save(userToEnable);
    verify(userEnablementUtils, times(0)).auditUserEnablementChangeEvent(true, userToEnable);
    verify(userEnablementUtils, times(0))
        .notifyByEmailUserEnablementChange(userToEnable, sysadmin, true);
  }
}

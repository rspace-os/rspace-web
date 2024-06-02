package com.researchspace.webapp.controller;

import static com.researchspace.Constants.SYSADMIN_ROLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.admin.service.SysAdminManager;
import com.researchspace.admin.service.UsageListingDTO;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.licenseserver.model.License;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.GroupListResult;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.LicenseService;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserDeletionManager;
import com.researchspace.service.UserDeletionPolicy;
import com.researchspace.service.UserDeletionPolicy.UserTypeRestriction;
import com.researchspace.service.UserManager;
import com.researchspace.service.UserStatisticsManager;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class SysAdminControllerTest {

  @Rule public MockitoRule mockery = MockitoJUnit.rule();
  @Mock UserManager userManager;
  @Mock SysAdminManager sysMgr;
  @Mock private SystemPropertyPermissionManager systemPropertyPermissionManager;
  @Mock LicenseService licenseService;
  @Mock UserStatisticsManager userStatisticsManager;
  @Mock IPropertyHolder properties;
  @Mock UserDeletionManager delMgr;
  @Mock UserExportHandler userExportHandler;
  @Mock CommunityServiceManager commService;
  @InjectMocks SysAdminController ctrller;
  private User sysadmin;

  @After
  public void tearDown() throws Exception {}

  @Test
  public void noUserExportIfUserRemovableCheckFails() {
    sysadmin = TestFactory.createAnyUserWithRole("sys", SYSADMIN_ROLE);

    when(userManager.getAuthenticatedUserInSession()).thenReturn(sysadmin);
    when(properties.getDeleteUser()).thenReturn(Boolean.TRUE.toString());
    when(delMgr.isUserRemovable(2L, noRestriction(), sysadmin))
        .thenReturn(new ServiceOperationResult<User>(null, false, "failed"));
    ctrller.removeUserAccount(2L);
    Mockito.verifyZeroInteractions(userExportHandler);
    verify(delMgr, Mockito.never()).removeUser(2L, noRestriction(), sysadmin);
  }

  @Test
  public void deleteUserRequiresSetDeploymentProperty() {
    sysadmin = TestFactory.createAnyUserWithRole("sys", SYSADMIN_ROLE);

    when(userManager.getAuthenticatedUserInSession()).thenReturn(sysadmin);
    when(properties.getDeleteUser()).thenReturn(Boolean.FALSE.toString());
    CoreTestUtils.assertIllegalStateExceptionThrown(() -> ctrller.removeUserAccount(2L));
    Mockito.verifyZeroInteractions(userExportHandler);
    verify(delMgr, Mockito.never()).removeUser(2L, noRestriction(), sysadmin);
  }

  @Test
  public void groupListResult() {
    Community comm = TestFactory.createACommunity();
    User piUser = TestFactory.createAnyUserWithRole("pi", Constants.PI_ROLE);
    Group grpGroup = TestFactory.createAnyGroup(piUser);
    comm.addLabGroup(grpGroup);
    when(commService.getCommunityWithAdminsAndGroups(1L)).thenReturn(comm);
    AjaxReturnObject<List<GroupListResult>> resultAjaxReturnObject = ctrller.getLabGroups(1L);
    assertEquals(1, resultAjaxReturnObject.getData().size());
    assertNotNull(resultAjaxReturnObject.getData().get(0).getPiFullname());

    // handle group with no PI. e.g. an empty group
    assertTrue(grpGroup.removeMember(piUser));
    resultAjaxReturnObject = ctrller.getLabGroups(1L);
    assertEquals(1, resultAjaxReturnObject.getData().size());
    assertEquals("No PI set", resultAjaxReturnObject.getData().get(0).getPiAffiliation());
    assertEquals("No PI set", resultAjaxReturnObject.getData().get(0).getPiFullname());
  }

  private UserDeletionPolicy noRestriction() {
    return new UserDeletionPolicy(UserTypeRestriction.NO_RESTRICTION);
  }

  @Test
  public void testNullLicenseIsOK() {
    sysadmin = TestFactory.createAnyUserWithRole("sys", SYSADMIN_ROLE);
    Principal mockPrincipal = sysadmin::getUsername;
    PaginationCriteria<User> pgcrit = PaginationCriteria.createDefaultForClass(User.class);
    when(userManager.getUserByUsername(sysadmin.getUniqueName())).thenReturn(sysadmin);
    when(userManager.getAuthenticatedUserInSession()).thenReturn(sysadmin);
    when(sysMgr.getUserUsageInfo(sysadmin, pgcrit))
        .thenReturn(new SearchResultsImpl<>(Collections.emptyList(), pgcrit, 0));
    final int totalEnabledusers = 4;
    when(userStatisticsManager.getUserStats(Mockito.anyInt()))
        .thenReturn(new UserStatistics(5, totalEnabledusers, 1, 3));
    when(licenseService.getLicense()).thenReturn(null);
    UsageListingDTO usage = ctrller.getUsersAndUsageListing(mockPrincipal, pgcrit, null);
    assertNotNull(usage);
    assertEquals(SysAdminController.UNKNOWN, usage.getUserStats().getAvailableSeats());
    // and ok case
    License license = new License();
    license.setTotalUserSeats(20);
    when(licenseService.getLicense()).thenReturn(license);
    usage = ctrller.getUsersAndUsageListing(mockPrincipal, pgcrit, null);
    assertEquals("16", usage.getUserStats().getAvailableSeats());
  }
}

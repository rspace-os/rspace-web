package com.researchspace.webapp.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Community;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserManager;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

public class CommunityAdminControllerTest {
  @Rule public MockitoRule rule = MockitoJUnit.rule();
  private CommunityAdminController controller;

  @Mock private CommunityServiceManager mockCommService;
  @Mock private UserManager mockUserMgr;
  @Mock private MessageSourceUtils mockMessageSource;
  @Mock private IPermissionUtils permUtils;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private SystemPropertyPermissionManager systemPropertyPermissionManagerMock;
  Model model;
  User subject;

  @Before
  public void setUp() throws Exception {
    controller = new CommunityAdminController();
    ReflectionTestUtils.setField(
        controller, "systemPropertyPermissionManager", systemPropertyPermissionManagerMock);
    controller.setCommunityServiceManager(mockCommService);
    controller.setMessageSource(mockMessageSource);
    controller.setPermissionUtils(permUtils);
    model = new ExtendedModelMap();
    controller.setUserManager(mockUserMgr);
    controller.setPublisher(publisher);
    subject = TestFactory.createAnyUser("any");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testRemoveGroup() {
    Long[] groupIds = new Long[] {2L};

    // can't delete from default community
    when(permUtils.isPermitted(Mockito.anyString())).thenReturn(true);
    when(mockUserMgr.getAuthenticatedUserInSession()).thenReturn(subject);
    AjaxReturnObject<Long> aro = controller.removeGroup(groupIds, Community.DEFAULT_COMMUNITY_ID);
    assertNull(aro.getData());
    assertTrue(aro.getErrorMsg().hasErrorMessages());
    Long OTHER_ID = 2L;
    // ok
    AjaxReturnObject<Long> aro2 = controller.removeGroup(groupIds, OTHER_ID);
    assertNotNull(aro2.getData());
    assertNull(aro2.getErrorMsg());
    verify(mockMessageSource).getMessage("community.removeFromDefaultProhibited.msg");
  }

  @Test
  public void testListCommunities() {

    final PaginationCriteria<Community> pgCrit =
        PaginationCriteria.createDefaultForClass(Community.class);

    final ISearchResults res = Mockito.mock(ISearchResults.class);
    when(mockUserMgr.getAuthenticatedUserInSession()).thenReturn(subject);
    when(mockCommService.listCommunities(subject, pgCrit)).thenReturn(res);

    controller.listCommunities(model, PaginationCriteria.createDefaultForClass(Community.class));
    verify(mockCommService).listCommunities(subject, pgCrit);
    assertTrue(model.containsAttribute("communities"));
    assertTrue(model.containsAttribute("paginationList"));
  }

  @Test
  public void testMoveGroup() {
    Long[] groupIds = new Long[] {2L};
    when(mockUserMgr.getAuthenticatedUserInSession()).thenReturn(subject);

    // moving same communityto tiself not allowed
    AjaxReturnObject<Long> aro = controller.moveGroup(groupIds, -2L, -2L);
    assertNull(aro.getData());
    assertTrue(aro.getErrorMsg().hasErrorMessages());
    // moving same community to tiself not allowed
    AjaxReturnObject<Long> aro2 = controller.moveGroup(groupIds, -3L, -2L);
    assertNotNull(aro2.getData());
    assertNull(aro2.getErrorMsg());
  }

  @Test
  public void testEditCommunity() {
    final Community comm = new Community();
    comm.setDisplayName("name");
    comm.setProfileText("profile");
    comm.setId(1L);
    BindingResult errors = new BeanPropertyBindingResult(comm, "comm");
    when(mockUserMgr.getAuthenticatedUserInSession()).thenReturn(subject);
    when(mockCommService.get(1L)).thenReturn(comm);
    when(permUtils.isPermitted(comm, PermissionType.WRITE, subject)).thenReturn(true);

    String view = controller.editCommunity(model, comm, errors);

    verify(mockCommService).save(comm);
    assertTrue(view.contains(CommunityAdminController.REDIRECT_SYSTEM_COMMUNITY));
    assertFalse(errors.hasErrors());

    Mockito.reset(mockCommService);
    // now try with empty displayname
    comm.setDisplayName("");
    errors = new BeanPropertyBindingResult(comm, "comm");

    view = controller.editCommunity(model, comm, errors);
    verify(mockCommService, Mockito.never()).save(comm);
    verify(mockCommService, Mockito.never()).get(1L);
    assertFalse(view.contains(CommunityAdminController.REDIRECT_SYSTEM_COMMUNITY));
    // has errors
    assertTrue(errors.hasErrors());
  }

  @Test
  public void testAddAdmin() {
    controller.setUserManager(mockUserMgr);
    final User subject = createASysAdmin();
    final Community comm = new Community();
    final Long[] adminIds = new Long[] {1L};
    comm.setAdminIds(Arrays.asList(adminIds));
    comm.setId(1L);
    when(mockUserMgr.getAuthenticatedUserInSession()).thenReturn(subject);

    when(permUtils.isPermitted(comm, PermissionType.WRITE, subject)).thenReturn(true);

    String view = controller.addAdmin(comm);
    verify(mockCommService).addAdminsToCommunity(adminIds, 1L);
    verify(publisher).publishEvent(Mockito.any(HistoricalEvent.class));
  }

  private User createASysAdmin() {
    final User subject = TestFactory.createAnyUser("any");
    subject.addRole(Role.SYSTEM_ROLE);
    return subject;
  }

  @Test
  public void testRemoveAdmin() {
    controller.setUserManager(mockUserMgr);
    final User subject = createASysAdmin();
    final ServiceOperationResult<Community> expected =
        new ServiceOperationResult<Community>(null, false);
    when(permUtils.isPermitted(Mockito.anyString())).thenReturn(true);
    when(mockUserMgr.getAuthenticatedUserInSession()).thenReturn(subject);
    when(mockCommService.removeAdminFromCommunity(-1L, -2L)).thenReturn(expected);

    AjaxReturnObject<Boolean> aro = controller.removeAdmin(-1L, -2L);
    assertNull(aro.getData());
    // removal successful
    final ServiceOperationResult<Community> expected2 =
        new ServiceOperationResult<Community>(null, true);
    Mockito.reset(mockCommService);
    when(mockCommService.removeAdminFromCommunity(-1L, -2L)).thenReturn(expected2);

    aro = controller.removeAdmin(-1L, -2L);
    verify(publisher).publishEvent(Mockito.any(HistoricalEvent.class));
    assertNotNull(aro.getData());
  }
}

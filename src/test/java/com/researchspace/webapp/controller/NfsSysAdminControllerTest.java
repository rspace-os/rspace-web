package com.researchspace.webapp.controller;

import static com.researchspace.testutils.RSpaceTestUtils.assertAuthExceptionThrown;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.NfsManager;
import com.researchspace.service.UserManager;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.ui.ExtendedModelMap;

public class NfsSysAdminControllerTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock NfsManager netFilesMgr;
  @Mock UserManager userMgr;
  @Mock MessageSourceUtils msgSource;
  NfsSysAdminController nfsSystemCtrller;
  User sysadmin, otherUser;
  NfsFileSystem nfs = null;

  @Before
  public void setup() {
    nfsSystemCtrller = new NfsSysAdminController();
    nfsSystemCtrller.setUserManager(userMgr);
    nfsSystemCtrller.setNfsManager(netFilesMgr);
    nfsSystemCtrller.setMessageSource(msgSource);
    sysadmin = TestFactory.createAnyUserWithRole("any", Constants.SYSADMIN_ROLE);
    otherUser = TestFactory.createAnyUserWithRole("any", Constants.ADMIN_ROLE);
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(sysadmin);
    nfs = new NfsFileSystem();
    nfs.setId(12L);
  }

  @Test
  public void testGetFileSystemsViewOnlyForSysadmin() throws Exception {
    assertNotNull(nfsSystemCtrller.getFileSystemsView(new ExtendedModelMap()));
    assertAuthExceptionThrown(
        () -> {
          when(userMgr.getAuthenticatedUserInSession()).thenReturn(otherUser);
          nfsSystemCtrller.getFileSystemsView(new ExtendedModelMap());
        });
  }

  @Test
  public void getFileSystemsList() throws Exception {
    assertNotNull(nfsSystemCtrller.getFileSystemsList());
    verify(netFilesMgr, atLeastOnce()).getFileSystems();
  }

  @Test(expected = AuthorizationException.class)
  public void getFileSystemsListFailsForNonSysadmin() throws Exception {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(otherUser);
    nfsSystemCtrller.getFileSystemsList();
    verify(netFilesMgr, never()).getFileSystems();
  }

  @Test
  public void saveFileSystem() {
    assertEquals(12, nfsSystemCtrller.saveFileSystem(nfs).intValue());
    verify(netFilesMgr, atLeastOnce()).saveNfsFileSystem(nfs);
  }

  @Test(expected = AuthorizationException.class)
  public void saveFileSystemsListFailsForNonSysadmin() throws Exception {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(otherUser);
    nfsSystemCtrller.saveFileSystem(nfs);
    verify(netFilesMgr, never()).saveNfsFileSystem(nfs);
  }

  @Test
  public void deleteFileSystem() {
    when(netFilesMgr.deleteNfsFileSystem(12L)).thenReturn(true);
    assertTrue(nfsSystemCtrller.deleteFileSystem(12L));

    when(netFilesMgr.deleteNfsFileSystem(12L)).thenReturn(false);
    assertFalse(nfsSystemCtrller.deleteFileSystem(12L));
  }

  @Test(expected = AuthorizationException.class)
  public void deleteFileSystemFailsForNonSysadmin() throws Exception {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(otherUser);
    nfsSystemCtrller.saveFileSystem(nfs);
    verify(netFilesMgr, never()).deleteNfsFileSystem(Mockito.anyLong());
  }
}

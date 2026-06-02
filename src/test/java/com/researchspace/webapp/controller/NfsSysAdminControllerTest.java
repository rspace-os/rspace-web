package com.researchspace.webapp.controller;

import static com.researchspace.testutils.RSpaceTestUtils.assertAuthExceptionThrown;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.NfsManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
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
    // resolve any i18n key to a string containing the first arg so existing
    // assertions on exception message content keep working
    when(msgSource.getMessage(anyString(), any(Object[].class)))
        .thenAnswer(
            inv -> {
              Object[] args = inv.getArgument(1);
              return args == null || args.length == 0 ? "" : String.valueOf(args[0]);
            });
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
    NfsFileSystemSaveResult result = nfsSystemCtrller.saveFileSystem(nfs);
    assertEquals(12L, result.getFileSystemId().longValue());
    assertTrue(result.getUnknownReadAllowlistUsernames().isEmpty());
    assertTrue(result.getUnknownWriteAllowlistUsernames().isEmpty());
    verify(netFilesMgr, atLeastOnce()).saveNfsFileSystem(nfs);
  }

  @Test
  public void saveFileSystem_unknownUsernames_returnedAsWarningButSaveSucceeds() {
    nfs.setReadAllowlist("alice,bob");
    nfs.setWriteAllowlist("carol");
    when(userMgr.getUserByUsername("alice")).thenReturn(sysadmin);
    when(userMgr.getUserByUsername("bob")).thenReturn(null);
    when(userMgr.getUserByUsername("carol"))
        .thenThrow(new org.springframework.orm.ObjectRetrievalFailureException("User", "carol"));

    NfsFileSystemSaveResult result = nfsSystemCtrller.saveFileSystem(nfs);

    assertEquals(java.util.List.of("bob"), result.getUnknownReadAllowlistUsernames());
    assertEquals(java.util.List.of("carol"), result.getUnknownWriteAllowlistUsernames());
    verify(netFilesMgr, atLeastOnce()).saveNfsFileSystem(nfs);
  }

  @Test
  public void saveFileSystem_usernameInBothLists_rejectedAndNotSaved() {
    nfs.setReadAllowlist("alice,carol");
    nfs.setWriteAllowlist("alice,bob");

    IllegalArgumentException ex =
        org.junit.Assert.assertThrows(
            IllegalArgumentException.class, () -> nfsSystemCtrller.saveFileSystem(nfs));
    org.junit.Assert.assertTrue(
        "expected error to name the duplicated user, got: " + ex.getMessage(),
        ex.getMessage().contains("alice"));
    verify(netFilesMgr, never()).saveNfsFileSystem(nfs);
  }

  @Test
  public void saveFileSystem_everyoneSentinelInBothLists_isAllowed() {
    // '*' in both lists means 'everyone reads, everyone writes' — a valid configuration,
    // not a duplicate of a named user.
    nfs.setReadAllowlist("*");
    nfs.setWriteAllowlist("*");

    NfsFileSystemSaveResult result = nfsSystemCtrller.saveFileSystem(nfs);

    assertEquals(12L, result.getFileSystemId().longValue());
    verify(netFilesMgr, atLeastOnce()).saveNfsFileSystem(nfs);
  }

  @Test
  public void saveFileSystem_emptyWriteAllowlist_isNobodyAndDoesNotTriggerLookup() {
    // The 'Nobody (read access only)' radio submits an empty string for the write list.
    // parseList returns an empty set, so no lookup is attempted; the value is persisted as-is.
    nfs.setReadAllowlist("alice");
    nfs.setWriteAllowlist("");
    when(userMgr.getUserByUsername("alice")).thenReturn(sysadmin);

    NfsFileSystemSaveResult result = nfsSystemCtrller.saveFileSystem(nfs);

    assertTrue(result.getUnknownReadAllowlistUsernames().isEmpty());
    assertTrue(result.getUnknownWriteAllowlistUsernames().isEmpty());
    verify(netFilesMgr, atLeastOnce()).saveNfsFileSystem(nfs);
  }

  @Test
  public void saveFileSystem_everyoneSentinel_doesNotTriggerLookup() {
    nfs.setReadAllowlist("*");
    nfs.setWriteAllowlist("*");

    NfsFileSystemSaveResult result = nfsSystemCtrller.saveFileSystem(nfs);

    assertTrue(result.getUnknownReadAllowlistUsernames().isEmpty());
    assertTrue(result.getUnknownWriteAllowlistUsernames().isEmpty());
    verify(userMgr, never()).getUserByUsername(Mockito.anyString());
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

package com.researchspace.service.impl;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.dao.FormDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.UserDao;
import com.researchspace.dao.UserDeletionDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.GroupManager;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.TransferService;
import com.researchspace.service.UserDeletionPolicy;
import com.researchspace.service.UserDeletionPolicy.UserTypeRestriction;
import com.researchspace.testutils.TestFactory;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UserDeletionManagerTest {

  private @Mock GroupManager grpMgr;
  private @Mock FormDao formDao;
  private @Mock IPermissionUtils permUtils;
  private @Mock UserDeletionDao deletionDao;
  private @Mock UserDao userDao;
  private @Mock FileMetadataDao fileMetadataDao;
  private @Mock FileStore fileStore;
  private @Mock DeletedUserResourcesListHelper deletedResourcesHelper;
  private @Mock RecordDao recordDao;
  private @Mock TransferService formTransferService;
  private @Mock TransferService templateTransferService;
  private @Spy MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());

  @InjectMocks UserDeletionManagerImpl userDeletionMgr;
  User toDelete, deleter, sysadmin1, sysadminToDelete, sysadmin3;

  @BeforeEach
  public void before() throws IOException {
    toDelete = TestFactory.createAnyUser("any");
    toDelete.setId(1L);
    deleter = TestFactory.createAnyUser("deleter");
    deleter.setId(2L);

    sysadmin1 = createSysadminWithID(3L, Constants.SYSADMIN_UNAME);
    sysadminToDelete = createSysadminWithID(4L, "sysadminToDelete");
    sysadmin3 = createSysadminWithID(5L, "sysadmin3");
  }

  User createSysadminWithID(Long id, String uname) {
    User c = TestFactory.createAnyUser(uname);
    c.setId(id);
    c.addRole(Role.SYSTEM_ROLE);
    return c;
  }

  @Test
  public void removeSelfUserFails() {
    stubResourcesListWriteable();
    enablePermissions();
    when(userDao.get(1L)).thenReturn(deleter);
    ServiceOperationResult<User> result = userDeletionMgr.removeUser(1L, noRestriction(), deleter);
    assertFalse(result.isSucceeded());
    assertEquals("You cannot delete yourself!", result.getMessage());
    verifyDeleteNeverInvoked();
    assertFalse(userDeletionMgr.isUserRemovable(1L, noRestriction(), deleter).isSucceeded());
  }

  @Test
  public void removeSysadmin1Fails() {
    stubResourcesListWriteable();
    enablePermissions();
    when(userDao.get(1L)).thenReturn(sysadmin1);
    ServiceOperationResult<User> result = userDeletionMgr.removeUser(1L, noRestriction(), deleter);
    assertFalse(result.isSucceeded());
    assertEquals(
        "Invalid attempt to delete sysadmin user - there must be at least one remaining active, "
            + "enabled sysadmin user. Also, can't delete the internal sysadmin account 'sysadmin1'",
        result.getMessage());
    verifyDeleteNeverInvoked();
    assertFalse(userDeletionMgr.isUserRemovable(1L, noRestriction(), deleter).isSucceeded());
  }

  @Test
  public void removeAnySysadminSucceeds() {
    stubResourcesListSaved();
    stubResourcesListWriteable();
    stubFilestoreFilesVerified();
    standardMockSetup();

    mockSysadminListing(toList(sysadminToDelete, sysadmin1));
    mockDeletion();
    ServiceOperationResult<User> result = userDeletionMgr.removeUser(1L, noRestriction(), deleter);
    assertTrue(result.isSucceeded(), result.getMessage());
    assertTrue(userDeletionMgr.isUserRemovable(1L, noRestriction(), deleter).isSucceeded());
  }

  @Test
  public void removeAnySysadminRequiresAtLeast1OtherActiveSysadmin() {
    stubResourcesListWriteable();
    standardMockSetup();
    mockSysadminListing(toList(sysadminToDelete, sysadmin3));
    // disable remaining sysadmin
    sysadmin3.setEnabled(false);
    ServiceOperationResult<User> result = userDeletionMgr.removeUser(1L, noRestriction(), deleter);
    verifyDeleteNeverInvoked();
    assertFalse(result.isSucceeded());
    assertFalse(userDeletionMgr.isUserRemovable(1L, noRestriction(), deleter).isSucceeded());
  }

  @Test
  public void removeAnySysadminRequiresAtLeast1_UnlockedSysadmin() {
    stubResourcesListWriteable();
    standardMockSetup();
    mockSysadminListing(toList(sysadminToDelete, sysadmin3));
    // lock remaining sysadmin
    sysadmin3.setAccountLocked(true);
    ServiceOperationResult<User> result = userDeletionMgr.removeUser(1L, noRestriction(), deleter);
    verifyDeleteNeverInvoked();
    assertFalse(result.isSucceeded());
    assertFalse(userDeletionMgr.isUserRemovable(1L, noRestriction(), deleter).isSucceeded());
  }

  @Test
  public void removeOnlyPiFails() {
    stubResourcesListWriteable();
    User pi = TestFactory.createAnyUserWithRole("pi", Role.PI_ROLE.getName());
    Group grp = TestFactory.createAnyGroup(pi, new User[] {});
    when(userDao.get(1L)).thenReturn(pi);
    enablePermissions();

    ServiceOperationResult<User> result = userDeletionMgr.removeUser(1L, noRestriction(), deleter);
    verifyDeleteNeverInvoked();
    assertFalse(result.isSucceeded());
    assertEquals(
        "Sorry, cannot remove the only admin or PI of this group. Please assign a new admin or PI "
            + "role to this group before deleting this member.",
        result.getMessage());
    assertFalse(userDeletionMgr.isUserRemovable(1L, noRestriction(), deleter).isSucceeded());
  }

  @Test
  public void strictUserDeletionRequiresInactiveGroup() {
    stubResourcesListSaved();
    stubResourcesListWriteable();
    stubFilestoreFilesVerified();
    User pi = TestFactory.createAnyUserWithRole("pi", Role.PI_ROLE.getName());
    User u1 = TestFactory.createAnyUser("u1");
    u1.setId(1L);
    User u2 = TestFactory.createAnyUser("u2");
    u1.setLastLogin(nYearsAgo(4));
    u2.setLastLogin(nYearsAgo(2));
    when(userDao.get(1L)).thenReturn(u1);
    mockDeletion();
    enablePermissions();

    UserDeletionPolicy policy = noRestriction();
    policy.setStrictPreserveDataForGroup(true);
    policy.setLastLoginCutOffForGroup(nYearsAgo(1));

    // case 1 strict policy is enabled, and lastLogins for all group members are before this date
    Group grp = TestFactory.createAnyGroup(pi, new User[] {u1, u2});
    ServiceOperationResult<User> result = userDeletionMgr.removeUser(1L, policy, deleter);
    assertTrue(result.isSucceeded(), result.getMessage());
    assertTrue(userDeletionMgr.isUserRemovable(1L, policy, deleter).isSucceeded());

    // now set policy so that u2 has logged in more recently than cutoff:
    // case 2 strict policy is enabled, and lastLogins for >=1 group member are AFTER this date
    policy.setLastLoginCutOffForGroup(nYearsAgo(3));
    result = userDeletionMgr.removeUser(1L, policy, deleter);
    assertFalse(result.isSucceeded(), result.getMessage());
    assertFalse(userDeletionMgr.isUserRemovable(1L, policy, deleter).isSucceeded());

    // now ignore the policy, user is removed anyway regardless of date cutoff
    // case 3 & 4 strict policy is disabled, date cutoff is ignored.
    policy.setStrictPreserveDataForGroup(false);
    assertTrue(userDeletionMgr.removeUser(1L, policy, deleter).isSucceeded());
    assertTrue(userDeletionMgr.isUserRemovable(1L, policy, deleter).isSucceeded());

    policy.setLastLoginCutOffForGroup(nYearsAgo(1));
    assertTrue(userDeletionMgr.isUserRemovable(1L, policy, deleter).isSucceeded());
    assertTrue(userDeletionMgr.removeUser(1L, policy, deleter).isSucceeded());
  }

  @Test
  public void isUserRemovableChecksResourcesFolderCconfiguration() throws IOException {
    stubResourcesListWriteable();
    enablePermissions();
    when(userDao.get(toDelete.getId())).thenReturn(toDelete);

    // writable folder should pass
    ServiceOperationResult<User> userRemovable =
        userDeletionMgr.isUserRemovable(toDelete.getId(), noRestriction(), sysadmin1);
    assertTrue(userRemovable.isSucceeded());

    // unwritable folder should fail
    when(deletedResourcesHelper.isUserResourcesListWriteable()).thenReturn(false);
    userRemovable = userDeletionMgr.isUserRemovable(toDelete.getId(), noRestriction(), sysadmin1);
    assertFalse(userRemovable.isSucceeded());
    assertTrue(
        userRemovable.getMessage().startsWith("sysadmin.delete.user.resourceList.folder points to"),
        "was: " + userRemovable.getMessage());
  }

  @Test
  public void removeUserFailsIfFilestoreResourcesListIncorrect() throws IOException {
    stubResourcesListWriteable();
    stubFilestoreFilesVerified();
    enablePermissions();
    when(userDao.get(toDelete.getId())).thenReturn(toDelete);
    when(fileStore.verifyUserFilestoreFiles(Mockito.any())).thenReturn(false);

    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(toDelete.getId(), noRestriction(), deleter);
    assertFalse(result.isSucceeded());
    assertEquals(
        "List of filestore resource marked for deletion seem incorrect", result.getMessage());
  }

  @Test
  public void removeUserResourcesOnlyForSysadmin() throws IOException {
    ServiceOperationResult<Integer> result =
        userDeletionMgr.deleteRemovedUserFilestoreResources(1L, true, deleter);
    assertFalse(result.isSucceeded());
    assertEquals(
        "Only user with sysadmin role can delete filestore resources", result.getMessage());
  }

  private Date nYearsAgo(int nYears) {
    return java.sql.Date.valueOf(LocalDate.now().minusYears(nYears));
  }

  private void verifyDeleteNeverInvoked() {
    verify(deletionDao, Mockito.never())
        .deleteUser(Mockito.anyLong(), Mockito.any(UserDeletionPolicy.class));
  }

  private void mockDeletion() {
    when(deletionDao.deleteUser(Mockito.anyLong(), Mockito.any(UserDeletionPolicy.class)))
        .thenReturn(new ServiceOperationResult<User>(toDelete, Boolean.TRUE, ""));
  }

  private void mockSysadminListing(List<User> allSysadminUsers) {
    when(userDao.listUsersByRole(
            Mockito.eq(Role.SYSTEM_ROLE), Mockito.any(PaginationCriteria.class)))
        .thenReturn(searchResultsOf(allSysadminUsers));
  }

  ISearchResults<User> searchResultsOf(List<User> list) {
    return new SearchResultsImpl<User>(list, 10, 10);
  }

  private void standardMockSetup() {
    when(userDao.get(1L)).thenReturn(sysadminToDelete);
    enablePermissions();
  }

  private void enablePermissions() {
    when(permUtils.isPermitted(Mockito.anyString())).thenReturn(true);
  }

  private UserDeletionPolicy noRestriction() {
    return new UserDeletionPolicy(UserTypeRestriction.NO_RESTRICTION);
  }

  private void stubResourcesListSaved() {
    when(deletedResourcesHelper.saveUserResourcesListToTemporaryFile(Mockito.any(), Mockito.any()))
        .thenReturn(true);
  }

  private void stubResourcesListWriteable() {
    when(deletedResourcesHelper.isUserResourcesListWriteable()).thenReturn(true);
  }

  private void stubFilestoreFilesVerified() {
    when(fileStore.verifyUserFilestoreFiles(Mockito.any())).thenReturn(true);
  }
}

package com.researchspace.service.impl;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.dao.FolderDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.CommunityServiceManager;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class FolderManagerTest {
  public @Rule MockitoRule mockery = MockitoJUnit.rule();
  @Mock IPermissionUtils permissionUtils;
  @Mock CommunityServiceManager communityServiceManager;
  @Mock FolderDao folderDao;
  @InjectMocks private FolderManagerImpl folderManagerImpl;

  User anyUser = TestFactory.createAnyUser("any");
  Folder parent = null, child = null;

  @Before
  public void before() {
    parent = TestFactory.createAFolder("parent", anyUser);
    parent.setId(1L);
    child = TestFactory.createAFolder("child", anyUser);
    child.setId(2L);
  }

  @Test
  public void testCreateGallerySubfolderThrowsIAEIfInvalid() {
    assertIllegalArgumentException(
        () -> folderManagerImpl.createGallerySubfolder("any", "unknown", anyUser));
  }

  // rspac-1949
  @Test
  public void addChildSuppressIllegalAddChildOperationException() throws Exception {

    // we add this in the model, so that subsequent attempt to add parent to child
    // will trigger IACO
    parent.addChild(child, anyUser);
    when(folderDao.get(child.getId())).thenReturn(child);

    // this should trigger IACO
    CoreTestUtils.assertExceptionThrown(
        () ->
            folderManagerImpl.addChild(
                child.getId(), parent, anyUser, ACLPropagationPolicy.DEFAULT_POLICY, false),
        IllegalAddChildOperation.class);

    assertChildNotAddedtoParent(parent);

    // suppress exception, but fails
    ServiceOperationResult<Folder> result =
        folderManagerImpl.addChild(
            child.getId(), parent, anyUser, ACLPropagationPolicy.DEFAULT_POLICY, true);
    assertThat(result.isSucceeded(), Matchers.is(Boolean.FALSE));
    assertEquals(child, result.getEntity()); // returns the intended parent
    assertChildNotAddedtoParent(parent);
  }

  @Test
  public void addChildSuccessWithIACOSuppression() throws Exception {
    when(folderDao.get(parent.getId())).thenReturn(parent);
    // here we expect success
    ServiceOperationResult<Folder> result = addChildToParent(true);
    assertResultSucceeded(result);
  }

  @Test
  public void addChildSuccessWithThrowIACO() throws Exception {
    when(folderDao.get(parent.getId())).thenReturn(parent);
    // here we expect success
    ServiceOperationResult<Folder> result = addChildToParent(false);
    assertResultSucceeded(result);
  }

  @Test
  public void communityAdminCanViewCommunityGroupFolderCreatedBySysadmin() {
    User sysAdmin = TestFactory.createAnyUserWithRole("sysadmin", "ROLE_SYSADMIN");
    assertCommunityAdminCanGetFolder(sysAdmin);
  }

  @Test
  public void communityAdminCanViewCommunityGroupFolderCreatedByPi() {
    User piFolderCreator = TestFactory.createAnyUserWithRole("piFolderCreator", "ROLE_PI");
    assertCommunityAdminCanGetFolder(piFolderCreator);
  }

  private void assertCommunityAdminCanGetFolder(User folderCreator) {
    User communityAdmin = TestFactory.createAnyUserWithRole("communityadmin", "ROLE_ADMIN");
    communityAdmin.setId(12345L);

    User piGroupCreator = TestFactory.createAnyUserWithRole("piGroupCreator", "ROLE_PI");
    Group labGroup = TestFactory.createAnyGroup(piGroupCreator);

    Community community = TestFactory.createACommunity();
    community.addLabGroup(labGroup);
    community.addAdmin(communityAdmin);

    Folder groupSharedFolder = TestFactory.createAFolder("groupSharedFolder", folderCreator);
    groupSharedFolder
        .getSharingACL()
        .addACLElement(
            labGroup, new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ));

    when(permissionUtils.refreshCacheIfNotified()).thenReturn(false);

    when(folderDao.get(groupSharedFolder.getId())).thenReturn(groupSharedFolder);
    when(communityServiceManager.listCommunitiesForAdmin(anyLong())).thenReturn(List.of(community));

    assertEquals(
        groupSharedFolder, folderManagerImpl.getFolder(groupSharedFolder.getId(), communityAdmin));
  }

  private void assertResultSucceeded(ServiceOperationResult<Folder> result) {
    assertThat(result.isSucceeded(), Matchers.is(Boolean.TRUE));
  }

  private ServiceOperationResult<Folder> addChildToParent(boolean suppressException) {
    ServiceOperationResult<Folder> result =
        folderManagerImpl.addChild(
            parent.getId(), child, anyUser, ACLPropagationPolicy.DEFAULT_POLICY, suppressException);
    return result;
  }

  private void assertChildNotAddedtoParent(Folder intendedChild) {
    assertThat(intendedChild.getParent(), nullValue());
  }
}

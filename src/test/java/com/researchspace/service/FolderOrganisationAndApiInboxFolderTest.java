package com.researchspace.service;

import static com.researchspace.core.util.MediaUtils.AUDIO_MEDIA_FLDER_NAME;
import static com.researchspace.service.UserFolderCreator.SHARED_SNIPPETS_FOLDER_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.dao.FolderDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.impl.FolderManagerImpl;
import com.researchspace.testutils.FolderTestUtils;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class FolderOrganisationAndApiInboxFolderTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock private FolderDao folderDao;
  @Mock private IPermissionUtils permUtils;
  @Mock private OperationFailedMessageGenerator messages;
  @Mock private UserGroup mockUserGroup;
  @Mock private Group mockGroup;
  private FolderManagerImpl folderMgr;
  private RecordFactory recordFactory;
  private User user;
  private UserFolderSetup setup;
  @Mock private FolderManager folderManagerMock;
  @Mock private Folder folderMock;

  @Before
  public void setUp() throws Exception {
    openMocks(this);
    recordFactory = new RecordFactory();
    user = TestFactory.createAnyUser("any");
    user.setUserGroups(Set.of(mockUserGroup));
    when(mockUserGroup.getGroup()).thenReturn(mockGroup);
    when(mockGroup.getSharedSnippetGroupFolderId()).thenReturn(1L);
    when(mockGroup.getUniqueName()).thenReturn("mockGroup");
    when(folderManagerMock.getFolder(eq(1L), eq(user))).thenReturn(folderMock);
    when(folderMock.getName()).thenReturn("group shared folder");
    folderMgr = new FolderManagerImpl(recordFactory, folderDao, permUtils, messages);

    setup = FolderTestUtils.createDefaultFolderStructure(user, folderManagerMock, folderMock);
  }

  @After
  public void tearDown() throws Exception {}

  @Test(expected = IllegalArgumentException.class)
  public void getApiUploadTargetFolderThrowsIAEIfInvalidfolderName() {
    folderMgr.getApiUploadTargetFolder("NotValidContentType", user, null);
  }

  @Test(expected = AuthorizationException.class)
  public void getApiUploadTargetFolderThrowsAuthExceptionIfFolderNotExists() {
    folderMgr.getApiUploadTargetFolder("", user, -3000L);
  }

  @Test
  public void testSharedSnippetFoldersCreated() {
    Folder snippet = setup.getSnippet();
    assertTrue(snippet.getChildrens().size() > 0);
    Folder shared =
        snippet.getSubFolderByName(SHARED_SNIPPETS_FOLDER_PREFIX + Folder.SHARED_FOLDER_NAME);
    assertTrue(shared.isSystemFolder());
    Folder labGroup =
        shared.getSubFolderByName(SHARED_SNIPPETS_FOLDER_PREFIX + Folder.LAB_GROUPS_FOLDER_NAME);
    assertNotNull(labGroup);
    Folder collabGrp =
        shared.getSubFolderByName(
            SHARED_SNIPPETS_FOLDER_PREFIX + Folder.COLLABORATION_GROUPS_FLDER_NAME);
    assertNotNull(collabGrp);
    Folder indGrp =
        shared.getSubFolderByName(
            SHARED_SNIPPETS_FOLDER_PREFIX + Folder.INDIVIDUAL_SHARE_ITEMS_FLDER_NAME);
    assertNotNull(indGrp);
    verify(folderManagerMock)
        .addChild(eq(null), eq(folderMock), eq(user), eq(ACLPropagationPolicy.NULL_POLICY));
  }

  @Test()
  public void getApiInboxWorkspaceHappyCase() {
    mockWorkspaceApiInboxExists(user.getUsername(), null);
    mockGetRootFolder(setup);
    Folder apiInboxInWorkspace = folderMgr.getApiUploadTargetFolder("", user, null);
    assertEquals(setup.getUserRoot(), apiInboxInWorkspace.getParent());

    // call again, it should return existing folder, i.e dao method only
    // called once
    mockWorkspaceApiInboxExists(user.getUsername(), apiInboxInWorkspace);
    Folder apiInboxInWorkspace2 = folderMgr.getApiUploadTargetFolder("", user, null);
    Mockito.verify(folderDao, Mockito.times(1)).getRootRecordForUser(user);
    assertTrue(apiInboxInWorkspace == apiInboxInWorkspace2);
  }

  @Test()
  public void getImportFolderPermissions() {
    mockWorkspaceImportExists(null);
    mockGetRootFolder(setup);
    Folder importsFolderInWorkspace = folderMgr.getImportsFolder(user);
    doAssertContentFolderPermissions(importsFolderInWorkspace);

    // call again, it should return existing folder, i.e dao method only
    // called once
    mockWorkspaceImportExists(importsFolderInWorkspace);
    Folder importsFolderInWorkspace2 = folderMgr.getImportsFolder(user);
    Mockito.verify(folderDao, Mockito.times(1)).getRootRecordForUser(user);
    assertTrue(importsFolderInWorkspace == importsFolderInWorkspace2);
  }

  private void doAssertContentFolderPermissions(Folder apiInboxInWorkspace) {
    assertFalse(aclPermits(apiInboxInWorkspace, user, PermissionType.DELETE));
    assertFalse(aclPermits(apiInboxInWorkspace, user, PermissionType.COPY));
    assertFalse(aclPermits(apiInboxInWorkspace, user, PermissionType.RENAME));
    assertFalse(aclPermits(apiInboxInWorkspace, user, PermissionType.SEND));
    // can create content inside
    assertTrue(aclPermits(apiInboxInWorkspace, user, PermissionType.CREATE));
    assertTrue(aclPermits(apiInboxInWorkspace, user, PermissionType.CREATE_FOLDER));
  }

  @Test()
  public void getApiInboxChildPermissions() {
    mockWorkspaceApiInboxExists("", null);
    mockGetRootFolder(setup);
    Folder apiInboxInWorkspace = folderMgr.getApiUploadTargetFolder("", user, null);
    Folder normalSubfolder = recordFactory.createFolder("any", user);
    apiInboxInWorkspace.addChild(normalSubfolder, user);
    assertTrue(aclPermits(normalSubfolder, user, PermissionType.DELETE));
    assertTrue(aclPermits(normalSubfolder, user, PermissionType.COPY));
    assertTrue(aclPermits(normalSubfolder, user, PermissionType.SEND));
    assertTrue(aclPermits(normalSubfolder, user, PermissionType.RENAME));
    assertTrue(aclPermits(normalSubfolder, user, PermissionType.CREATE));
    assertTrue(aclPermits(normalSubfolder, user, PermissionType.CREATE_FOLDER));
  }

  private boolean aclPermits(Folder folder, User user, PermissionType permission) {
    return folder.getSharingACL().isPermitted(user, permission);
  }

  @Test()
  public void getApiInboxGalleryHappyCase() {
    mockWorkspaceApiInboxExists(AUDIO_MEDIA_FLDER_NAME, null);
    mockGetRootFolder(setup);
    Folder audioFolder = findGalleryFolder(setup, AUDIO_MEDIA_FLDER_NAME);
    when(folderDao.getSystemFolderForUserByName(user, AUDIO_MEDIA_FLDER_NAME))
        .thenReturn(audioFolder);
    Folder apiInboxInGallery =
        folderMgr.getApiUploadTargetFolder(AUDIO_MEDIA_FLDER_NAME, user, null);
    assertEquals(audioFolder, apiInboxInGallery.getParent());

    // call again, it should return existing folder, i.e dao method nnly called once
    mockWorkspaceApiInboxExists(AUDIO_MEDIA_FLDER_NAME, apiInboxInGallery);
    Folder apiInboxInWorkspace2 =
        folderMgr.getApiUploadTargetFolder(AUDIO_MEDIA_FLDER_NAME, user, null);
    Mockito.verify(folderDao, Mockito.times(1))
        .getSystemFolderForUserByName(user, AUDIO_MEDIA_FLDER_NAME);
    assertTrue(apiInboxInGallery == apiInboxInWorkspace2);
  }

  private Folder findGalleryFolder(UserFolderSetup setup, String galleryFolderName) {
    return (Folder)
        setup.getMediaRoot().getChildrens().stream()
            .filter(r -> galleryFolderName.equals(r.getName()))
            .findFirst()
            .get();
  }

  @Test(expected = IllegalArgumentException.class)
  public void getWorkspaceApiUploadTargetFolderThrowsIAEIfDesiredTargetInSharedFolder() {
    mockGetRootFolder(setup);
    mockGetFolderToReturn(setup.getShared());
    folderMgr.getApiUploadTargetFolder("", user, 1L);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getWorkspaceApiUploadTargetFolderThrowsIAEIfDesiredTargetInSTemplatesFolder() {
    mockGetRootFolder(setup);
    mockGetFolderToReturn(setup.getTemplateFolder());
    folderMgr.getApiUploadTargetFolder("", user, 1L);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getWorkspaceApiUploadTargetFolderThrowsIAEIfDesiredTargetInGalleryFolder() {
    mockGetRootFolder(setup);
    mockGetFolderToReturn(setup.getMediaRoot());
    folderMgr.getApiUploadTargetFolder("", user, 1L);
  }

  @Test(expected = AuthorizationException.class)
  public void getWorkspaceApiUploadTargetFolderThrowsAuthIfNoReadPermissionOnTargetFolder() {
    mockGetRootFolder(setup);
    mockGetFolderToReturn(setup.getUserRoot());
    when(permUtils.isPermitted(setup.getUserRoot(), PermissionType.READ, user)).thenReturn(false);
    folderMgr.getApiUploadTargetFolder("", user, 1L);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getGalleryApiUploadTargetFolderThrowThrowsIAEIfDesiredTargetInWorkspace() {
    mockGetRootFolder(setup);
    mockGetFolderToReturn(setup.getUserRoot());
    folderMgr.getApiUploadTargetFolder(MediaUtils.IMAGES_MEDIA_FLDER_NAME, user, 1L);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getGalleryApiUploadTargetFolderThrowThrowsIAEIfDesiredTargetInTemplates() {
    mockGetRootFolder(setup);
    mockGetFolderToReturn(setup.getTemplateFolder());
    folderMgr.getApiUploadTargetFolder(MediaUtils.IMAGES_MEDIA_FLDER_NAME, user, 1L);
  }

  private void mockGetFolderToReturn(Folder folderToReturnOnGet) {
    when(folderDao.getSafeNull(Mockito.anyLong())).thenReturn(Optional.of(folderToReturnOnGet));
    when(permUtils.isPermitted(folderToReturnOnGet, PermissionType.READ, user)).thenReturn(true);
  }

  private void mockGetRootFolder(UserFolderSetup setup) {
    when(folderDao.getRootRecordForUser(user)).thenReturn(setup.getUserRoot());
  }

  private void mockWorkspaceApiInboxExists(String folderName, Folder optionalFolder) {
    when(folderDao.getApiFolderForContentType(folderName, user))
        .thenReturn(Optional.ofNullable(optionalFolder));
  }

  private void mockWorkspaceImportExists(Folder optionalFolder) {
    when(folderDao.getImportFolder(user)).thenReturn(Optional.ofNullable(optionalFolder));
  }

  @Test
  public void testFolderStructure() {
    User user = TestFactory.createAnyUser("any");
    UserFolderSetup setup = FolderTestUtils.createDefaultFolderStructure(user);
    setup.getTemplateFolder();
  }

  @Test
  public void testGetOwnerParent() throws InterruptedException, IllegalAddChildOperation {
    User anyuser = TestFactory.createAnyUser("any");
    User otheruser = TestFactory.createAnyUser("other");
    StructuredDocument sd = TestFactory.createAnySD();
    UserFolderSetup setup = FolderTestUtils.createDefaultFolderStructure(anyuser);
    anyuser.setRootFolder(setup.getUserRoot());
    sd = TestFactory.createAnySD();
    sd.setOwner(anyuser);
    setup.getUserRoot().addChild(sd, anyuser);
    assertEquals(setup.getUserRoot(), sd.getOwnerParent().get());
    assertEquals(setup.getUserRoot(), setup.getMediaRoot().getOwnerParent().get());
    // now create another folder parent for SD.
    Folder otherRoot = TestFactory.createAFolder("otherRoort", otheruser);
    otheruser.setRootFolder(otherRoot);
    Folder groupFolder =
        recordFactory.createCommunalGroupFolder(new Group("any", anyuser), otheruser);
    setup.getShared().addChild(groupFolder, anyuser);
    groupFolder.addChild(sd, anyuser);
    // now test that
    assertEquals(setup.getUserRoot(), sd.getOwnerParent().get());
    // if parent is null, we get null - not an exception:
  }
}

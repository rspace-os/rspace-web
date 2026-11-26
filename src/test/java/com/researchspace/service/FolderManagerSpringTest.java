package com.researchspace.service;

import static com.researchspace.core.util.MediaUtils.IMAGES_MEDIA_FLDER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.license.InactiveLicenseTestService;
import com.researchspace.licensews.LicenseExpiredException;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.controller.ServiceLoggerAspct;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.stat.EntityStatistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FolderManagerSpringTest extends SpringTransactionalTest {

  private static final ACLElement SOMEONE_ELSE_READ =
      new ACLElement(
          "someoneElse",
          new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ));

  User user;

  private @Autowired RecordManager recordManager;
  private @Autowired LicenseService licenseService;
  private @Autowired ServiceLoggerAspct aspect;
  private @Autowired SharingHandler sharingHandler;

  @BeforeEach
  public void setUp() throws Exception {
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("fuser"));
    initialiseContentWithEmptyContent(user);
    assertTrue(user.isContentInitialized());
  }

  @AfterEach
  public void tearDown() throws Exception {
    aspect.setLicenseService(licenseService); // restore default
    super.tearDown();
  }

  @Test
  public void testGetMediaTypeFolder() throws Exception {
    // create a new folder
    assertNotNull(recordManager.getGalleryMediaFolderForUser("unknown", user));
    // should be already present
    assertNotNull(recordManager.getGalleryMediaFolderForUser(IMAGES_MEDIA_FLDER_NAME, user));
  }

  @Test
  public void testRemoveFolder() throws Exception {
    // we'll delete the image folder from the media folder
    Folder toRemove = recordManager.getGalleryMediaFolderForUser(IMAGES_MEDIA_FLDER_NAME, user);
    Folder parent = folderDao.getGalleryRootFolderForUser(user);
    int numChildrenB4 = folderDao.getChildren(parent).size();
    // remove child
    parent = folderMgr.removeBaseRecordFromFolder(toRemove, parent.getId());

    // get new child count
    int numChildrenAfter = folderDao.getChildren(parent).size();
    // check there is now 1 less child
    assertEquals(numChildrenB4 - 1, numChildrenAfter);
  }

  @Test
  public void addAndRemoveFolderUpdatesAclOfWholeContent() {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    initialiseContentWithEmptyContent(pi);
    User member = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    initialiseContentWithEmptyContent(member);
    Group group = createGroup("g1", pi);
    addUsersToGroup(pi, group, member);
    logoutAndLoginAs(pi);
    Folder outer = folderMgr.createNewFolder(pi.getRootFolder().getId(), "outer", pi);
    Folder inner = folderMgr.createNewFolder(outer.getId(), "inner", pi);
    StructuredDocument doc = createBasicDocumentInFolder(pi, inner, "any");
    List<ACLElement> before = storedAcl(doc.getId());
    Folder groupFolder = folderDao.getSharedFolderForGroup(group);
    List<ACLElement> groupAcl = List.copyOf(groupFolder.getSharingACL().getAclElements());
    assertThat(groupAcl).isNotEmpty();

    folderMgr.addChild(groupFolder.getId(), outer, pi, ACLPropagationPolicy.DEFAULT_POLICY);
    assertThat(storedAcl(doc.getId())).containsAll(groupAcl);

    folderMgr.removeBaseRecordFromFolder(folderDao.get(outer.getId()), groupFolder.getId());
    assertThat(storedAcl(doc.getId())).containsExactlyInAnyOrderElementsOf(before);
  }

  @Test
  public void moveFolderUpdatesAclOfWholeContent() {
    Long rootId = user.getRootFolder().getId();
    Folder dest = folderMgr.createNewFolder(rootId, "dest", user);
    dest.getSharingACL().addACLElement(SOMEONE_ELSE_READ);
    Folder outer = folderMgr.createNewFolder(rootId, "outer", user);
    Folder inner = folderMgr.createNewFolder(outer.getId(), "inner", user);
    StructuredDocument doc = createBasicDocumentInFolder(user, inner, "any");
    Notebook notebook =
        folderMgr.createNewNotebook(outer.getId(), "nb", new DefaultRecordContext(), user);
    StructuredDocument entry = createBasicDocumentInFolder(user, notebook, "entry");
    List<ACLElement> docBefore = storedAcl(doc.getId());
    List<ACLElement> entryBefore = storedAcl(entry.getId());

    assertTrue(folderMgr.move(outer.getId(), dest.getId(), rootId, user).isSucceeded());
    assertThat(storedAcl(doc.getId())).contains(SOMEONE_ELSE_READ);
    assertThat(storedAcl(entry.getId())).contains(SOMEONE_ELSE_READ);

    assertTrue(folderMgr.move(outer.getId(), rootId, dest.getId(), user).isSucceeded());
    assertThat(storedAcl(doc.getId())).containsExactlyInAnyOrderElementsOf(docBefore);
    assertThat(storedAcl(entry.getId())).containsExactlyInAnyOrderElementsOf(entryBefore);
  }

  @Test
  public void moveKeepsAclInheritedThroughFolderWhenDocIsAlsoInNotebook() {
    Long rootId = user.getRootFolder().getId();
    ACLElement labAdminRead =
        new ACLElement(
            "labGroup[PI,RS_LAB_ADMIN]",
            new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ));
    Folder src = folderMgr.createNewFolder(rootId, "src", user);
    src.getSharingACL().addACLElement(labAdminRead);
    Folder dest = folderMgr.createNewFolder(rootId, "dest", user);
    dest.getSharingACL().addACLElement(labAdminRead);
    Folder outer = folderMgr.createNewFolder(src.getId(), "outer", user);
    Folder left = folderMgr.createNewFolder(outer.getId(), "left", user);
    Folder right = folderMgr.createNewFolder(outer.getId(), "right", user);
    Notebook leftNotebook =
        folderMgr.createNewNotebook(left.getId(), "leftNb", new DefaultRecordContext(), user);
    Notebook rightNotebook =
        folderMgr.createNewNotebook(right.getId(), "rightNb", new DefaultRecordContext(), user);
    StructuredDocument leftDoc = createBasicDocumentInFolder(user, left, "leftDoc");
    StructuredDocument rightDoc = createBasicDocumentInFolder(user, right, "rightDoc");
    // cross-linked, so whatever the child order one doc is reached through its folder first
    folderMgr.addChild(leftNotebook.getId(), rightDoc, user);
    folderMgr.addChild(rightNotebook.getId(), leftDoc, user);
    flushDatabaseState();
    clearSessionAndEvictAll();

    assertTrue(folderMgr.move(outer.getId(), dest.getId(), src.getId(), user).isSucceeded());
    assertThat(storedAcl(leftDoc.getId())).contains(labAdminRead);
    assertThat(storedAcl(rightDoc.getId())).contains(labAdminRead);
  }

  @Test
  public void movingGalleryFolderWithSubfolderKeepsItsContent() {
    Folder images = recordManager.getGalleryMediaFolderForUser(IMAGES_MEDIA_FLDER_NAME, user);
    Folder moving = folderMgr.createGallerySubfolder("moving", IMAGES_MEDIA_FLDER_NAME, user);
    Folder dest = folderMgr.createGallerySubfolder("dest", IMAGES_MEDIA_FLDER_NAME, user);
    Folder child = folderMgr.createNewFolder(moving.getId(), "child", user);
    flushDatabaseState();
    clearSessionAndEvictAll();

    assertTrue(folderMgr.move(moving.getId(), dest.getId(), images.getId(), user).isSucceeded());

    assertThat(folderDao.getFolderChildrenIds(dest)).containsExactly(moving.getId());
    assertThat(folderDao.getFolderChildrenIds(moving)).containsExactly(child.getId());
    assertThat(folderDao.getFolderChildrenIds(images)).doesNotContain(moving.getId());
  }

  @Test
  public void addingProxiedFolderPersistsItsEdge() {
    Long rootId = user.getRootFolder().getId();
    Folder dest = folderMgr.createNewFolder(rootId, "dest", user);
    Folder child = folderMgr.createNewFolder(rootId, "child", user);
    flushDatabaseState();
    clearSessionAndEvictAll();
    Folder proxy = sessionFactory.getCurrentSession().getReference(Folder.class, child.getId());
    assertInstanceOf(HibernateProxy.class, proxy);

    folderMgr.addChild(dest.getId(), proxy, user, ACLPropagationPolicy.NULL_POLICY);
    flushDatabaseState();

    assertThat(folderDao.getFolderChildrenIds(dest)).containsExactly(child.getId());
  }

  @Test
  public void movingFolderDoesNotLoadOtherContentOfDestination() {
    Long rootId = user.getRootFolder().getId();
    Folder dest = folderMgr.createNewFolder(rootId, "dest", user);
    int siblings = 30;
    for (int i = 0; i < siblings; i++) {
      folderMgr.createNewFolder(dest.getId(), "sibling" + i, user);
    }
    Folder moving = folderMgr.createNewFolder(rootId, "moving", user);

    EntityStatistics folderStats =
        entityStatisticsFor(
            Folder.class,
            () ->
                assertTrue(
                    folderMgr.move(moving.getId(), dest.getId(), rootId, user).isSucceeded()));
    assertThat(folderStats.getLoadCount()).isLessThan(siblings);
    assertThat(folderDao.getFolderChildrenIds(dest)).contains(moving.getId());
  }

  @Test
  public void movingFolderWritesEachItemInsideOnce() {
    Long rootId = user.getRootFolder().getId();
    Folder dest = folderMgr.createNewFolder(rootId, "dest", user);
    dest.getSharingACL().addACLElement(SOMEONE_ELSE_READ);
    Folder moving = folderMgr.createNewFolder(rootId, "moving", user);
    // listed before the documents, as rows come in edge id order; walking into it flushes them
    folderMgr.createNewFolder(moving.getId(), "sub", user);
    int docs = 3;
    for (int i = 0; i < docs; i++) {
      createBasicDocumentInFolder(user, moving, "doc" + i);
    }

    EntityStatistics docStats =
        entityStatisticsFor(
            StructuredDocument.class,
            () ->
                assertTrue(
                    folderMgr.move(moving.getId(), dest.getId(), rootId, user).isSucceeded()));
    assertEquals(docs, docStats.getUpdateCount());
  }

  @Test
  public void moveThatOnlyRemovesOldEdgeStillUpdatesAclOfContent() {
    Long rootId = user.getRootFolder().getId();
    Folder src = folderMgr.createNewFolder(rootId, "src", user);
    src.getSharingACL().addACLElement(SOMEONE_ELSE_READ);
    Folder dest = folderMgr.createNewFolder(rootId, "dest", user);
    Folder outer = folderMgr.createNewFolder(src.getId(), "outer", user);
    StructuredDocument doc = createBasicDocumentInFolder(user, outer, "any");
    assertThat(doc.getSharingACL().getAclElements()).contains(SOMEONE_ELSE_READ);
    folderMgr.addChild(dest.getId(), outer, user, ACLPropagationPolicy.NULL_POLICY);

    // dest already holds outer, so only the removal from src happens
    assertFalse(folderMgr.move(outer.getId(), dest.getId(), src.getId(), user).isSucceeded());
    assertThat(storedAcl(doc.getId())).doesNotContain(SOMEONE_ELSE_READ);
  }

  @Test
  public void removingOrMovingFolderFromNonParentLeavesAclOfContent() {
    Long rootId = user.getRootFolder().getId();
    Folder other = folderMgr.createNewFolder(rootId, "other", user);
    Folder outer = folderMgr.createNewFolder(rootId, "outer", user);
    StructuredDocument doc = createBasicDocumentInFolder(user, outer, "any");
    List<ACLElement> before = List.copyOf(doc.getSharingACL().getAclElements());

    folderMgr.removeBaseRecordFromFolder(outer, other.getId());
    assertThat(storedAcl(doc.getId())).containsExactlyInAnyOrderElementsOf(before);

    Folder target = folderMgr.createNewFolder(rootId, "target", user);
    assertFalse(folderMgr.move(outer.getId(), target.getId(), other.getId(), user).isSucceeded());
    assertThat(storedAcl(doc.getId())).containsExactlyInAnyOrderElementsOf(before);
  }

  @Test
  public void removingDetachedChildDeletesItsEdge() {
    Folder parent = folderMgr.createNewFolder(user.getRootFolder().getId(), "parent", user);
    StructuredDocument doc = createBasicDocumentInFolder(user, parent, "any");
    flushDatabaseState();
    assertThat(folderDao.getFolderChildrenIds(parent)).contains(doc.getId());
    clearSessionAndEvictAll();
    Record detached = recordManager.get(doc.getId());
    clearSessionAndEvictAll();

    folderMgr.removeBaseRecordFromFolder(detached, parent.getId());
    flushDatabaseState();

    assertThat(folderDao.getFolderChildrenIds(parent)).doesNotContain(doc.getId());
  }

  @Test
  public void savingStaleCopyOfRecordKeepsFolderEdgesAddedSinceItWasLoaded() {
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "any");
    Folder other = folderMgr.createNewFolder(user.getRootFolder().getId(), "other", user);
    flushDatabaseState();
    clearSessionAndEvictAll();
    Record stale = recordManager.get(doc.getId());
    clearSessionAndEvictAll();

    folderMgr.addChild(
        other.getId(), recordManager.get(doc.getId()), user, ACLPropagationPolicy.NULL_POLICY);
    flushDatabaseState();
    clearSessionAndEvictAll();
    sessionFactory.getCache().evictAllRegions();
    stale.setName("renamed");
    recordManager.save(stale, user);
    flushDatabaseState();

    assertThat(folderDao.getFolderChildrenIds(other)).contains(doc.getId());
  }

  @Test
  public void testGetDeletedFolder() throws Exception {
    logoutAndLoginAs(user);
    Folder folder =
        folderMgr.createNewFolder(
            user.getRootFolder().getId(), getRandomAlphabeticString("F"), user);
    folderMgr.removeBaseRecordFromFolder(folder, folder.getParent().getId());
    Long folderId = folder.getId();
    assertThrows(AuthorizationException.class, () -> folderMgr.getFolder(folderId, user));
  }

  @Test
  public void testGetFolderPermissions() throws Exception {
    final User u1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    final User u2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u2"));
    initialiseContentWithEmptyContent(u1, u2);

    logoutAndLoginAs(u1);
    final Folder f1 = folderMgr.getRootRecordForUser(u1, u1);
    assertThrows(AuthorizationException.class, () -> folderMgr.getRootRecordForUser(u1, u2));

    logoutAndLoginAs(u2);
    final Folder f2 = folderMgr.getRootRecordForUser(u2, u2);
    assertThrows(AuthorizationException.class, () -> folderMgr.getRootRecordForUser(u1, u2));

    // now try getFolder method
    folderMgr.getFolder(f2.getId(), u2);
    Long folderId = f1.getId();
    assertThrows(AuthorizationException.class, () -> folderMgr.getFolder(folderId, u2));
  }

  @Test
  public void testRemoveRecord() {
    int b4 = folderDao.getChildren(user.getRootFolder()).size();
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(user, "any");
    assertEquals(b4 + 1, getNumChildrenInRootFolder(user));

    // remove child
    Folder uRoot = folderMgr.removeBaseRecordFromFolder(sd, user.getRootFolder().getId());

    // get new child count
    int numChildrenAfter = folderDao.getChildren(uRoot).size();
    // check there is now 1 less child
    assertEquals(b4, numChildrenAfter);
  }

  @Test
  public void isFolderInSharedTreeTest() throws IllegalAddChildOperation {
    User piUser = createAndSaveAPi();
    initialiseContentWithEmptyContent(piUser);
    assertTrue(piUser.isContentInitialized());
    logoutAndLoginAs(piUser);

    Group group = createGroup("groupName", piUser);
    addUsersToGroup(piUser, group, user);
    Folder grpFolderShared = folderMgr.getFolder(group.getCommunalGroupFolderId(), piUser);

    Notebook notebookShared = recordFactory.createNotebook("notebookShared", user);
    sharingHandler.shareIntoSharedFolderOrNotebook(
        user,
        grpFolderShared,
        notebookShared.getId(),
        grpFolderShared.getParents().stream()
            .filter(recToParent -> recToParent.getFolder().getOwner().equals(user))
            .findFirst()
            .get()
            .getFolder()
            .getId());

    assertTrue(folderMgr.isFolderInSharedTree(notebookShared, grpFolderShared.getId(), user));
    assertTrue(folderMgr.isFolderInSharedTree(notebookShared, grpFolderShared.getId(), piUser));
  }

  @Test
  public void getGroupFolderRootFromSharedSubfolderTest() throws IllegalAddChildOperation {
    Folder userLabGroupFolder = folderDao.getLabGroupFolderForUser(user);

    // create a dummmy shared group folder with a shared folder underne
    Folder grpFolder = recordFactory.createFolder("agroup", user);
    grpFolder.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);
    folderMgr.addChild(userLabGroupFolder.getId(), grpFolder, user);

    Folder grpSubFolder = recordFactory.createFolder("agroupSubFolder", user);
    folderMgr.addChild(grpFolder.getId(), grpSubFolder, user);
    assertEquals(
        grpFolder,
        folderMgr
            .getGroupOrIndividualShrdFolderRootFromSharedSubfolder(grpSubFolder.getId(), null, user)
            .get());
    // a folder not a subfolder of group folder will return null.
    assertThat(
            folderMgr.getGroupOrIndividualShrdFolderRootFromSharedSubfolder(
                userLabGroupFolder.getId(), null, user))
        .isNotPresent();

    // now check the same type of assertion, but for individual shared folders
    Folder individSharedFolderRoot = folderDao.getIndividualSharedItemsFolderForUser(user);
    Folder individSharedFolder = recordFactory.createFolder("userA-userB", user);
    individSharedFolder.addType(RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT);
    folderMgr.addChild(individSharedFolderRoot.getId(), individSharedFolder, user);

    Folder sharedWork = recordFactory.createFolder("sharedFolder", user);
    folderMgr.addChild(individSharedFolder.getId(), sharedWork, user);
    assertEquals(
        individSharedFolder,
        folderMgr
            .getGroupOrIndividualShrdFolderRootFromSharedSubfolder(sharedWork.getId(), null, user)
            .get());
  }

  @Test
  public void folderCreationRequireActiveLicense() {
    aspect.setLicenseService(new InactiveLicenseTestService());
    logoutAndLoginAs(user);
    Long rootFolderId = user.getRootFolder().getId();
    assertThrows(
        LicenseExpiredException.class, () -> folderMgr.createNewFolder(rootFolderId, "any", user));
  }

  @Test
  public void notebookCreationRequireActiveLicense() {
    aspect.setLicenseService(new InactiveLicenseTestService());
    logoutAndLoginAs(user);
    Long rootFolderId = user.getRootFolder().getId();
    DefaultRecordContext context = new DefaultRecordContext();
    assertThrows(
        LicenseExpiredException.class,
        () -> folderMgr.createNewNotebook(rootFolderId, "any", context, user));
  }

  @Test
  public void userNeedsPermissionsToCreateNotebookAndFolder() throws Exception {
    final User userA = createAndSaveUserIfNotExists(getRandomAlphabeticString("A"));
    final User userB = createAndSaveUserIfNotExists(getRandomAlphabeticString("B"));
    initialiseContentWithEmptyContent(userA, userB);
    logoutAndLoginAs(userA);
    // ok, has permission to create stuff in their own folder
    folderMgr.createNewFolder(userA.getRootFolder().getId(), "any", userA);
    // but can't create stuff in someone elses folder
    Long otherRootFolderId = userB.getRootFolder().getId();
    assertThrows(
        AuthorizationException.class,
        () -> folderMgr.createNewFolder(otherRootFolderId, "any", userA));

    // same for notebook
    folderMgr.createNewNotebook(
        userA.getRootFolder().getId(), "any", new DefaultRecordContext(), userA);
    DefaultRecordContext context = new DefaultRecordContext();
    assertThrows(
        AuthorizationException.class,
        () -> folderMgr.createNewNotebook(otherRootFolderId, "any", context, userA));
  }
}

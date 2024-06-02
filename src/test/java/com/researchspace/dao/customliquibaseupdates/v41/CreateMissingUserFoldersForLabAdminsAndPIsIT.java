package com.researchspace.dao.customliquibaseupdates.v41;

import static org.junit.Assert.assertEquals;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CreateMissingUserFoldersForLabAdminsAndPIsIT extends RealTransactionSpringTestBase {

  private CreateMissingUserFoldersForLabAdminsAndPIs userFoldersCreator;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    userFoldersCreator = new CreateMissingUserFoldersForLabAdminsAndPIs();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void checkMemberFolderCreatedForAdmins() throws Exception {

    /* set up pi, lab admin and two members */
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User labAdmin = createAndSaveUser(getRandomAlphabeticString("labAdmin"));
    User member1 = createAndSaveUser(getRandomAlphabeticString("member1"));
    User member2 = createAndSaveUser(getRandomAlphabeticString("member2"));
    initUsers(pi, labAdmin, member1, member2);

    Group group =
        createGroupForUsers(
            pi, pi.getUsername(), labAdmin.getUsername(), pi, labAdmin, member1, member2);
    logoutAndLoginAs(pi);
    grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), pi, group.getId(), true);

    // remove first member's folder from pi's and lab admin's shared labgroups
    doInTransaction(
        () -> {
          Long piLabGroupsFolderId = folderDao.getLabGroupFolderForUser(pi).getId();
          Long labAdminLabGroupsFolderId = folderDao.getLabGroupFolderForUser(labAdmin).getId();
          Folder member1Root = member1.getRootFolder();
          folderMgr.removeBaseRecordFromFolder(member1Root, piLabGroupsFolderId);
          folderMgr.removeBaseRecordFromFolder(member1Root, labAdminLabGroupsFolderId);

          // assert the folders are removed
          assertEquals(3, folderDao.getLabGroupFolderForUser(pi).getChildren().size());
          assertEquals(2, folderDao.getLabGroupFolderForUser(labAdmin).getChildren().size());
        });

    // run update
    doInTransaction(
        () -> {
          userFoldersCreator.setUp();
          userFoldersCreator.execute(null);
        });

    // spy that folder adding code was run once for pi and once for lab admin
    assertEquals(1, userFoldersCreator.affectedPiCounter);
    assertEquals(1, userFoldersCreator.piFoldersCreatedCounter);
    assertEquals(1, userFoldersCreator.affectedLabAdminCounter);
    assertEquals(1, userFoldersCreator.labAdminFoldersCreatedCounter);

    // assert the folders are there
    doInTransaction(
        () -> {
          assertEquals(4, folderDao.getLabGroupFolderForUser(pi).getChildren().size());
          assertEquals(3, folderDao.getLabGroupFolderForUser(labAdmin).getChildren().size());
        });
  }

  @Test
  public void checkUserFolderCreatedOnlyOnceForTwoGroups() throws Exception {

    /* set up pi and member */
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User member = createAndSaveUser(getRandomAlphabeticString("member1"));
    initUsers(pi, member);

    /* set up two groups */
    createGroupForUsers(pi, pi.getUsername(), null, pi, member);
    createGroupForUsers(pi, pi.getUsername(), null, pi, member);

    /* remove member's folder from PIs labgroup folder */
    doInTransaction(
        () -> {
          Long piLabGroupsFolderId = folderDao.getLabGroupFolderForUser(pi).getId();
          Folder userRoot = member.getRootFolder();
          folderMgr.removeBaseRecordFromFolder(userRoot, piLabGroupsFolderId);
          assertEquals(2, folderDao.getLabGroupFolderForUser(pi).getChildren().size());
        });
    /* assert only group folders visible by PI */

    doInTransaction(
        () -> {
          userFoldersCreator.setUp();
          userFoldersCreator.execute(null);
        });

    // spy that folder adding code was run once
    assertEquals(1, userFoldersCreator.affectedPiCounter);
    assertEquals(1, userFoldersCreator.piFoldersCreatedCounter);
    assertEquals(0, userFoldersCreator.affectedLabAdminCounter);
    assertEquals(0, userFoldersCreator.labAdminFoldersCreatedCounter);

    // assert a single folder created
    doInTransaction(
        () -> {
          assertEquals(3, folderDao.getLabGroupFolderForUser(pi).getChildren().size());
        });
  }
}

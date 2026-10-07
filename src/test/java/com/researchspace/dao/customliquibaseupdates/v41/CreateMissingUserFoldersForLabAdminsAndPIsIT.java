package com.researchspace.dao.customliquibaseupdates.v41;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CreateMissingUserFoldersForLabAdminsAndPIsIT extends RealTransactionSpringTestBase {

  private CreateMissingUserFoldersForLabAdminsAndPIs userFoldersCreator;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    userFoldersCreator = new CreateMissingUserFoldersForLabAdminsAndPIs();
  }

  @AfterEach
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
          Folder member1Root = folderDao.getRootRecordForUser(member1);
          folderMgr.removeBaseRecordFromFolder(member1Root, piLabGroupsFolderId);
          folderMgr.removeBaseRecordFromFolder(member1Root, labAdminLabGroupsFolderId);

          // assert the folders are removed
          assertThat(folderDao.getLabGroupFolderForUser(pi).getChildren()).hasSize(3);
          assertThat(folderDao.getLabGroupFolderForUser(labAdmin).getChildren()).hasSize(2);
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
          assertThat(folderDao.getLabGroupFolderForUser(pi).getChildren()).hasSize(4);
          assertThat(folderDao.getLabGroupFolderForUser(labAdmin).getChildren()).hasSize(3);
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
          Folder piLabGroupsFolder = folderDao.getLabGroupFolderForUser(pi);
          assertThat(folderDao.getFolderChildrenIds(piLabGroupsFolder)).hasSize(3);
          Folder userRoot = folderDao.getRootRecordForUser(member);
          folderMgr.removeBaseRecordFromFolder(userRoot, piLabGroupsFolder.getId());
          assertThat(folderDao.getFolderChildrenIds(piLabGroupsFolder)).hasSize(2);
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
          assertThat(folderDao.getLabGroupFolderForUser(pi).getChildren()).hasSize(3);
        });
  }
}

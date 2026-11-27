package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.transaction.support.TransactionTemplate;

/** RSDEV-1400: one membership, and one set of group folder links, per group and user. */
class GroupMembershipIT extends RealTransactionSpringTestBase {

  private static final int THREADS = 4;

  private @Autowired IGroupCreationStrategy groupCreationStrategy;

  private JdbcTemplate jdbc;
  private User pi;
  private User user;
  private Group group;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    jdbc = new JdbcTemplate(dataSource);
    pi = createAndSaveUser(getRandomAlphabeticString("gmPi"), Constants.PI_ROLE);
    user = createAndSaveUser(getRandomAlphabeticString("gmUser"));
    initUsers(pi, user);
    group = createGroupForPiAndUsers(pi, new User[] {pi});
    assertNotNull(group.getCommunalGroupFolderId());
    assertNotNull(group.getSharedSnippetGroupFolderId());
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  void concurrentAddsCreateOneMembership() throws Exception {
    // with snapshot isolation the waiting adds fail (error 1020) instead of seeing the first one
    List<String> snapshotIsolation =
        jdbc.query(
            "SHOW VARIABLES LIKE 'innodb_snapshot_isolation'", (rs, i) -> rs.getString("Value"));
    assertTrue(
        snapshotIsolation.isEmpty() || "OFF".equalsIgnoreCase(snapshotIsolation.get(0)),
        "this test needs innodb_snapshot_isolation=0, as CI and the dev stack configure");

    Long groupId = group.getId();
    CyclicBarrier allHaveReadTheGroup = new CyclicBarrier(THREADS);
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    List<Future<?>> adds = new ArrayList<>();
    try {
      for (int i = 0; i < THREADS; i++) {
        adds.add(
            pool.submit(
                () ->
                    new TransactionTemplate(getTxMger())
                        .executeWithoutResult(
                            tx -> {
                              // each add has seen "not a member" before any of them adds
                              assertFalse(grpMgr.getGroup(groupId).hasMember(user));
                              awaitBarrier(allHaveReadTheGroup);
                              grpMgr.addUserToGroup(
                                  user.getUsername(), groupId, RoleInGroup.DEFAULT);
                            })));
      }
      List<Throwable> failures = new ArrayList<>();
      for (Future<?> add : adds) {
        try {
          add.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
          failures.add(e);
        }
      }

      assertEquals(1, membershipRows(), "membership rows");
      assertEquals(
          1, liveLinks(group.getCommunalGroupFolderId()), "links to the group shared folder");
      assertEquals(
          1, liveLinks(group.getSharedSnippetGroupFolderId()), "links to the shared snippets");
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM GroupMembershipEvent"
                  + " WHERE group_id = ? AND user_id = ? AND groupEventType = 'JOIN'",
              Integer.class,
              groupId,
              user.getId()),
          "join events");
      assertTrue(failures.isEmpty(), () -> "adds failed: " + failures);
    } finally {
      pool.shutdownNow();
    }

    User sysadmin = logoutAndLoginAsSysAdmin();
    doInTransaction(() -> grpMgr.removeUserFromGroup(user.getUsername(), groupId, sysadmin));
    assertEquals(0, membershipRows(), "a single removal removes the membership");
    assertEquals(0, liveLinks(group.getCommunalGroupFolderId()));
  }

  @Test
  void removeThenReAddInOneTransactionKeepsOneRow() throws Exception {
    doInTransaction(
        () -> grpMgr.addUserToGroup(user.getUsername(), group.getId(), RoleInGroup.DEFAULT));
    assertEquals(1, membershipRows());
    User sysadmin = logoutAndLoginAsSysAdmin();

    doInTransaction(
        () -> {
          grpMgr.removeUserFromGroup(user.getUsername(), group.getId(), sysadmin);
          grpMgr.addUserToGroup(user.getUsername(), group.getId(), RoleInGroup.DEFAULT);
        });

    assertEquals(1, membershipRows());
    assertEquals(1, liveLinks(group.getCommunalGroupFolderId()));
  }

  @Test
  void addingToAMissingGroupThrowsNotFound() {
    String username = user.getUsername();
    assertThrows(
        ObjectRetrievalFailureException.class,
        () -> grpMgr.addUserToGroup(username, Long.MAX_VALUE, RoleInGroup.DEFAULT));
  }

  @Test
  void groupCreationWithRepeatedMembersCreatesOneRowEach() throws Exception {
    User member = createAndSaveUser(getRandomAlphabeticString("gmMember"));
    initUsers(member);

    // the PI is in the members map and is also added as PI
    Group fromMap =
        doInTransaction(
            () ->
                groupCreationStrategy.createAndSaveGroup(
                    "rsdev1400",
                    pi,
                    pi,
                    GroupType.LAB_GROUP,
                    Map.of(pi, RoleInGroup.DEFAULT, member, RoleInGroup.DEFAULT)));
    assertMembers(fromMap, 2);

    // a member listed twice
    Group newGroup = new Group("rsdev1400b", pi);
    newGroup.setDisplayName("rsdev1400b");
    newGroup.setGroupType(GroupType.LAB_GROUP);
    newGroup.setPis(pi.getUsername());
    Group fromList =
        doInTransaction(
            () ->
                groupCreationStrategy.createAndSaveGroup(
                    newGroup, pi, List.of(pi, member, member)));
    assertMembers(fromList, 2);
  }

  private void assertMembers(Group grp, int expected) {
    assertEquals(
        expected,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM UserGroup WHERE group_id = ?", Integer.class, grp.getId()));
    assertEquals(
        expected,
        jdbc.queryForObject(
            "SELECT COUNT(DISTINCT user_id) FROM UserGroup WHERE group_id = ?",
            Integer.class,
            grp.getId()));
  }

  private int membershipRows() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM UserGroup WHERE group_id = ? AND user_id = ?",
        Integer.class,
        group.getId(),
        user.getId());
  }

  private int liveLinks(Long childFolderId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM RecordToFolder WHERE record_id = ? AND userName = ?"
            + " AND COALESCE(recordInFolderDeleted, 0) = 0",
        Integer.class,
        childFolderId,
        user.getUsername());
  }

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(30, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException("not all adds reached the barrier", e);
    }
  }
}

package com.researchspace.dao.customliquibaseupdates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.dao.UserGroupDao;
import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.MigrationFailedException;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs the RSDEV-1400 changesets on a populated schema seeded with duplicate group memberships and
 * duplicate group folder links, as concurrent adds created them before the fix.
 */
class DedupeGroupMemberships_RSDEV1400IT extends RealTransactionSpringTestBase {

  private static final String CHANGELOG = "sqlUpdates/changeLog-rsdev-1400.xml";
  private static final String KEY = "uk_UserGroup_group_user";
  private static final int DEDUPE = 0;
  private static final int CHECK_KEY = 1;
  private static final int ADD_KEY = 2;
  private static final String LIVE = " AND COALESCE(recordInFolderDeleted, 0) = 0";

  private @Autowired UserGroupDao userGroupDao;

  private JdbcTemplate jdbc;
  private final List<Long> seededMemberships = new ArrayList<>();
  private final List<Long> seededLinks = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    jdbc = new JdbcTemplate(dataSource);
    dropKey();
  }

  @AfterEach
  public void tearDown() throws Exception {
    try {
      for (Long id : seededMemberships) {
        jdbc.update("DELETE FROM UserGroup WHERE id = ?", id);
        jdbc.update("DELETE FROM RSDEV1400_RemovedUserGroup WHERE id = ?", id);
      }
      for (Long id : seededLinks) {
        jdbc.update("DELETE FROM RecordToFolder WHERE id = ?", id);
        jdbc.update("DELETE FROM RSDEV1400_RemovedRecordToFolder WHERE id = ?", id);
      }
      if (!compatibleKeyExists()) {
        dropKey();
        jdbc.execute("ALTER TABLE UserGroup ADD CONSTRAINT " + KEY + " UNIQUE (group_id, user_id)");
      }
    } finally {
      super.tearDown();
    }
  }

  @Test
  void keepsTheLowestIdMembershipUnchangedAndAddsTheKey() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("ddPi"), Constants.PI_ROLE);
    User demoted = createAndSaveUser(getRandomAlphabeticString("ddA"));
    User autoshareOff = createAndSaveUser(getRandomAlphabeticString("ddB"));
    User control = createAndSaveUser(getRandomAlphabeticString("ddC"));
    initUsers(pi, demoted, autoshareOff, control);
    Group group = createGroupForPiAndUsers(pi, new User[] {pi, demoted, autoshareOff, control});
    Long groupId = group.getId();
    Long communal = group.getCommunalGroupFolderId();
    Folder autoshareFolder =
        doInTransaction(() -> grpMgr.createAutoshareFolder(autoshareOff, grpMgr.getGroup(groupId)));
    Folder controlRoot = doInTransaction(() -> folderMgr.getRootFolderForUser(control));
    Folder ordinary = createSubFolder(controlRoot, "rsdev1400", control);

    long keptDemoted = membershipId(groupId, demoted);
    long keptAutoshareOff = membershipId(groupId, autoshareOff);
    long keptPi = membershipId(groupId, pi);
    long keptControl = membershipId(groupId, control);

    // demoted: the kept row says DEFAULT, a stale copy still says lab admin
    jdbc.update(
        "UPDATE UserGroup SET roleInGroup = ?, adminViewDocsEnabled = 0 WHERE id = ?",
        RoleInGroup.DEFAULT.ordinal(),
        keptDemoted);
    long staleLabAdmin =
        copyMembership(
            keptDemoted,
            "roleInGroup = "
                + RoleInGroup.RS_LAB_ADMIN.ordinal()
                + ", adminViewDocsEnabled = 1,"
                + " permissionStrings = 'RECORD:READ:group=rsdev1400'");
    // autoshare turned off on the kept row, on in a stale copy that has the folder
    jdbc.update(
        "UPDATE UserGroup SET autoshareEnabled = 0, autoShareFolder_id = NULL WHERE id = ?",
        keptAutoshareOff);
    long staleAutoshare =
        copyMembership(
            keptAutoshareOff,
            "autoshareEnabled = 1, autoShareFolder_id = " + autoshareFolder.getId());
    long stalePi = copyMembership(keptPi, "roleInGroup = " + RoleInGroup.DEFAULT.ordinal());

    long keptCommunalLink = liveLinkId(communal, demoted.getUsername());
    long duplicateCommunalLink = copyLink(keptCommunalLink, 0);
    long demotedHome = rootFolderId(demoted);
    long keptHomeLink =
        jdbc.queryForObject(
            "SELECT MIN(id) FROM RecordToFolder WHERE record_id = ?" + LIVE,
            Long.class,
            demotedHome);
    long piFolder =
        jdbc.queryForObject(
            "SELECT folder_id FROM RecordToFolder WHERE id = ?", Long.class, keptHomeLink);
    long duplicateHomeLink = copyLink(keptHomeLink, 0);
    long softDeletedDuplicate = copyLink(liveLinkId(communal, autoshareOff.getUsername()), 1);
    long ordinaryLink =
        jdbc.queryForObject(
            "SELECT MIN(id) FROM RecordToFolder WHERE record_id = ?" + LIVE,
            Long.class,
            ordinary.getId());
    long outOfScopeDuplicate = copyLink(ordinaryLink, 0);
    sessionFactory.getCache().evictAllRegions();

    Map<Long, Map<String, Object>> keptBefore = new LinkedHashMap<>();
    for (long id : List.of(keptDemoted, keptAutoshareOff, keptPi, keptControl)) {
      keptBefore.put(id, row(id));
    }
    System.out.printf(
        "RSDEV-1400 before: %d membership rows for %d members; %d live links to the group folder"
            + " for %s%n",
        count("SELECT COUNT(*) FROM UserGroup WHERE group_id = ?", groupId),
        count("SELECT COUNT(DISTINCT user_id) FROM UserGroup WHERE group_id = ?", groupId),
        count(
            "SELECT COUNT(*) FROM RecordToFolder WHERE record_id = ? AND userName = ?" + LIVE,
            communal,
            demoted.getUsername()),
        demoted.getUsername());

    runChangeSet(DEDUPE);
    runChangeSet(CHECK_KEY);
    runChangeSet(ADD_KEY);

    assertEquals(4, count("SELECT COUNT(*) FROM UserGroup WHERE group_id = ?", groupId));
    assertEquals(
        4, count("SELECT COUNT(DISTINCT user_id) FROM UserGroup WHERE group_id = ?", groupId));
    for (Map.Entry<Long, Map<String, Object>> kept : keptBefore.entrySet()) {
      assertEquals(kept.getValue(), row(kept.getKey()), "kept row " + kept.getKey());
    }
    assertEquals(
        Map.of(staleLabAdmin, keptDemoted, staleAutoshare, keptAutoshareOff, stalePi, keptPi),
        backedUpMemberships(groupId));
    assertEquals(
        RoleInGroup.RS_LAB_ADMIN.ordinal(),
        count("SELECT roleInGroup FROM RSDEV1400_RemovedUserGroup WHERE id = ?", staleLabAdmin));

    // the stale copy's autoshare folder stays in the group's shared folder
    assertEquals(
        1,
        count(
            "SELECT COUNT(*) FROM RecordToFolder WHERE record_id = ? AND folder_id = ?" + LIVE,
            autoshareFolder.getId(),
            communal));
    // live duplicates go (lowest id kept); soft-deleted and unrelated duplicates stay
    assertEquals(List.of(keptCommunalLink), liveLinkIds(communal, demoted.getUsername()));
    assertEquals(
        List.of(keptHomeLink),
        jdbc.queryForList(
            "SELECT id FROM RecordToFolder WHERE record_id = ? AND folder_id = ?" + LIVE,
            Long.class,
            demotedHome,
            piFolder));
    assertEquals(
        Map.of(duplicateCommunalLink, keptCommunalLink, duplicateHomeLink, keptHomeLink),
        backedUpLinks());
    assertEquals(
        1, count("SELECT COUNT(*) FROM RecordToFolder WHERE id = ?", softDeletedDuplicate));
    assertEquals(1, count("SELECT COUNT(*) FROM RecordToFolder WHERE id = ?", outOfScopeDuplicate));

    assertTrue(compatibleKeyExists());
    assertThat(doInTransaction(() -> userGroupDao.findByUserId(demoted.getId())))
        .filteredOn(ug -> ug.getGroup().getId().equals(groupId))
        .hasSize(1);

    // rerunning changes nothing, and an existing key is accepted
    runChangeSet(DEDUPE);
    runChangeSet(CHECK_KEY);
    runChangeSet(ADD_KEY);
    assertEquals(4, count("SELECT COUNT(*) FROM UserGroup WHERE group_id = ?", groupId));
    assertEquals(3, backedUpMemberships(groupId).size());
    assertTrue(compatibleKeyExists());

    long rejectedId = nextSeedId();
    seededMemberships.add(rejectedId);
    DataIntegrityViolationException duplicate =
        assertThrows(
            DataIntegrityViolationException.class,
            () ->
                jdbc.update(
                    "INSERT INTO UserGroup (id, includePermissions, roleInGroup, group_id, user_id,"
                        + " adminViewDocsEnabled, piCanEditWork) VALUES (?, 1, 0, ?, ?, 0, 0)",
                    rejectedId,
                    groupId,
                    demoted.getId()));
    assertThat(duplicate.getMessage()).contains(KEY);

    // a single removal now removes the member
    User sysadmin = logoutAndLoginAsSysAdmin();
    sessionFactory.getCache().evictAllRegions();
    doInTransaction(() -> grpMgr.removeUserFromGroup(demoted.getUsername(), groupId, sysadmin));
    assertEquals(
        0,
        count(
            "SELECT COUNT(*) FROM UserGroup WHERE group_id = ? AND user_id = ?",
            groupId,
            demoted.getId()));
    assertEquals(0, liveLinkIds(communal, demoted.getUsername()).size());
  }

  @Test
  void keyCheckHaltsOnAnIncompatibleIndexWithTheKeyName() throws Exception {
    jdbc.execute("CREATE INDEX " + KEY + " ON UserGroup (group_id)");
    assertFalse(compatibleKeyExists());

    assertThrows(MigrationFailedException.class, () -> runChangeSet(CHECK_KEY));
  }

  private long membershipId(Long groupId, User user) {
    return jdbc.queryForObject(
        "SELECT id FROM UserGroup WHERE group_id = ? AND user_id = ?",
        Long.class,
        groupId,
        user.getId());
  }

  private long nextSeedId() {
    // far above the ids Hibernate's table generator may still hand out in this JVM
    return jdbc.queryForObject("SELECT MAX(id) FROM UserGroup", Long.class) + 1_000_000;
  }

  private long copyMembership(long fromId, String overrides) {
    long id = nextSeedId();
    seededMemberships.add(id);
    jdbc.update(
        "INSERT INTO UserGroup (id, includePermissions, roleInGroup, group_id, user_id,"
            + " adminViewDocsEnabled, piCanEditWork, autoshareEnabled, autoShareFolder_id,"
            + " permissionStrings) SELECT ?, includePermissions, roleInGroup, group_id, user_id,"
            + " adminViewDocsEnabled, piCanEditWork, autoshareEnabled, autoShareFolder_id,"
            + " permissionStrings FROM UserGroup WHERE id = ?",
        id,
        fromId);
    jdbc.update("UPDATE UserGroup SET " + overrides + " WHERE id = ?", id);
    return id;
  }

  private long copyLink(long fromId, int deleted) {
    jdbc.update(
        "INSERT INTO RecordToFolder (recordInFolderDeleted, userName, folder_id, record_id,"
            + " deletedDate) SELECT ?, userName, folder_id, record_id, deletedDate"
            + " FROM RecordToFolder WHERE id = ?",
        deleted,
        fromId);
    long id = jdbc.queryForObject("SELECT MAX(id) FROM RecordToFolder", Long.class);
    seededLinks.add(id);
    return id;
  }

  private long liveLinkId(Long childFolderId, String username) {
    List<Long> ids = liveLinkIds(childFolderId, username);
    assertEquals(1, ids.size());
    return ids.get(0);
  }

  private List<Long> liveLinkIds(Long childFolderId, String username) {
    return jdbc.queryForList(
        "SELECT id FROM RecordToFolder WHERE record_id = ? AND userName = ?"
            + LIVE
            + " ORDER BY id",
        Long.class,
        childFolderId,
        username);
  }

  private long rootFolderId(User user) {
    Long id =
        jdbc.queryForObject(
            "SELECT rootFolder_id FROM User WHERE id = ?", Long.class, user.getId());
    assertNotNull(id);
    return id;
  }

  private Map<String, Object> row(long id) {
    return jdbc.queryForMap("SELECT * FROM UserGroup WHERE id = ?", id);
  }

  private Map<Long, Long> backedUpMemberships(Long groupId) {
    Map<Long, Long> backups = new LinkedHashMap<>();
    jdbc.query(
        "SELECT id, kept_id FROM RSDEV1400_RemovedUserGroup WHERE group_id = ?",
        rs -> {
          backups.put(rs.getLong("id"), rs.getLong("kept_id"));
        },
        groupId);
    return backups;
  }

  private Map<Long, Long> backedUpLinks() {
    Map<Long, Long> backups = new LinkedHashMap<>();
    for (Long id : seededLinks) {
      jdbc.query(
          "SELECT id, kept_id FROM RSDEV1400_RemovedRecordToFolder WHERE id = ?",
          rs -> {
            backups.put(rs.getLong("id"), rs.getLong("kept_id"));
          },
          id);
    }
    return backups;
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  private boolean keyExists() {
    return count(
            "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = 'UserGroup' AND INDEX_NAME = ?",
            KEY)
        > 0;
  }

  private boolean compatibleKeyExists() {
    return jdbc.queryForList(
            "SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = 'UserGroup' AND INDEX_NAME = ? AND NON_UNIQUE = 0"
                + " ORDER BY SEQ_IN_INDEX",
            String.class,
            KEY)
        .equals(List.of("group_id", "user_id"));
  }

  private void dropKey() {
    if (keyExists()) {
      jdbc.execute("ALTER TABLE UserGroup DROP INDEX " + KEY);
    }
  }

  private void runChangeSet(int index) throws Exception {
    try (Connection connection = dataSource.getConnection();
        var resources = new ClassLoaderResourceAccessor()) {
      DatabaseChangeLog changelog =
          ChangeLogParserFactory.getInstance()
              .getParser(CHANGELOG, resources)
              .parse(CHANGELOG, new ChangeLogParameters(), resources);
      var database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      changelog.getChangeSets().get(index).execute(changelog, database);
      database.commit();
    }
  }
}

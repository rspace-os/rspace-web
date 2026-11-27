package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.model.permissions.PermissionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import liquibase.database.Database;
import org.hibernate.Session;

/**
 * Removes access-list entries that name no existing user, group or community, left on records by
 * users deleted before RSDEV-1636. A later account with the same username would otherwise gain the
 * access. Each changed row is first copied to {@value #BACKUP_TABLE}. Rows whose list cannot be
 * parsed exactly are left whole; entries whose orphanhood is not provable are kept.
 */
public class RemoveOrphanedAclEntries_RSDEV1636 extends AbstractCustomLiquibaseUpdater {

  static final String BACKUP_TABLE = "RSDEV1636_RemovedAclEntries";
  static final int ACL_COLUMN_LENGTH = 2500;
  static final int PAGE_SIZE = 5000;
  static final int BATCH_SIZE = 500;

  private static final String ROLES =
      Arrays.stream(RoleInGroup.values()).map(Enum::name).collect(Collectors.joining("|"));
  private static final Pattern ROLE_RESTRICTED =
      Pattern.compile("^(.+)\\[(" + ROLES + ")(,(" + ROLES + "))*\\]$");
  private static final ConstraintPermissionResolver PERMISSIONS =
      new ConstraintPermissionResolver();

  int batchSize = BATCH_SIZE;

  private final Map<String, Boolean> principals = new HashMap<>();
  private final Map<String, Boolean> groups = new HashMap<>();
  private int scanned;
  private int changed;
  private int removedEntries;
  private int ambiguous;
  private int prefixMatchedRemoved;

  /** The outcome of cleaning one access list. */
  record Cleaned(boolean ambiguous, String acl, List<String> removedNames) {
    boolean isChanged() {
      return !removedNames.isEmpty();
    }
  }

  @Override
  public String getConfirmationMessage() {
    return String.format(
        "Scanned %d access lists, changed %d, removed %d orphaned entries (%d named with an"
            + " existing group's name as prefix), left %d unparsed lists unchanged",
        scanned, changed, removedEntries, prefixMatchedRemoved, ambiguous);
  }

  @Override
  protected void doExecute(Database database) {
    Session session = sessionFactory.getCurrentSession();
    List<String> groupNames =
        session.createNativeQuery("select uniqueName from rsGroup", String.class).list();
    long lastId = Long.MIN_VALUE;
    int changedInBatch = 0;
    while (true) {
      List<Object[]> rows =
          session
              .createNativeQuery(
                  "select id, acl from BaseRecord where id > :last and acl is not null"
                      + " and acl <> '' order by id limit "
                      + PAGE_SIZE,
                  Object[].class)
              .setParameter("last", lastId)
              .list();
      if (rows.isEmpty()) {
        break;
      }
      for (Object[] row : rows) {
        lastId = ((Number) row[0]).longValue();
        String acl = (String) row[1];
        scanned++;
        Cleaned result = clean(acl, this::principalExists, this::groupExists);
        if (result.ambiguous()) {
          logger.warn("Access list of record {} not parsed, left unchanged", lastId);
          ambiguous++;
          continue;
        }
        if (!result.isChanged()) {
          continue;
        }
        backUpAndUpdate(session, lastId, acl, result);
        changed++;
        removedEntries += result.removedNames().size();
        prefixMatchedRemoved +=
            (int)
                result.removedNames().stream()
                    .filter(name -> groupNames.stream().anyMatch(name::startsWith))
                    .count();
        if (++changedInBatch == batchSize) {
          endBatch();
          session = sessionFactory.getCurrentSession();
          changedInBatch = 0;
        }
      }
    }
    logger.info(getConfirmationMessage());
  }

  void endBatch() {
    commitTransaction();
    openTransaction(getTxMger());
  }

  private void backUpAndUpdate(Session session, long id, String oldAcl, Cleaned result) {
    session
        .createNativeMutationQuery(
            "insert ignore into "
                + BACKUP_TABLE
                + " (record_id, old_acl, new_acl, removed_count) values (:id, :old, :new, :n)")
        .setParameter("id", id)
        .setParameter("old", oldAcl)
        .setParameter("new", result.acl())
        .setParameter("n", result.removedNames().size())
        .executeUpdate();
    session
        .createNativeMutationQuery(
            "update BaseRecord set acl = :new where id = :id and BINARY acl = BINARY :old")
        .setParameter("new", result.acl())
        .setParameter("id", id)
        .setParameter("old", oldAcl)
        .executeUpdate();
  }

  // the database collation decides equality, so collation-equal names are kept
  private boolean principalExists(String name) {
    return principals.computeIfAbsent(
        name,
        n ->
            !sessionFactory
                .getCurrentSession()
                .createNativeQuery(
                    "select 1 from User where username = :n"
                        + " union all select 1 from rsGroup where uniqueName = :n"
                        + " union all select 1 from Community where uniqueName = :n limit 1",
                    Object.class)
                .setParameter("n", n)
                .list()
                .isEmpty());
  }

  private boolean groupExists(String name) {
    return groups.computeIfAbsent(
        name,
        n ->
            !sessionFactory
                .getCurrentSession()
                .createNativeQuery(
                    "select 1 from rsGroup where uniqueName = :n limit 1", Object.class)
                .setParameter("n", n)
                .list()
                .isEmpty());
  }

  /**
   * Removes the entries of an access list whose name provably belongs to no existing principal.
   *
   * @param acl the stored access list
   * @param principalExists whether a user, group or community has exactly this name
   * @param groupExists whether a group has exactly this name
   * @return the cleaned list with the removed names, or an ambiguous result for a list that does
   *     not parse exactly
   */
  static Cleaned clean(
      String acl, Predicate<String> principalExists, Predicate<String> groupExists) {
    if (acl.length() >= ACL_COLUMN_LENGTH) {
      return new Cleaned(true, acl, List.of()); // possibly truncated
    }
    List<String> kept = new ArrayList<>();
    List<String> removed = new ArrayList<>();
    for (String element : acl.split("&", -1)) {
      String[] nameAndPermission = element.split("=", -1);
      if (nameAndPermission.length != 2
          || nameAndPermission[0].isBlank()
          || !isRecordPermission(nameAndPermission[1])) {
        return new Cleaned(true, acl, List.of());
      }
      String name = nameAndPermission[0];
      if (isOrphaned(name, principalExists, groupExists)) {
        removed.add(name);
      } else {
        kept.add(element);
      }
    }
    return new Cleaned(false, String.join("&", kept), removed);
  }

  private static boolean isOrphaned(
      String name, Predicate<String> principalExists, Predicate<String> groupExists) {
    if (RecordGroupSharing.ANONYMOUS_USER.equals(name) || principalExists.test(name)) {
      return false;
    }
    Matcher roleRestricted = ROLE_RESTRICTED.matcher(name);
    if (roleRestricted.matches() && groupExists.test(roleRestricted.group(1))) {
      return false;
    }
    // unknown or malformed role syntax: orphanhood not provable
    return !name.contains("[") && !name.contains("]");
  }

  private static boolean isRecordPermission(String permission) {
    try {
      ConstraintBasedPermission parsed = PERMISSIONS.resolvePermission(permission);
      return parsed.getDomain() == PermissionDomain.RECORD && !parsed.getActions().isEmpty();
    } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
      return false;
    }
  }
}

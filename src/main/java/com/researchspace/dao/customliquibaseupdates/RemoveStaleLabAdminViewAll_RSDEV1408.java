package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.model.PermissionStringConverter;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import liquibase.database.Database;
import org.hibernate.Session;

/**
 * Removes Lab Admin "view all" access that outlived the Lab Admin role (RSDEV-1408). Before the
 * fix, a view-all Lab Admin who became PI, and later User, kept both the {@code
 * adminViewDocsEnabled} flag and the user-level {@code RECORD:READ:property_owner=...} permission.
 *
 * <p>Strictly subtractive: each stored owner-read permission keeps only the usernames the user may
 * still read as a view-all Lab Admin, that is, members of a group where they are still a Lab Admin
 * with view-all (the rule {@code GroupManagerImpl} uses when revoking). A permission with no names
 * left is dropped. Nothing is added or merged. Users whose permissions need no change are not
 * written. Irreversible.
 */
public class RemoveStaleLabAdminViewAll_RSDEV1408 extends AbstractCustomLiquibaseUpdater {

  /**
   * The only shape view-all grants create: READ as the only action and the owner constraint alone.
   * Owner values cannot contain constraint separators, so any other action or constraint (for
   * example {@code &group=...}) is left alone.
   */
  private static final Pattern OWNER_READ = Pattern.compile("RECORD:READ:property_owner=([^:&=]+)");

  private static final String OWNER_READ_PREFIX = "RECORD:READ:property_owner=";

  private final PermissionStringConverter converter = new PermissionStringConverter();
  private int usersChanged;
  private int permissionsTrimmed;
  private int permissionsRemoved;
  private int flagsCleared;

  @Override
  public String getConfirmationMessage() {
    return String.format(
        Locale.ROOT,
        "Stale Lab Admin view-all access: %d users changed (%d permissions trimmed, %d removed),"
            + " %d view-all flags cleared",
        usersChanged,
        permissionsTrimmed,
        permissionsRemoved,
        flagsCleared);
  }

  @Override
  protected void doExecute(Database database) {
    Session session = sessionFactory.getCurrentSession();
    flagsCleared =
        session
            .createNativeMutationQuery(
                "update UserGroup set adminViewDocsEnabled = 0"
                    + " where adminViewDocsEnabled = 1 and roleInGroup <> :labAdmin")
            .setParameter("labAdmin", RoleInGroup.RS_LAB_ADMIN.ordinal())
            .executeUpdate();

    List<Object[]> candidates =
        session
            .createNativeQuery(
                "select id, permissionStrings from User where permissionStrings like :ownerRead",
                Object[].class)
            .setParameter("ownerRead", "%" + OWNER_READ_PREFIX + "%")
            .list();
    for (Object[] row : candidates) {
      Long userId = ((Number) row[0]).longValue();
      Set<String> stored = converter.convertToEntityAttribute((String) row[1]);
      Set<String> legitimate = viewAllReadableUsernames(session, userId);
      Set<String> repaired = new HashSet<>();
      for (String permission : stored) {
        String kept = keepLegitimateOwners(permission, legitimate);
        if (kept != null) {
          repaired.add(kept);
        }
      }
      if (!repaired.equals(stored)) {
        session
            .createNativeMutationQuery("update User set permissionStrings = :perms where id = :id")
            .setParameter("perms", converter.convertToDatabaseColumn(repaired))
            .setParameter("id", userId)
            .executeUpdate();
        usersChanged++;
      }
    }
    if (usersChanged > 0) {
      sessionFactory.getCache().evictEntityData(User.class);
    }
    logger.info(getConfirmationMessage());
  }

  /** Usernames of members of groups where this user is still a view-all Lab Admin. */
  private Set<String> viewAllReadableUsernames(Session session, Long userId) {
    return new HashSet<>(
        session
            .createNativeQuery(
                "select distinct m.username from UserGroup admin"
                    + " join UserGroup mg on mg.group_id = admin.group_id"
                    + " join User m on m.id = mg.user_id"
                    + " where admin.user_id = :id and admin.roleInGroup = :labAdmin"
                    + " and admin.adminViewDocsEnabled = 1 and m.id <> :id",
                String.class)
            .setParameter("id", userId)
            .setParameter("labAdmin", RoleInGroup.RS_LAB_ADMIN.ordinal())
            .list());
  }

  /**
   * @return the permission unchanged if it is not an owner-read grant, the grant with only
   *     legitimate owners, or null if none remain
   */
  private String keepLegitimateOwners(String permission, Set<String> legitimate) {
    Matcher m = OWNER_READ.matcher(permission);
    if (!m.matches() || m.group(1).startsWith("${")) {
      return permission;
    }
    String[] owners = m.group(1).split(",");
    List<String> kept = new ArrayList<>();
    for (String owner : owners) {
      if (legitimate.contains(owner.trim())) {
        kept.add(owner);
      }
    }
    if (kept.size() == owners.length) {
      return permission;
    }
    if (kept.isEmpty()) {
      permissionsRemoved++;
      return null;
    }
    permissionsTrimmed++;
    return OWNER_READ_PREFIX + String.join(",", kept);
  }
}

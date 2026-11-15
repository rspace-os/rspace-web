package com.researchspace.dao.customliquibaseupdates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.Group;
import com.researchspace.model.PermissionStringConverter;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RemoveStaleLabAdminViewAll_RSDEV1408Test extends SpringTransactionalTest {

  private static final String OWNER_READ = "RECORD:READ:property_owner=";
  private final PermissionStringConverter converter = new PermissionStringConverter();

  @Test
  void removesOnlyStaleOwnerReadAccess() throws Exception {
    User piA = createAndSaveAPi();
    User piB = createAndSaveAPi();
    User piC = createAndSaveAPi();
    User memberA = createAndSaveRandomUser();
    User memberB = createAndSaveRandomUser();
    User departed = createAndSaveRandomUser();
    User legitAdmin = createAndSaveRandomUser();
    User mixed = createAndSaveRandomUser();
    User stuck = createAndSaveRandomUser();
    User leaver = createAndSaveRandomUser();
    User onlyNonPi = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(
        piA, piB, piC, memberA, memberB, legitAdmin, mixed, stuck, onlyNonPi);

    // real view-all grants: legitAdmin and mixed in groupA, onlyNonPi alone with its PI in groupC
    Group groupA = labGroup(piA, memberA);
    addLabAdmins(groupA, legitAdmin, mixed);
    logoutAndLoginAs(piA);
    grpMgr.authorizeLabAdminToViewAll(legitAdmin.getId(), piA, groupA.getId(), true);
    grpMgr.authorizeLabAdminToViewAll(mixed.getId(), piA, groupA.getId(), true);
    Group groupC = labGroup(piC);
    addLabAdmins(groupC, onlyNonPi);
    logoutAndLoginAs(piC);
    grpMgr.authorizeLabAdminToViewAll(onlyNonPi.getId(), piC, groupC.getId(), true);

    // stuck state left by the old code: ordinary members of groupB with the flag and owner read
    Group groupB = labGroup(piB, memberB, stuck, mixed);
    sessionFactory.getCurrentSession().flush();
    setViewAllFlag(stuck, groupB);
    setViewAllFlag(mixed, groupB);
    String departedName = departed.getUsername();
    addRawPermission(stuck, OWNER_READ + memberB.getUsername() + "," + departedName);
    addRawPermission(
        mixed,
        OWNER_READ + memberA.getUsername() + "," + memberB.getUsername() + "," + departedName);
    // an extra constraint makes it a different permission: never trimmed into a wider grant
    addRawPermission(
        mixed,
        OWNER_READ
            + memberA.getUsername()
            + ","
            + departedName
            + "&group="
            + groupB.getUniqueName());
    addRawPermission(leaver, OWNER_READ + memberA.getUsername());
    Set<String> unrelated =
        Set.of(
            "RECORD:READ,WRITE:property_owner=" + memberA.getUsername(),
            OWNER_READ + departedName + "&group=" + groupB.getUniqueName(),
            "FORM:READ:property_owner=" + memberA.getUsername(),
            "RECORD:READ:property_owner=${self}",
            "RECORD:READ:id=1");
    unrelated.forEach(p -> addRawPermission(leaver, p));
    Set<String> onlyNonPiGrant = storedPermissions(onlyNonPi);
    addRawPermission(onlyNonPi, OWNER_READ + memberB.getUsername());
    String legitAdminBefore = rawPermissions(legitAdmin);
    Set<String> mixedGrant = ownerReads(mixed);
    mixedGrant.remove(
        OWNER_READ + memberA.getUsername() + "," + memberB.getUsername() + "," + departedName);

    runChange();

    assertEquals(legitAdminBefore, rawPermissions(legitAdmin));
    assertTrue(viewAllFlag(legitAdmin, groupA));
    assertTrue(viewAllFlag(mixed, groupA));
    assertThat(ownerReads(stuck)).isEmpty();
    assertFalse(viewAllFlag(stuck, groupB));
    assertFalse(viewAllFlag(mixed, groupB));
    Set<String> mixedExpected = new HashSet<>(mixedGrant);
    mixedExpected.add(OWNER_READ + memberA.getUsername());
    assertEquals(mixedExpected, ownerReads(mixed));
    assertEquals(unrelated, storedPermissions(leaver));
    // the grant for a Lab Admin with no other non-PI member is the empty owner string; left alone
    assertThat(onlyNonPiGrant).contains(OWNER_READ);
    assertEquals(onlyNonPiGrant, storedPermissions(onlyNonPi));

    Set<String> afterFirstRun = storedPermissions(mixed);
    runChange();
    assertEquals(afterFirstRun, storedPermissions(mixed));
    assertEquals(legitAdminBefore, rawPermissions(legitAdmin));
  }

  private Group labGroup(User pi, User... members) throws Exception {
    Group group = createGroup(getRandomAlphabeticString("grp"), pi);
    return addUsersToGroup(pi, group, members);
  }

  private void addLabAdmins(Group group, User... admins) {
    for (User admin : admins) {
      grpMgr.addUserToGroup(admin.getUsername(), group.getId(), RoleInGroup.RS_LAB_ADMIN);
    }
  }

  private void runChange() {
    sessionFactory.getCurrentSession().flush();
    RemoveStaleLabAdminViewAll_RSDEV1408 change = new RemoveStaleLabAdminViewAll_RSDEV1408();
    change.context = applicationContext;
    change.sessionFactory = sessionFactory;
    change.doExecute(null);
  }

  private void setViewAllFlag(User user, Group group) {
    sessionFactory
        .getCurrentSession()
        .createNativeMutationQuery(
            "update UserGroup set adminViewDocsEnabled = 1 where user_id = :u and group_id = :g")
        .setParameter("u", user.getId())
        .setParameter("g", group.getId())
        .executeUpdate();
  }

  private boolean viewAllFlag(User user, Group group) {
    Object flag =
        sessionFactory
            .getCurrentSession()
            .createNativeQuery(
                "select adminViewDocsEnabled + 0 from UserGroup"
                    + " where user_id = :u and group_id = :g",
                Object.class)
            .setParameter("u", user.getId())
            .setParameter("g", group.getId())
            .uniqueResult();
    return ((Number) flag).intValue() == 1;
  }

  private void addRawPermission(User user, String permission) {
    Set<String> perms = storedPermissions(user);
    perms.add(permission);
    sessionFactory
        .getCurrentSession()
        .createNativeMutationQuery("update User set permissionStrings = :p where id = :id")
        .setParameter("p", converter.convertToDatabaseColumn(perms))
        .setParameter("id", user.getId())
        .executeUpdate();
  }

  private Set<String> ownerReads(User user) {
    Set<String> perms = storedPermissions(user);
    perms.removeIf(p -> !p.startsWith(OWNER_READ));
    return perms;
  }

  private Set<String> storedPermissions(User user) {
    return converter.convertToEntityAttribute(rawPermissions(user));
  }

  private String rawPermissions(User user) {
    return sessionFactory
        .getCurrentSession()
        .createNativeQuery("select permissionStrings from User where id = :id", String.class)
        .setParameter("id", user.getId())
        .uniqueResult();
  }
}

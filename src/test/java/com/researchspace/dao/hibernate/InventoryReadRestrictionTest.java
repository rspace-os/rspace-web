package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Guards the Blaze-Persistence form of the inventory read restriction against the HQL fragment the
 * legacy searches use. A missing disjunct here would show every caller records they may not read.
 */
class InventoryReadRestrictionTest {

  private final InventoryPermissionUtils permissions = mock(InventoryPermissionUtils.class);
  private final InventoryDaoHibernate<InventoryRecord, Long> dao = newDao();
  private final User user = mock(User.class);

  private InventoryDaoHibernate<InventoryRecord, Long> newDao() {
    InventoryDaoHibernate<InventoryRecord, Long> created =
        new InventoryDaoHibernate<>(InventoryRecord.class);
    created.invPermissionUtils = permissions;
    return created;
  }

  private static Group group(String uniqueName) {
    Group group = mock(Group.class);
    when(group.getUniqueName()).thenReturn(uniqueName);
    return group;
  }

  private void caller(String username, List<String> visibleOwners, List<String> groupMembers) {
    when(user.getUsername()).thenReturn(username);
    when(user.getGroups()).thenReturn(Set.of());
    when(permissions.getOwnersVisibleWithUserRole(user)).thenReturn(visibleOwners);
    when(permissions.getUsernameOfUserAndAllMembersOfTheirGroups(user)).thenReturn(groupMembers);
  }

  @Test
  void doesNotRestrictASystemAdministrator() {
    when(user.hasSysadminRole()).thenReturn(true);

    assertNull(dao.inventoryReadRestriction("item", user));
  }

  @Test
  void restrictsACallerWithoutGroupsToTheirOwnRecords() {
    caller("owner", List.of(), List.of());

    Predicate restriction = dao.inventoryReadRestriction("item", user);

    assertEquals("(item.owner.username = :invPermUser)", restriction.expression());
    assertEquals(Map.of("invPermUser", "owner"), restriction.parameters());
  }

  @Test
  void addsOneDisjunctForEachWayARecordCanBeShared() {
    Set<Group> groups = Set.of(group("lab"));
    caller("owner", List.of("pi"), List.of("owner", "colleague"));
    when(user.getGroups()).thenReturn(groups);

    Predicate restriction = dao.inventoryReadRestriction("item", user);

    assertEquals(
        "(item.owner.username = :invPermUser"
            + " OR item.owner.username IN :invPermVisibleOwners"
            + " OR (item.sharingMode = :invPermSharedWithGroups"
            + " AND item.owner.username IN :invPermGroupMembers)"
            + " OR (item.sharingMode = :invPermWhitelisted"
            + " AND item.sharingACL.acl LIKE :invPermGroupAcl0))",
        restriction.expression());
    assertEquals(
        Map.of(
            "invPermUser",
            "owner",
            "invPermVisibleOwners",
            List.of("pi"),
            "invPermSharedWithGroups",
            InventorySharingMode.OWNER_GROUPS,
            "invPermGroupMembers",
            List.of("owner", "colleague"),
            "invPermWhitelisted",
            InventorySharingMode.WHITELIST,
            "invPermGroupAcl0",
            "%lab%"),
        restriction.parameters());
  }

  @Test
  void checksTheAclOfEveryGroupTheCallerBelongsTo() {
    Set<Group> groups = Set.of(group("lab"), group("facility"));
    caller("owner", List.of(), List.of());
    when(user.getGroups()).thenReturn(groups);

    Predicate restriction = dao.inventoryReadRestriction("item", user);

    assertEquals(2, restriction.parameters().keySet().stream().filter(isAclPattern()).count());
  }

  private static java.util.function.Predicate<String> isAclPattern() {
    return name -> name.startsWith("invPermGroupAcl");
  }
}

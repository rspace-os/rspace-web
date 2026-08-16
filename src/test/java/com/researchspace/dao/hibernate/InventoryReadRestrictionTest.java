package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.service.inventory.InstrumentReadAccess;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Guards the typed form of the inventory read rule against the HQL fragment the legacy searches
 * use. A missing disjunct here would show every caller records they may not read.
 */
class InventoryReadRestrictionTest {

  private final InventoryPermissionUtils permissions = mock(InventoryPermissionUtils.class);
  private final InstrumentReadAccess access = new InstrumentReadAccess(permissions);
  private final User user = mock(User.class);

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
  void deniesAnAnonymousCaller() {
    assertTrue(access.check(new AccessContext(null, Operation.READ, "instruments")).isDenied());
  }

  @Test
  void restrictsASystemAdministratorOnlyToActiveInstruments() {
    when(user.hasSysadminRole()).thenReturn(true);

    assertEquals(comparison("deleted", Operator.EQUAL, false), filter());
  }

  @Test
  void restrictsACallerWithoutGroupsToTheirOwnRecords() {
    caller("owner", List.of(), List.of());

    assertEquals(
        new FilterExpression.And(
            List.of(
                comparison("deleted", Operator.EQUAL, false),
                comparison("ownerUsername", Operator.EQUAL, "owner"))),
        filter());
  }

  @Test
  void addsOneDisjunctForEachWayARecordCanBeShared() {
    Set<Group> groups = Set.of(group("lab"));
    caller("owner", List.of("pi"), List.of("owner", "colleague"));
    when(user.getGroups()).thenReturn(groups);

    assertEquals(
        new FilterExpression.And(
            List.of(
                comparison("deleted", Operator.EQUAL, false),
                new FilterExpression.Or(
                    List.of(
                        comparison("ownerUsername", Operator.EQUAL, "owner"),
                        new FilterExpression.Comparison(
                            "ownerUsername", Operator.IN, List.of("pi"), false),
                        new FilterExpression.And(
                            List.of(
                                comparison(
                                    "sharingMode",
                                    Operator.EQUAL,
                                    InventorySharingMode.OWNER_GROUPS),
                                new FilterExpression.Comparison(
                                    "ownerUsername",
                                    Operator.IN,
                                    List.of("owner", "colleague"),
                                    false))),
                        new FilterExpression.And(
                            List.of(
                                comparison(
                                    "sharingMode", Operator.EQUAL, InventorySharingMode.WHITELIST),
                                comparison("sharingAcl", Operator.CONTAINS, "lab"))))))),
        filter());
  }

  @Test
  void checksTheAclOfEveryGroupTheCallerBelongsTo() {
    Set<Group> groups = Set.of(group("lab"), group("facility"));
    caller("owner", List.of(), List.of());
    when(user.getGroups()).thenReturn(groups);

    FilterExpression filter = filter();

    assertEquals(
        2,
        ((FilterExpression.Or) ((FilterExpression.And) filter).children().get(1))
            .children().stream().filter(InventoryReadRestrictionTest::testsAnAcl).count());
  }

  /**
   * The rule must compile against a real collection, which proves the internal filters it names are
   * declared and resolvable. A description missing {@code InventoryReadFilters.ALL} fails here.
   */
  @Test
  void compilesAgainstACollectionThatDeclaresTheInternalFilters() {
    Set<Group> groups = Set.of(group("lab"));
    caller("owner", List.of("pi"), List.of("owner", "colleague"));
    when(user.getGroups()).thenReturn(groups);
    CollectionQueryExecutor<Instrument> instruments =
        new CollectionQueryExecutor<>(
            Instrument.class, ApiV2InstrumentResource.DESCRIPTION, "item");

    Predicate compiled = instruments.compileConstraint(filter());

    assertTrue(compiled.expression().contains("item.owner.username"));
    assertTrue(compiled.expression().contains("item.sharingMode"));
    assertTrue(compiled.expression().contains("item.sharingACL.acl"));
    assertTrue(
        compiled.parameters().containsValue(InventorySharingMode.WHITELIST),
        "the enum binds as a parameter, because the Blaze parser refuses the HQL literal form");
    assertTrue(compiled.parameters().containsValue("%lab%"));
    assertTrue(compiled.parameters().containsValue(List.of("pi")));
  }

  private static boolean testsAnAcl(FilterExpression child) {
    return child instanceof FilterExpression.And and
        && and.children().stream()
            .anyMatch(
                grandchild ->
                    grandchild instanceof FilterExpression.Comparison comparison
                        && comparison.field().equals("sharingAcl"));
  }

  private FilterExpression filter() {
    return access
        .check(new AccessContext(user, Operation.READ, "instruments"))
        .constraintOrEmpty()
        .orElseThrow();
  }

  private static FilterExpression.Comparison comparison(
      String field, Operator operator, Object value) {
    return new FilterExpression.Comparison(field, operator, List.of(value), false);
  }
}

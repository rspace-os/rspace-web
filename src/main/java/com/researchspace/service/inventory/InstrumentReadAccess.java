package com.researchspace.service.inventory;

import static com.researchspace.inventory.model.InventoryReadFilters.OWNER_USERNAME;
import static com.researchspace.inventory.model.InventoryReadFilters.SHARING_ACL;
import static com.researchspace.inventory.model.InventoryReadFilters.SHARING_MODE;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Authoritative REST v2 read policy for the instrument collection. */
@Component
public final class InstrumentReadAccess implements AccessFunction {

  private static final String MEMO_KEY = InstrumentReadAccess.class.getName() + ".constraint";
  private static final AccessDocumentation DOCUMENTATION =
      new AccessDocumentation(
          "A logged-in session is required. Only active instruments visible under the inventory "
              + "sharing policy are returned.",
          Set.of(AccessPolicy.AUTHENTICATION_REQUIRED));
  private static final FilterExpression NOT_DELETED = equals("deleted", false);

  private final InventoryPermissionUtils permissions;

  public InstrumentReadAccess(InventoryPermissionUtils permissions) {
    this.permissions = permissions;
  }

  @Override
  public AccessResult check(AccessContext context) {
    if (!context.isAuthenticated()) {
      return AccessResult.denied(AccessPolicy.AUTHENTICATION_REQUIRED);
    }
    FilterExpression constraint =
        context.computeOnce(MEMO_KEY, FilterExpression.class, () -> constraint(context.user()));
    return AccessResult.allowedWhere(constraint);
  }

  @Override
  public Optional<AccessDocumentation> documentation() {
    return Optional.of(DOCUMENTATION);
  }

  private FilterExpression constraint(User user) {
    if (user.hasSysadminRole()) {
      return NOT_DELETED;
    }
    List<String> groupMembers = permissions.getUsernameOfUserAndAllMembersOfTheirGroups(user);
    List<String> groupNames = user.getGroups().stream().map(Group::getUniqueName).toList();
    List<String> visibleOwners = permissions.getOwnersVisibleWithUserRole(user);

    List<FilterExpression> disjuncts = new ArrayList<>();
    disjuncts.add(equals(OWNER_USERNAME, user.getUsername()));
    if (!visibleOwners.isEmpty()) {
      disjuncts.add(in(OWNER_USERNAME, visibleOwners));
    }
    if (!groupMembers.isEmpty()) {
      disjuncts.add(
          new FilterExpression.And(
              List.of(
                  equals(SHARING_MODE, InventorySharingMode.OWNER_GROUPS),
                  in(OWNER_USERNAME, groupMembers))));
    }
    for (String groupName : groupNames) {
      disjuncts.add(
          new FilterExpression.And(
              List.of(
                  equals(SHARING_MODE, InventorySharingMode.WHITELIST),
                  new FilterExpression.Comparison(
                      SHARING_ACL, Operator.CONTAINS, List.of(groupName), false))));
    }
    FilterExpression inventory =
        disjuncts.size() == 1 ? disjuncts.get(0) : new FilterExpression.Or(disjuncts);
    return new FilterExpression.And(List.of(NOT_DELETED, inventory));
  }

  private static FilterExpression.Comparison equals(String selector, Object value) {
    return new FilterExpression.Comparison(selector, Operator.EQUAL, List.of(value), false);
  }

  private static FilterExpression.Comparison in(String selector, List<String> values) {
    return new FilterExpression.Comparison(selector, Operator.IN, List.copyOf(values), false);
  }
}

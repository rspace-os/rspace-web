package com.researchspace.service.inventory;

import com.researchspace.inventory.model.InventorySharingReadFilters;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
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
    if (!context.isAuthenticated()
        || !context.user().isEnabled()
        || context.user().isAccountLocked()) {
      return AccessResult.denied(AccessPolicy.AUTHENTICATION_REQUIRED);
    }
    FilterExpression constraint =
        context.computeOnce(
            MEMO_KEY, FilterExpression.class, () -> constraint(context.user(), false));
    return AccessResult.allowedWhere(constraint);
  }

  @Override
  public Optional<AccessDocumentation> documentation() {
    return Optional.of(DOCUMENTATION);
  }

  /** Current item read or edit restriction, using the same Inventory sharing facts. */
  public FilterExpression constraint(User user, boolean requireEdit) {
    if (user.hasSysadminRole()) {
      return NOT_DELETED;
    }
    List<String> groupMembers = permissions.getUsernameOfUserAndAllMembersOfTheirGroups(user);
    List<String> groupNames = user.getGroups().stream().map(Group::getUniqueName).toList();
    List<String> visibleOwners =
        requireEdit ? List.of() : permissions.getOwnersVisibleWithUserRole(user);

    FilterExpression inventory =
        InventorySharingReadFilters.readable(
            user.getUsername(), groupMembers, groupNames, visibleOwners);
    return new FilterExpression.And(List.of(NOT_DELETED, inventory));
  }

  private static FilterExpression.Comparison equals(String selector, Object value) {
    return new FilterExpression.Comparison(selector, Operator.EQUAL, List.of(value), false);
  }
}

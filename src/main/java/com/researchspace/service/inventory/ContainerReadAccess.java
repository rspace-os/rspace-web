package com.researchspace.service.inventory;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.InventoryContainerReadConstraint;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Authoritative REST v2 read policy for the container collection. */
@Component
public final class ContainerReadAccess implements AccessFunction {

  private static final String MEMO_KEY = ContainerReadAccess.class.getName() + ".constraint";
  private static final AccessDocumentation DOCUMENTATION =
      new AccessDocumentation(
          "A logged-in session is required. Only active containers visible directly or through an"
              + " immediate child under the inventory sharing policy are returned.",
          Set.of(AccessPolicy.AUTHENTICATION_REQUIRED));

  private final InventoryPermissionUtils permissions;

  public ContainerReadAccess(InventoryPermissionUtils permissions) {
    this.permissions = permissions;
  }

  @Override
  public AccessResult check(AccessContext context) {
    if (!context.isAuthenticated()) {
      return AccessResult.denied(AccessPolicy.AUTHENTICATION_REQUIRED);
    }
    InventoryContainerReadConstraint constraint =
        context.computeOnce(
            MEMO_KEY, InventoryContainerReadConstraint.class, () -> constraint(context.user()));
    return AccessResult.allowedWhere(constraint);
  }

  @Override
  public Optional<AccessDocumentation> documentation() {
    return Optional.of(DOCUMENTATION);
  }

  private InventoryContainerReadConstraint constraint(User user) {
    return new InventoryContainerReadConstraint(
        user.getUsername(),
        user.hasSysadminRole()
            ? List.of()
            : permissions.getUsernameOfUserAndAllMembersOfTheirGroups(user),
        user.getGroups().stream().map(Group::getUniqueName).toList(),
        user.hasSysadminRole() ? List.of() : permissions.getOwnersVisibleWithUserRole(user),
        user.hasSysadminRole());
  }
}

package com.researchspace.inventory.model;

import static com.researchspace.inventory.model.InventoryReadFilters.OWNER_USERNAME;
import static com.researchspace.inventory.model.InventoryReadFilters.SHARING_ACL;
import static com.researchspace.inventory.model.InventoryReadFilters.SHARING_MODE;

import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import java.util.ArrayList;
import java.util.List;

/** Shared ownership and sharing branches for Inventory collection read policies. */
public final class InventorySharingReadFilters {
  private InventorySharingReadFilters() {}

  public static FilterExpression readable(
      String username,
      List<String> groupMembers,
      List<String> groupNames,
      List<String> visibleOwners) {
    List<FilterExpression> disjuncts = new ArrayList<>();
    disjuncts.add(equals(OWNER_USERNAME, username));
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
                  new FilterExpression.Or(
                      List.of(
                          new FilterExpression.Comparison(
                              SHARING_ACL, Operator.EQUAL, List.of(groupName + "=*"), true),
                          new FilterExpression.Comparison(
                              SHARING_ACL,
                              Operator.CONTAINS,
                              List.of("&" + groupName + "="),
                              false))))));
    }
    return disjuncts.size() == 1 ? disjuncts.get(0) : new FilterExpression.Or(disjuncts);
  }

  private static FilterExpression.Comparison equals(String selector, Object value) {
    return new FilterExpression.Comparison(selector, Operator.EQUAL, List.of(value), false);
  }

  private static FilterExpression.Comparison in(String selector, List<String> values) {
    return new FilterExpression.Comparison(selector, Operator.IN, List.copyOf(values), false);
  }
}

package com.researchspace.model.collection;

import java.util.List;

/** Trusted Inventory container visibility facts, including immediate-child visibility. */
public record InventoryContainerReadConstraint(
    String username,
    List<String> groupMembers,
    List<String> groupNames,
    List<String> visibleOwners,
    boolean sysadmin)
    implements QueryConstraint {
  public InventoryContainerReadConstraint {
    groupMembers = List.copyOf(groupMembers);
    groupNames = List.copyOf(groupNames);
    visibleOwners = List.copyOf(visibleOwners);
  }
}

package com.researchspace.model.collection;

import java.util.Map;
import java.util.Objects;

/** Immutable field selections for the root document and any expanded resource types. */
public record ResourceFieldSelections(FieldSelection root, Map<String, FieldSelection> byResource) {

  public ResourceFieldSelections {
    Objects.requireNonNull(root, "Root field selection");
    byResource = Map.copyOf(byResource);
  }

  public static ResourceFieldSelections root(FieldSelection selection) {
    return new ResourceFieldSelections(selection, Map.of());
  }

  public static ResourceFieldSelections forRoot(
      String resourceName, Map<String, FieldSelection> selections) {
    return new ResourceFieldSelections(
        selections.getOrDefault(resourceName, FieldSelection.all()), selections);
  }

  public FieldSelection forResource(String resourceName) {
    return byResource.getOrDefault(resourceName, FieldSelection.all());
  }

  public ResourceFieldSelections withRoot(FieldSelection selection) {
    return root.equals(selection) ? this : new ResourceFieldSelections(selection, byResource);
  }
}

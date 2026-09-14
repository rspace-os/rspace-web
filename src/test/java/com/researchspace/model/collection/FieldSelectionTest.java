package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class FieldSelectionTest {

  @Test
  void intersectsInclusiveAndExclusiveSelectionsWithoutDroppingPermittedFields() {
    assertEquals(
        FieldSelection.include(Set.of("id", "name")),
        FieldSelection.include(Set.of("id", "name", "secret"))
            .intersect(FieldSelection.include(Set.of("id", "name")), "id"));
    assertEquals(
        FieldSelection.include(Set.of("id", "name")),
        FieldSelection.include(Set.of("id", "name", "secret"))
            .intersect(FieldSelection.exclude(Set.of("secret")), "id"));
    assertEquals(
        FieldSelection.include(Set.of("id", "name")),
        FieldSelection.exclude(Set.of("secret"))
            .intersect(FieldSelection.include(Set.of("id", "name", "secret")), "id"));
    assertEquals(
        FieldSelection.exclude(Set.of("secret", "internal")),
        FieldSelection.exclude(Set.of("secret"))
            .intersect(FieldSelection.exclude(Set.of("internal")), "id"));
  }
}

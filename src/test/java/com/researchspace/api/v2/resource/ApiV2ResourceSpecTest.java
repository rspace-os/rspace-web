package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.Sort;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiV2ResourceSpecTest {
  @Test
  @SuppressWarnings("unchecked")
  void defaultsAndMutationKeysFollowExposedOperations() {
    var description =
        new CollectionDescription<>(
            "widgets",
            Widget.class,
            List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Widget::id)),
            "id",
            List.of(new Sort("id", true)));
    ResourceOperations<Widget, Long> operations = mock(ResourceOperations.class);
    var readOnly = ApiV2ResourceSpec.readOnly(description, operations, Long::valueOf).build();
    assertEquals(
        Set.of(ResourceOperation.LIST, ResourceOperation.COUNT, ResourceOperation.READ),
        readOnly.exposedOperations());
    assertEquals(CollectionMutationLimits.DEFAULT, readOnly.mutationLimits());
    assertTrue(readOnly.runtimeFields().isEmpty());
    assertTrue(readOnly.resourceAccess().isEmpty());
    for (var operation :
        List.of(
            ResourceOperation.CREATE,
            ResourceOperation.BULK_CREATE,
            ResourceOperation.UPDATE,
            ResourceOperation.BULK_UPDATE)) {
      var builder =
          ApiV2ResourceSpec.builder(description, operations, Long::valueOf)
              .expose(Set.of(operation));
      assertThrows(IllegalArgumentException.class, builder::build);
      builder.mutationErrors("create", "update").build();
    }
    var all =
        ApiV2ResourceSpec.builder(description, operations, Long::valueOf)
            .mutationErrors("create", "update")
            .build();
    assertEquals(Set.of(ResourceOperation.values()), all.exposedOperations());
  }

  private record Widget(Long id) {}
}

package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiV2ResourceErrorMappingTest {

  record Widget(Long id) {}

  private static final Field<Widget, Long> ID =
      Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Widget::id);
  private static final CollectionDescription<Widget> WIDGETS =
      new CollectionDescription<>(
          "widgets",
          Widget.class,
          List.of(ID),
          List.of(),
          "id",
          List.of(new Sort("id", true)),
          AccessPolicy.readOnly(AccessFunction.anyone()));

  private ResourceOperations<Widget, Long> operations;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    operations = mock(ResourceOperations.class);
  }

  @Test
  void usesTheClosestOperationMappingAndDocumentsIt() {
    ChildFailure failure = new ChildFailure("specific");
    when(operations.find(any(), nullable(User.class))).thenThrow(failure);
    ApiV2ResourceRegistration<Widget, Long> registration =
        register(
            Map.of(
                ResourceOperation.LIST,
                List.of(
                    ApiV2ErrorMapping.of(
                        ParentFailure.class,
                        HttpStatus.CONFLICT,
                        "errors.parent",
                        "A parent failure."),
                    ApiV2ErrorMapping.of(
                        ChildFailure.class,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "errors.child",
                        "A child failure.",
                        exception -> new Object[] {exception.value}))));

    ApiV2ResourceException translated =
        assertThrows(
            ApiV2ResourceException.class,
            () -> registration.list(ResourceRequest.unpaged(null), null));

    assertSame(failure, translated.getCause());
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, translated.status());
    assertEquals("errors.child", translated.errorCode());
    assertArrayEquals(new Object[] {"specific"}, translated.arguments());
    assertEquals(
        "errors.child",
        registration
            .operationDocumentation(ResourceOperation.LIST)
            .responses()
            .get(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .errorCode());
  }

  @Test
  void leavesAnExceptionUnchangedForAnUnconfiguredOperation() {
    ChildFailure failure = new ChildFailure("unmapped");
    when(operations.count(any(), nullable(User.class))).thenThrow(failure);
    ApiV2ResourceRegistration<Widget, Long> registration =
        register(
            Map.of(
                ResourceOperation.LIST,
                List.of(
                    ApiV2ErrorMapping.of(
                        ChildFailure.class,
                        HttpStatus.CONFLICT,
                        "errors.child",
                        "A child failure."))));

    ChildFailure unchanged =
        assertThrows(
            ChildFailure.class, () -> registration.count(ResourceRequest.unpaged(null), null));

    assertSame(failure, unchanged);
  }

  private ApiV2ResourceRegistration<Widget, Long> register(
      Map<ResourceOperation, List<ApiV2ErrorMapping>> mappings) {
    ApiV2ResourceSpec<Widget, Long> spec =
        new ApiV2ResourceSpec<>(WIDGETS, operations, Long::valueOf, "create", "update", mappings);
    return spec.bind(
        new ResourceRegistry(List.of(WIDGETS)), ApiV2RelationshipResolver.unavailable());
  }

  private static class ParentFailure extends RuntimeException {}

  private static final class ChildFailure extends ParentFailure {
    private final String value;

    private ChildFailure(String value) {
      this.value = value;
    }
  }
}

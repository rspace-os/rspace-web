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
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
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
          "id",
          List.of(new Sort("id", true)),
          AccessPolicy.readOnly(AccessFunction.anyone()));

  private ResourceOperations<Widget, Long> operations;

  @BeforeEach
  void setUp() {
    operations = operationsMock();
  }

  @SuppressWarnings("unchecked") // Mockito creates an erased interface mock; ID use is test-owned.
  private static ResourceOperations<Widget, Long> operationsMock() {
    return (ResourceOperations<Widget, Long>) mock(ResourceOperations.class);
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
                    ApiV2ErrorMapping.forException(ParentFailure.class)
                        .status(HttpStatus.CONFLICT)
                        .documentedError("errors.parent", "A parent failure.")
                        .build(),
                    ApiV2ErrorMapping.forException(ChildFailure.class)
                        .status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .documentedError("errors.child", "A child failure.")
                        .arguments(exception -> new Object[] {exception.value})
                        .build())));

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
                    ApiV2ErrorMapping.forException(ChildFailure.class)
                        .status(HttpStatus.CONFLICT)
                        .documentedError("errors.child", "A child failure.")
                        .build())));

    ChildFailure unchanged =
        assertThrows(
            ChildFailure.class, () -> registration.count(ResourceRequest.unpaged(null), null));

    assertSame(failure, unchanged);
  }

  @Test
  void builtMappingsRetainTheirExtractorsAndBadRequestsCloneArguments() {
    var builder =
        ApiV2ErrorMapping.forException(ChildFailure.class)
            .status(HttpStatus.BAD_REQUEST)
            .documentedError("errors.child", "Child failure")
            .arguments(ex -> new Object[] {ex.value});
    var mapping = builder.build();
    builder.documentedError("errors.other", "Other failure").arguments(ex -> new Object[0]);
    var translated = mapping.translate(new ChildFailure("value"));
    assertEquals("errors.child", translated.errorCode());
    assertArrayEquals(new Object[] {"value"}, translated.arguments());
    Object[] arguments = {"original"};
    var badRequest = ApiV2ResourceException.badRequest("errors.child", arguments);
    arguments[0] = "changed";
    badRequest.arguments()[0] = "changed again";
    assertArrayEquals(new Object[] {"original"}, badRequest.arguments());
  }

  private ApiV2ResourceRegistration<Widget, Long> register(
      Map<ResourceOperation, List<ApiV2ErrorMapping>> mappings) {
    var builder =
        ApiV2ResourceSpec.builder(WIDGETS, operations, Long::valueOf)
            .mutationErrors("create", "update");
    mappings.forEach(builder::errorMappings);
    var spec = builder.build();
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

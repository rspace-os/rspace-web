package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import com.researchspace.service.ResourceMutationManager;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

class ResourceMutationScopeTest {
  static Stream<Arguments> cases() {
    return Stream.of(versionedTarget(), unversionedTarget())
        .flatMap(
            target ->
                Stream.of(
                    Arguments.of(target, true, true),
                    Arguments.of(target, true, false),
                    Arguments.of(target, false, true),
                    Arguments.of(target, false, false)));
  }

  @ParameterizedTest(name = "{0}, delete={1}, visible={2}")
  @MethodSource("cases")
  void checksAuthorizedScopeInsideMutationBoundary(
      Target<?, ?> target, boolean delete, boolean visible) {
    check(target, delete, visible);
  }

  /** A collection under test, with the id and writable field the mutation targets. */
  private record Target<T, ID>(
      CollectionDescription<T> source,
      Function<String, ID> parser,
      ID id,
      String field,
      MutationPolicy policy) {}

  private static Named<Target<?, ?>> versionedTarget() {
    return Named.of(
        "versioned collection",
        new Target<>(
            scopeDescription(),
            Long::valueOf,
            7L,
            "enabled",
            new MutationPolicy(
                VersionPolicy.required(
                    "configurationVersion",
                    "errors.api.v2.bookingConfiguration.ifMatchRequired",
                    ResourceOperation.UPDATE,
                    ResourceOperation.DELETE),
                DeletePolicy.SOFT.withPermanentOption(),
                IdempotencyPolicy.NONE)));
  }

  private static Named<Target<?, ?>> unversionedTarget() {
    return Named.of(
        "unversioned collection",
        new Target<>(scopeDescription(), Long::valueOf, 7L, "enabled", MutationPolicy.DEFAULT));
  }

  private static CollectionDescription<ScopeFixture> scopeDescription() {
    return new CollectionDescription<>(
        "scopes",
        ScopeFixture.class,
        List.of(
            Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), ScopeFixture::id),
            Field.readOnly(
                "configurationVersion",
                "configurationVersion",
                CollectionFieldTypes.longNumber(),
                ScopeFixture::version),
            Field.writable(
                "enabled",
                "enabled",
                CollectionFieldTypes.bool(),
                ScopeFixture::enabled,
                (resource, value) -> {})),
        "id",
        List.of(new Sort("id", true)));
  }

  private record ScopeFixture(Long id, Long version, Boolean enabled) {}

  private <T, ID> void check(Target<T, ID> target, boolean delete, boolean visible) {
    CollectionDescription<T> source = target.source();
    ID id = target.id();
    MutationPolicy policy = target.policy();
    var constraint =
        new FilterExpression.Comparison(source.idField(), Operator.NOT_EQUAL, List.of(id), false);
    AccessFunction scoped =
        AccessFunction.documented(
            "TestMutationScope",
            "Scope",
            Set.of(),
            ignored -> AccessResult.allowedWhere(constraint));
    var description =
        new CollectionDescription<>(
            source.resourceName(),
            source.entityType(),
            source.fields(),
            source.idField(),
            source.defaultSort(),
            new AccessPolicy(scoped, scoped, scoped, scoped, scoped));
    @SuppressWarnings("unchecked")
    ResourceOperations<T, ID> operations = mock(ResourceOperations.class);
    var boundary = new Boundary();
    User subject = new User("scope-reader");
    var caller = ApiV2Caller.direct(subject);
    when(operations.find(any(), same(subject)))
        .thenAnswer(
            invocation -> {
              assertTrue(boundary.executing);
              return new ResourcePage<>(
                  visible ? List.of(mock(source.entityType())) : List.of(), visible ? 1 : 0);
            });
    if (visible) {
      if (delete) {
        when(operations.delete(eq(id), any(), same(caller)))
            .thenAnswer(
                invocation -> {
                  assertTrue(boundary.executing);
                  return Optional.empty();
                });
      } else {
        when(operations.update(eq(id), any(), any(), same(caller)))
            .thenAnswer(
                invocation -> {
                  assertTrue(boundary.executing);
                  return Optional.empty();
                });
      }
    }
    var spec =
        ApiV2ResourceSpec.builder(description, operations, target.parser())
            .mutationErrors("create", "update")
            .mutationPolicy(policy)
            .build();
    var registration =
        new ApiV2ResourceRegistration<>(
            spec,
            new ResourceRegistry(List.of(description)),
            ApiV2RelationshipResolver.unavailable(),
            ignored -> List.of(),
            boundary);
    // A visible item reaches the write, whose stubbed empty result is also a 404; the verify calls
    // below tell the two cases apart.
    if (delete) {
      assertThrows(
          NotFoundException.class,
          () -> registration.delete(id.toString(), new ResourceDeleteOptions(0L, false), caller));
      verify(operations, visible ? times(1) : never()).delete(eq(id), any(), same(caller));
    } else {
      assertThrows(
          NotFoundException.class,
          () ->
              registration.update(
                  id.toString(),
                  new ObjectMapper().createObjectNode().put(target.field(), true),
                  policy.version().field().isPresent() ? 0L : null,
                  caller));
      verify(operations, visible ? times(1) : never()).update(eq(id), any(), any(), same(caller));
    }
    var request = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(operations).find(request.capture(), same(subject));
    assertEquals(
        new FilterExpression.Comparison(source.idField(), Operator.EQUAL, List.of(id), false),
        request.getValue().filter());
    assertEquals(constraint, request.getValue().serverConstraint());
    assertTrue(boundary.called);
    assertFalse(boundary.executing);
  }

  private static final class Boundary extends ResourceMutationManager {
    boolean called;
    boolean executing;

    @Override
    public <R> R execute(Supplier<R> action) {
      called = true;
      executing = true;
      try {
        return action.get();
      } finally {
        executing = false;
      }
    }
  }
}

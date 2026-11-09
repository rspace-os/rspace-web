package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiV2RelationshipPageBoundTest {

  @Test
  void resolvesEveryReadableTargetWithBoundedFallbackQueries() {
    User actor = mock(User.class);
    RecordingOperations operations = new RecordingOperations();
    Set<Object> requestedIds = new HashSet<>();
    for (long id = 1; id <= CollectionQueryLimits.MAX_PAGE_SIZE + 3; id++) {
      requestedIds.add(id);
      operations.nodes.put(id, new Node(id));
    }
    long deniedId = CollectionQueryLimits.MAX_PAGE_SIZE + 2L;
    long missingId = CollectionQueryLimits.MAX_PAGE_SIZE + 3L;
    operations.deniedIds.add(deniedId);
    operations.nodes.remove(missingId);
    CollectionDescription<Node> description =
        new CollectionDescription<>(
            "nodes",
            Node.class,
            List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Node::id)),
            "id",
            List.of(new Sort("id", true)));
    var registration =
        new ApiV2ResourceCatalog(
                List.of(
                    new ApiV2ResourceSpec<>(
                        description, operations, Long::valueOf, "error", "error")))
            .find("nodes")
            .orElseThrow();

    var resolved = registration.resolveReadable(requestedIds, actor);

    Set<Object> readableIds = new HashSet<>(requestedIds);
    readableIds.remove(deniedId);
    readableIds.remove(missingId);
    assertEquals(readableIds, resolved.keySet());
    resolved.forEach((id, target) -> assertSame(operations.nodes.get(id), target.entity()));
    assertEquals(
        List.of(CollectionQueryLimits.MAX_PAGE_SIZE, 3),
        operations.findCalls.stream().map(call -> call.request().page().size()).toList());
    List<Object> queriedIds = new ArrayList<>();
    for (FindCall call : operations.findCalls) {
      assertSame(actor, call.actor());
      assertEquals(1, call.request().page().number());
      FilterExpression.Comparison filter =
          assertInstanceOf(FilterExpression.Comparison.class, call.request().filter());
      assertEquals("id", filter.field());
      assertEquals(Operator.IN, filter.operator());
      assertFalse(filter.wildcard());
      assertEquals(call.request().page().size(), filter.values().size());
      queriedIds.addAll(filter.values());
    }
    assertEquals(requestedIds, new HashSet<>(queriedIds));
    assertEquals(requestedIds.size(), queriedIds.size());
    assertEquals(1, operations.overrideCalls);
    assertEquals(readableIds, new HashSet<>(operations.overrideIds));
    assertSame(actor, operations.overrideActor);
  }

  private record Node(Long id) {}

  private record FindCall(ResourceRequest request, User actor) {}

  private static final class RecordingOperations implements ResourceOperations<Node, Long> {
    private final Map<Long, Node> nodes = new HashMap<>();
    private final Set<Long> deniedIds = new HashSet<>();
    private final List<FindCall> findCalls = new ArrayList<>();
    private List<Long> overrideIds;
    private User overrideActor;
    private int overrideCalls;

    @Override
    public ResourcePage<Node> find(ResourceRequest request, User actor) {
      findCalls.add(new FindCall(request, actor));
      FilterExpression.Comparison filter =
          assertInstanceOf(FilterExpression.Comparison.class, request.filter());
      List<Node> selected =
          filter.values().stream()
              .map(Long.class::cast)
              .filter(id -> !deniedIds.contains(id))
              .map(nodes::get)
              .filter(java.util.Objects::nonNull)
              .toList();
      return new ResourcePage<>(selected, selected.size());
    }

    @Override
    public long count(ResourceRequest request, User actor) {
      return nodes.size();
    }

    @Override
    public Optional<Node> findById(Long id, User actor) {
      return Optional.ofNullable(nodes.get(id));
    }

    @Override
    public Map<Object, Map<String, Object>> readOverrides(List<Node> resources, User actor) {
      overrideCalls++;
      overrideIds = resources.stream().map(Node::id).toList();
      overrideActor = actor;
      return Map.of();
    }
  }
}

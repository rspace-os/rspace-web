package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.DocumentValidationException.Reason;
import com.researchspace.model.collection.DocumentValidationException.Violation;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResolvedResourceReference;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.SplitReferenceBinding;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.Test;

class ApiV2RelationshipResolverTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final User actor = mock(User.class);

  @Test
  void resolvesExistingSameCollectionTargetBeforeCreate() throws Exception {
    RecordingNodeOperations operations = new RecordingNodeOperations();
    operations.nodes.put(2L, new Node(2L, null));
    ApiV2ResourceRegistration<?, ?> nodes = registration(description(false), operations);

    nodes.create(referenceBody(2L), ApiV2Caller.direct(actor));

    ResolvedResourceReference<?, ?> resolved =
        assertInstanceOf(
            ResolvedResourceReference.class, operations.lastDocument.values().get("target"));
    assertEquals(new ResourceReference<>("NODE", 2L), resolved.reference());
    assertEquals(operations.nodes.get(2L), resolved.entity());
  }

  @Test
  void returnsTheSameFieldViolationForMissingUnreadableAndSelfTargets() throws Exception {
    RecordingNodeOperations operations = new RecordingNodeOperations();
    operations.nodes.put(2L, new Node(2L, null));
    operations.deniedIds.add(3L);
    ApiV2ResourceRegistration<?, ?> nodes = registration(description(false), operations);

    DocumentValidationException missing =
        assertThrows(
            DocumentValidationException.class,
            () -> nodes.create(referenceBody(99L), ApiV2Caller.direct(actor)));
    DocumentValidationException unreadable =
        assertThrows(
            DocumentValidationException.class,
            () -> nodes.create(referenceBody(3L), ApiV2Caller.direct(actor)));
    DocumentValidationException self =
        assertThrows(
            DocumentValidationException.class,
            () -> nodes.update("2", referenceBody(2L), ApiV2Caller.direct(actor)));

    assertEquals(List.of(new Violation("target", Reason.INVALID_VALUE)), missing.getViolations());
    assertEquals(missing.getViolations(), unreadable.getViolations());
    assertEquals(missing.getViolations(), self.getViolations());
    assertEquals(0, operations.writeCalls);
  }

  @Test
  void rejectsSameCollectionBulkChangeUntilSelfReferencesAreAllowed() throws Exception {
    RecordingNodeOperations operations = new RecordingNodeOperations();
    operations.nodes.put(2L, new Node(2L, null));
    ResourceRequest request =
        new ResourceRequest(
            null,
            List.of(),
            new ResourceRequest.Page(1, 20),
            FieldSelection.all(),
            IncludeTree.empty());

    ApiV2ResourceRegistration<?, ?> restricted = registration(description(false), operations);
    assertThrows(
        DocumentValidationException.class,
        () -> restricted.updateMany(request, referenceBody(2L), ApiV2Caller.direct(actor)));

    ApiV2ResourceRegistration<?, ?> permitted = registration(description(true), operations);
    permitted.updateMany(request, referenceBody(2L), ApiV2Caller.direct(actor));

    assertInstanceOf(
        ResolvedResourceReference.class, operations.lastDocument.values().get("target"));
  }

  @Test
  void resolvesTargetOnlyResourcesWithoutAddingCrudRoutes() throws Exception {
    CollectionDescription<Node> nodes = targetOnlySourceDescription();
    CollectionDescription<Target> targets = targetDescription();
    RecordingNodeOperations operations = new RecordingNodeOperations();
    ApiV2ResourceCatalog catalog =
        new ApiV2ResourceCatalog(
            List.of(new ApiV2ResourceSpec<>(nodes, operations, Long::valueOf, "error", "error")),
            List.of(
                new ApiV2RelationshipTargetSpec<>(
                    targets,
                    Long.class,
                    (ids, caller) ->
                        ids.stream()
                            .collect(
                                java.util.stream.Collectors.toMap(
                                    id -> id, id -> new Target(id, "classified"))))));

    catalog
        .find("nodes")
        .orElseThrow()
        .create(
            mapper.readTree("{\"target\":{\"relationTo\":\"targets\",\"value\":7}}"),
            ApiV2Caller.direct(actor));

    ResolvedResourceReference<?, ?> resolved =
        assertInstanceOf(
            ResolvedResourceReference.class, operations.lastDocument.values().get("target"));
    assertEquals(new Target(7L, "classified"), resolved.entity());
    assertTrue(catalog.find("targets").isEmpty());
    assertTrue(catalog.registry().resources().contains(targets));

    operations.nodes.put(1L, new Node(1L, new ResourceReference<>("TARGET", 7L)));
    ResourceRequest populated =
        new ResourceRequest(
            null,
            List.of(),
            new ResourceRequest.Page(1, 20),
            FieldSelection.all(),
            new IncludeTree(Map.of("target", IncludeTree.empty())));
    Map<?, ?> reference =
        assertInstanceOf(
            Map.class,
            catalog.find("nodes").orElseThrow().list(populated, actor).docs().get(0).get("target"));
    Map<?, ?> targetDocument = assertInstanceOf(Map.class, reference.get("value"));
    assertFalse(targetDocument.containsKey("secret"));
  }

  @Test
  void batchesDistinctPopulationLookupsOncePerDepthForOneListRequest() {
    RecordingNodeOperations operations = new RecordingNodeOperations();
    operations.nodes.put(1L, new Node(1L, new ResourceReference<>("NODE", 101L)));
    operations.nodes.put(2L, new Node(2L, new ResourceReference<>("NODE", 102L)));
    operations.nodes.put(101L, new Node(101L, new ResourceReference<>("NODE", 201L)));
    operations.nodes.put(102L, new Node(102L, new ResourceReference<>("NODE", 202L)));
    operations.nodes.put(201L, new Node(201L, null));
    operations.nodes.put(202L, new Node(202L, null));
    ApiV2ResourceRegistration<?, ?> nodes = registration(description(false), operations);
    ResourceRequest request =
        new ResourceRequest(
            new com.researchspace.model.collection.FilterExpression.Comparison(
                "id", CollectionDescription.Operator.IN, List.of(1L, 2L), false),
            List.of(),
            new ResourceRequest.Page(1, 20),
            FieldSelection.all(),
            new IncludeTree(
                Map.of("target", new IncludeTree(Map.of("target", IncludeTree.empty())))));

    nodes.list(request, actor);

    assertEquals(3, operations.findCalls);
    assertEquals(0, operations.readableLookupCalls);
  }

  @Test
  void rendersNullWithoutRawRelationshipIdsWhenTheTargetIsUnreadable() {
    RecordingNodeOperations operations = new RecordingNodeOperations();
    operations.nodes.put(1L, new Node(1L, new ResourceReference<>("NODE", 2L)));
    operations.nodes.put(2L, new Node(2L, null));
    operations.deniedIds.add(2L);
    ResourceRequest request =
        new ResourceRequest(
            new com.researchspace.model.collection.FilterExpression.Comparison(
                "id", CollectionDescription.Operator.EQUAL, List.of(1L), false),
            List.of(),
            new ResourceRequest.Page(1, 20),
            FieldSelection.all(),
            IncludeTree.empty());

    Map<String, Object> document =
        registration(description(false), operations).list(request, actor).docs().get(0);

    assertTrue(document.containsKey("target"));
    assertNull(document.get("target"));
  }

  private ApiV2ResourceRegistration<?, ?> registration(
      CollectionDescription<Node> description, RecordingNodeOperations operations) {
    ApiV2ResourceCatalog catalog =
        new ApiV2ResourceCatalog(
            List.of(
                new ApiV2ResourceSpec<>(description, operations, Long::valueOf, "error", "error")));
    return catalog.find("nodes").orElseThrow();
  }

  private JsonNode referenceBody(long id) throws Exception {
    return mapper.readTree("{\"target\":{\"relationTo\":\"nodes\",\"value\":" + id + "}}");
  }

  private static CollectionDescription<Node> description(boolean selfReferenceAllowed) {
    CollectionDescription.Relationship<Node> target =
        CollectionDescription.Relationship.polymorphicToOne(
                "target",
                CollectionFieldTypes.longNumber(),
                List.of(new RelationshipTarget<>("nodes", "NODE", Node.class)),
                new SplitReferenceBinding<>(Node::target, "targetType", "targetId"))
            .required();
    if (selfReferenceAllowed) {
      target = target.allowSelfReference();
    }
    return new CollectionDescription<>(
        "nodes",
        Node.class,
        List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Node::id)),
        List.of(target),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.authenticated());
  }

  private static CollectionDescription<Target> targetDescription() {
    return new CollectionDescription<>(
        "targets",
        Target.class,
        List.of(
            Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Target::id),
            Field.readOnly("secret", "secret", CollectionFieldTypes.text(), Target::secret)
                .readableBy(AccessFunction.never())),
        List.of(),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.authenticated());
  }

  private static CollectionDescription<Node> targetOnlySourceDescription() {
    return new CollectionDescription<>(
        "nodes",
        Node.class,
        List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Node::id)),
        List.of(
            CollectionDescription.Relationship.polymorphicToOne(
                    "target",
                    CollectionFieldTypes.longNumber(),
                    List.of(new RelationshipTarget<>("targets", "TARGET", Target.class)),
                    new SplitReferenceBinding<>(Node::target, "targetType", "targetId"))
                .required()),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.authenticated());
  }

  private record Node(Long id, ResourceReference<String, Long> target) {}

  private record Target(Long id, String secret) {}

  private static final class RecordingNodeOperations implements ResourceOperations<Node, Long> {

    private final Map<Long, Node> nodes = new HashMap<>();
    private final Set<Long> deniedIds = new HashSet<>();
    private ParsedDocument lastDocument;
    private int writeCalls;
    private int findCalls;
    private int readableLookupCalls;

    @Override
    public ResourcePage<Node> find(ResourceRequest request, User actor) {
      findCalls++;
      if (request.filter()
          instanceof com.researchspace.model.collection.FilterExpression.Comparison comparison) {
        List<Node> selected =
            comparison.values().stream()
                .map(Long.class::cast)
                .filter(id -> !deniedIds.contains(id))
                .map(nodes::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new ResourcePage<>(selected, selected.size());
      }
      return new ResourcePage<>(List.copyOf(nodes.values()), nodes.size());
    }

    @Override
    public long count(ResourceRequest request, User actor) {
      return nodes.size();
    }

    @Override
    public Optional<Node> findById(Long id, User actor) {
      readableLookupCalls++;
      if (deniedIds.contains(id)) {
        throw new AuthorizationException();
      }
      return Optional.ofNullable(nodes.get(id));
    }

    @Override
    public Node create(ParsedDocument document, ApiV2Caller caller) {
      lastDocument = document;
      writeCalls++;
      ResolvedResourceReference<?, ?> resolved =
          (ResolvedResourceReference<?, ?>) document.values().get("target");
      return new Node(1L, (ResourceReference<String, Long>) resolved.reference());
    }

    @Override
    public Optional<Node> update(Long id, ParsedDocument document, ApiV2Caller caller) {
      lastDocument = document;
      writeCalls++;
      return Optional.of(new Node(id, null));
    }

    @Override
    public List<Node> updateMany(
        ResourceRequest request, ParsedDocument document, ApiV2Caller caller) {
      lastDocument = document;
      writeCalls++;
      return List.of();
    }
  }
}

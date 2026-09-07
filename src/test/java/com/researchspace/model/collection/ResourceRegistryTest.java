package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Relationship;
import com.researchspace.model.collection.CollectionDescription.Sort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResourceRegistryTest {

  private static final CollectionDescription<Child> CHILDREN =
      new CollectionDescription<>(
          "children",
          Child.class,
          List.of(
              Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Child::id),
              Field.readOnly("name", "name", CollectionFieldTypes.text(20), Child::name)),
          List.of(),
          "id",
          List.of(new Sort("id", true)));

  private static final CollectionDescription<Parent> PARENTS =
      new CollectionDescription<>(
          "parents",
          Parent.class,
          List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Parent::id)),
          List.of(
              Relationship.referenceToOne(
                  "primaryChild",
                  "children",
                  CollectionFieldTypes.longNumber(),
                  Child.class,
                  Parent::primaryChild,
                  Child::id,
                  "primaryChild.id")),
          "id",
          List.of(new Sort("id", true)));

  @Test
  void validatesTheRelationshipGraphAndExpandsRegisteredResources() {
    ResourceRegistry registry = new ResourceRegistry(List.of(PARENTS, CHILDREN));
    Child first = new Child(10L, "first");
    Parent parent = new Parent(1L, first);

    Map<String, Object> document =
        new ResourceRenderer(registry)
            .render(parent, PARENTS, FieldSelection.all(), IncludeTree.empty());

    assertEquals(Map.of("relationTo", "children", "value", 10L), document.get("primaryChild"));
    assertTrue(IncludeTree.toDepth(PARENTS, registry, 0).isEmpty());
    assertEquals(PARENTS, registry.requireEntityType(Parent.class));
  }

  @Test
  void appliesReadOverridesToExpandedTargets() {
    ResourceRegistry registry = new ResourceRegistry(List.of(PARENTS, CHILDREN));
    Child child = new Child(10L, "classified");
    Parent parent = new Parent(1L, child);

    Map<String, Object> document =
        new ResourceRenderer(registry)
            .render(
                parent,
                PARENTS,
                FieldSelection.all(),
                IncludeTree.toDepth(PARENTS, registry, 1),
                (resource, id) ->
                    Optional.of(
                        new ResourceRenderer.ResolvedTarget(
                            child,
                            FieldSelection.all(),
                            java.util.Collections.singletonMap("name", null))));

    Map<?, ?> relationship = (Map<?, ?>) document.get("primaryChild");
    Map<?, ?> expanded = (Map<?, ?>) relationship.get("value");
    assertTrue(expanded.containsKey("name"));
    assertEquals(null, expanded.get("name"));
  }

  @Test
  void exposesFieldAndRelationshipSchemaMetadata() {
    CollectionDescription.ResourceSchema schema = PARENTS.schema();

    assertEquals("parents", schema.name());
    assertEquals(Parent.class, schema.entityType());
    assertEquals("integer", schema.fields().get(0).type().jsonType());
    assertEquals("int64", schema.fields().get(0).type().format());
    assertEquals(20, CHILDREN.schema().fields().get(1).type().maxLength());
    assertEquals("A logged-in session is required.", schema.access().readAccess().description());
    assertEquals(
        "A logged-in session is required.",
        schema.relationships().get(0).readAccess().description());
  }

  @Test
  void authorizesQueryFieldsAcrossTheRelationshipGraph() {
    AccessContext context =
        new AccessContext(null, AccessContext.Operation.READ, PARENTS.resourceName());
    ResourceRegistry registry = new ResourceRegistry(List.of(PARENTS, CHILDREN));

    assertTrue(registry.isQueryFieldReadable("id", context));
    assertTrue(registry.isQueryFieldReadable("primaryChild.name", context));
  }

  @Test
  void rejectsAQueryFieldHiddenByTheRelationshipOrTargetCollection() {
    CollectionDescription<Child> hiddenNames =
        new CollectionDescription<>(
            "children",
            Child.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Child::id),
                Field.readOnly("name", "name", CollectionFieldTypes.text(20), Child::name)
                    .readableBy(AccessFunction.never())),
            List.of(),
            "id",
            List.of(new Sort("id", true)));
    CollectionDescription<Parent> hiddenRelationship =
        new CollectionDescription<>(
            "parents",
            Parent.class,
            PARENTS.fields(),
            List.of(PARENTS.requireRelationship("primaryChild").readableBy(AccessFunction.never())),
            "id",
            List.of(new Sort("id", true)));
    AccessContext context =
        new AccessContext(null, AccessContext.Operation.READ, PARENTS.resourceName());

    assertFalse(
        new ResourceRegistry(List.of(PARENTS, hiddenNames))
            .isQueryFieldReadable("primaryChild.name", context));
    assertFalse(
        new ResourceRegistry(List.of(hiddenRelationship, CHILDREN))
            .isQueryFieldReadable("primaryChild.name", context));
  }

  @Test
  void rejectsUnknownTargetsAndDuplicateResourcesAtStartup() {
    assertThrows(IllegalArgumentException.class, () -> new ResourceRegistry(List.of(PARENTS)));
    assertThrows(
        IllegalArgumentException.class, () -> new ResourceRegistry(List.of(CHILDREN, CHILDREN)));
  }

  @Test
  void permitsNamedResourceAliasesForOneEntityTypeButRejectsAmbiguousTypeLookup() {
    CollectionDescription<Child> relationshipOnlyChildren =
        new CollectionDescription<>(
            "relationship-children",
            Child.class,
            List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Child::id)),
            List.of(),
            "id",
            List.of(new Sort("id", true)));

    ResourceRegistry registry = new ResourceRegistry(List.of(CHILDREN, relationshipOnlyChildren));

    assertEquals(CHILDREN, registry.requireResource("children"));
    assertEquals(relationshipOnlyChildren, registry.requireResource("relationship-children"));
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> registry.requireEntityType(Child.class));
    assertTrue(exception.getMessage().contains("use a resource name instead"));
  }

  @Test
  void permitsSameCollectionTargetsAndStopsAtTheRequestedDepth() {
    CollectionDescription<Node> nodes = nodes();
    ResourceRegistry registry = new ResourceRegistry(List.of(nodes));
    Node first = new Node(1L, 2L);
    Node second = new Node(2L, 1L);
    Map<Long, Node> byId = Map.of(1L, first, 2L, second);
    ResourceRenderer renderer = new ResourceRenderer(registry);

    Map<String, Object> shallow =
        renderer.render(
            first, nodes, FieldSelection.all(), IncludeTree.toDepth(nodes, registry, 0));
    assertEquals(
        Map.of("relationTo", "nodes", "value", 2L, "globalId", "IN2"), shallow.get("target"));

    Map<String, Object> populated =
        renderer.render(
            first,
            nodes,
            FieldSelection.all(),
            IncludeTree.toDepth(nodes, registry, 2),
            (resource, id) ->
                Optional.ofNullable(byId.get(id))
                    .map(
                        entity ->
                            new ResourceRenderer.ResolvedTarget(entity, FieldSelection.all())));
    Map<?, ?> firstReference = (Map<?, ?>) populated.get("target");
    Map<?, ?> secondDocument = (Map<?, ?>) firstReference.get("value");
    Map<?, ?> secondReference = (Map<?, ?>) secondDocument.get("target");
    Map<?, ?> repeatedFirst = (Map<?, ?>) secondReference.get("value");
    assertEquals(1L, repeatedFirst.get("id"));
    assertEquals(
        Map.of("relationTo", "nodes", "value", 2L, "globalId", "IN2"), repeatedFirst.get("target"));

    Map<String, Object> narrowedTarget =
        renderer.render(
            first,
            nodes,
            FieldSelection.all(),
            IncludeTree.toDepth(nodes, registry, 1),
            (resource, id) ->
                Optional.ofNullable(byId.get(id))
                    .map(
                        entity ->
                            new ResourceRenderer.ResolvedTarget(
                                entity, FieldSelection.include(Set.of("id")))));
    assertEquals(
        Map.of("relationTo", "nodes", "value", Map.of("id", 2L), "globalId", "IN2"),
        narrowedTarget.get("target"));
  }

  @Test
  void rendererUsesBatchedResultsWithoutFallingBackToSingleTargetResolution() {
    CollectionDescription<Node> nodes = nodes();
    ResourceRegistry registry = new ResourceRegistry(List.of(nodes));
    Node first = new Node(1L, 2L);
    Node second = new Node(2L, 1L);
    Map<Long, Node> byId = Map.of(1L, first, 2L, second);
    AtomicInteger batches = new AtomicInteger();
    ResourceRenderer.TargetResolver resolver =
        new ResourceRenderer.TargetResolver() {
          @Override
          public Optional<ResourceRenderer.ResolvedTarget> resolve(String resourceName, Object id) {
            throw new AssertionError("rendering must reuse the batched result");
          }

          @Override
          public Map<ResourceRenderer.TargetKey, Optional<ResourceRenderer.ResolvedTarget>>
              resolveAll(java.util.Collection<ResourceRenderer.TargetKey> targets) {
            batches.incrementAndGet();
            java.util.LinkedHashMap<
                    ResourceRenderer.TargetKey, Optional<ResourceRenderer.ResolvedTarget>>
                resolved = new java.util.LinkedHashMap<>();
            targets.forEach(
                key ->
                    resolved.put(
                        key,
                        Optional.ofNullable(byId.get(key.id()))
                            .map(
                                entity ->
                                    new ResourceRenderer.ResolvedTarget(
                                        entity, FieldSelection.all()))));
            return resolved;
          }
        };

    List<Map<String, Object>> documents =
        new ResourceRenderer(registry)
            .renderAll(
                List.of(first, second),
                nodes,
                ignored -> FieldSelection.all(),
                ResourceFieldSelections.root(FieldSelection.all()),
                IncludeTree.toDepth(nodes, registry, 1),
                resolver);

    assertEquals(2, documents.size());
    assertEquals(1, batches.get());
  }

  @Test
  void validatesPolymorphicTargetEntityAndIdTypes() {
    CollectionDescription<Node> nodes = nodes();
    CollectionDescription<Node> wrongEntityTarget =
        new CollectionDescription<>(
            "nodes",
            Node.class,
            nodes.fields(),
            List.of(
                CollectionDescription.Relationship.polymorphicToOne(
                    "target",
                    CollectionFieldTypes.longNumber(),
                    List.of(new RelationshipTarget<>("nodes", NodeKind.NODE, "IN", String.class)),
                    new SplitReferenceBinding<>(Node::target, "targetType", "targetId"))),
            "id",
            List.of(new Sort("id", true)));
    assertThrows(
        IllegalArgumentException.class, () -> new ResourceRegistry(List.of(wrongEntityTarget)));

    CollectionDescription<StringIdTarget> stringTargets =
        new CollectionDescription<>(
            "stringTargets",
            StringIdTarget.class,
            List.of(Field.readOnly("id", "id", CollectionFieldTypes.text(20), StringIdTarget::id)),
            List.of(),
            "id",
            List.of(new Sort("id", true)));
    CollectionDescription<Node> wrongIdSource =
        new CollectionDescription<>(
            "nodes",
            Node.class,
            nodes.fields(),
            List.of(
                CollectionDescription.Relationship.polymorphicToOne(
                    "target",
                    CollectionFieldTypes.longNumber(),
                    List.of(
                        new RelationshipTarget<>(
                            "stringTargets", NodeKind.NODE, null, StringIdTarget.class)),
                    new SplitReferenceBinding<>(Node::target, "targetType", "targetId"))),
            "id",
            List.of(new Sort("id", true)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ResourceRegistry(List.of(wrongIdSource, stringTargets)));
  }

  @Test
  void rejectsAnAmbiguouslyTypedPolymorphicTargetFieldAtStartup() {
    CollectionDescription<NumericTarget> numericTargets =
        new CollectionDescription<>(
            "numericTargets",
            NumericTarget.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), NumericTarget::id),
                Field.readOnly(
                    "name", "name", CollectionFieldTypes.longNumber(), NumericTarget::name)),
            List.of(),
            "id",
            List.of(new Sort("id", true)));
    CollectionDescription<Node> source =
        new CollectionDescription<>(
            "nodes",
            Node.class,
            nodes().fields(),
            List.of(
                CollectionDescription.Relationship.polymorphicToOne(
                    "target",
                    CollectionFieldTypes.longNumber(),
                    List.of(
                        new RelationshipTarget<>("children", NodeKind.NODE, null, Child.class),
                        new RelationshipTarget<>(
                            "numericTargets", NodeKind.NUMERIC, null, NumericTarget.class)),
                    new SplitReferenceBinding<>(Node::target, "targetType", "targetId"))),
            "id",
            List.of(new Sort("id", true)));

    assertThrows(
        IllegalArgumentException.class,
        () -> new ResourceRegistry(List.of(source, CHILDREN, numericTargets)));
  }

  @Test
  void indexesCompatiblePolymorphicTargetsAsOneQueryPath() {
    CollectionDescription<NamedTarget> namedTargets =
        new CollectionDescription<>(
            "namedTargets",
            NamedTarget.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), NamedTarget::id),
                Field.readOnly("name", "name", CollectionFieldTypes.text(20), NamedTarget::name)),
            List.of(),
            "id",
            List.of(new Sort("id", true)));
    CollectionDescription<Node> source =
        polymorphicNodes(
            List.of(
                new RelationshipTarget<>("children", NodeKind.NODE, null, Child.class),
                new RelationshipTarget<>(
                    "namedTargets", NodeKind.NUMERIC, null, NamedTarget.class)));

    ResourceRegistry.RelationshipQueryPath path =
        new ResourceRegistry(List.of(source, CHILDREN, namedTargets))
            .findRelationshipQueryPath("nodes", "target.name")
            .orElseThrow();

    assertEquals(List.of("children", "namedTargets"), targetResourceNames(path));
    assertEquals(
        FilterSelector.relationshipTargetFieldOperators(), path.filterSelector().operators());
  }

  @Test
  void indexesEachDirectionAndSelfReferenceIndependently() {
    CollectionDescription<Left> left =
        new CollectionDescription<>(
            "lefts",
            Left.class,
            List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Left::id)),
            List.of(
                CollectionDescription.Relationship.polymorphicToOne(
                    "right",
                    CollectionFieldTypes.longNumber(),
                    List.of(
                        new RelationshipTarget<>("rights", DirectionKind.RIGHT, null, Right.class)),
                    new SplitReferenceBinding<>(Left::right, "rightType", "rightId"))),
            "id",
            List.of(new Sort("id", true)));
    CollectionDescription<Right> right =
        new CollectionDescription<>(
            "rights",
            Right.class,
            List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Right::id)),
            List.of(
                CollectionDescription.Relationship.polymorphicToOne(
                    "left",
                    CollectionFieldTypes.longNumber(),
                    List.of(
                        new RelationshipTarget<>("lefts", DirectionKind.LEFT, null, Left.class)),
                    new SplitReferenceBinding<>(Right::left, "leftType", "leftId"))),
            "id",
            List.of(new Sort("id", true)));
    CollectionDescription<Node> nodes = nodes();

    ResourceRegistry registry = new ResourceRegistry(List.of(left, right, nodes));

    assertTrue(registry.findRelationshipQueryPath("lefts", "right.id").isPresent());
    assertTrue(registry.findRelationshipQueryPath("rights", "left.id").isPresent());
    assertTrue(registry.findRelationshipQueryPath("nodes", "target.id").isPresent());
  }

  @Test
  void evaluatesOneTargetPolicyOnceAcrossSeveralRelationships() {
    AtomicInteger evaluations = new AtomicInteger();
    AccessFunction countedRead =
        AccessFunction.documented(
            "Counted read access.",
            Set.of(),
            context -> {
              evaluations.incrementAndGet();
              return AccessResult.allowed();
            });
    CollectionDescription<Child> children =
        new CollectionDescription<>(
            "children",
            Child.class,
            CHILDREN.fields(),
            List.of(),
            "id",
            List.of(new Sort("id", true)),
            AccessPolicy.readOnly(countedRead));
    CollectionDescription<TwoChildren> source =
        new CollectionDescription<>(
            "twoChildren",
            TwoChildren.class,
            List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), TwoChildren::id)),
            List.of(
                Relationship.referenceToOne(
                    "first",
                    "children",
                    CollectionFieldTypes.longNumber(),
                    Child.class,
                    TwoChildren::first,
                    Child::id,
                    "first.id"),
                Relationship.referenceToOne(
                    "second",
                    "children",
                    CollectionFieldTypes.longNumber(),
                    Child.class,
                    TwoChildren::second,
                    Child::id,
                    "second.id")),
            "id",
            List.of(new Sort("id", true)));
    ResourceRegistry registry = new ResourceRegistry(List.of(source, children));
    RelationshipReadAccess access = RelationshipReadAccess.forActor(registry, null);

    assertTrue(access.findPath("twoChildren", "first.name").isPresent());
    assertTrue(access.findPath("twoChildren", "second.name").isPresent());
    assertEquals(access.result("children"), access.result("children"));
    assertEquals(1, evaluations.get());
  }

  private static CollectionDescription<Node> polymorphicNodes(
      List<RelationshipTarget<NodeKind>> targets) {
    return new CollectionDescription<>(
        "nodes",
        Node.class,
        nodes().fields(),
        List.of(
            CollectionDescription.Relationship.polymorphicToOne(
                "target",
                CollectionFieldTypes.longNumber(),
                targets,
                new SplitReferenceBinding<>(Node::target, "targetType", "targetId"))),
        "id",
        List.of(new Sort("id", true)));
  }

  private static List<String> targetResourceNames(ResourceRegistry.RelationshipQueryPath path) {
    return path.targets().stream().map(target -> target.target().resourceName()).toList();
  }

  private static CollectionDescription<Node> nodes() {
    return new CollectionDescription<>(
        "nodes",
        Node.class,
        List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Node::id)),
        List.of(
            CollectionDescription.Relationship.polymorphicToOne(
                "target",
                CollectionFieldTypes.longNumber(),
                List.of(new RelationshipTarget<>("nodes", NodeKind.NODE, "IN", Node.class)),
                new SplitReferenceBinding<>(Node::target, "targetType", "targetId"))),
        "id",
        List.of(new Sort("id", true)));
  }

  private record Child(Long id, String name) {}

  private record Parent(Long id, Child primaryChild) {}

  private record Node(Long id, Long targetId) {
    ResourceReference<NodeKind, Long> target() {
      return new ResourceReference<>(NodeKind.NODE, targetId);
    }
  }

  private record StringIdTarget(String id) {}

  private record NumericTarget(Long id, Long name) {}

  private record NamedTarget(Long id, String name) {}

  private record TwoChildren(Long id, Child first, Child second) {}

  private record Left(Long id, Long rightId) {
    ResourceReference<DirectionKind, Long> right() {
      return new ResourceReference<>(DirectionKind.RIGHT, rightId);
    }
  }

  private record Right(Long id, Long leftId) {
    ResourceReference<DirectionKind, Long> left() {
      return new ResourceReference<>(DirectionKind.LEFT, leftId);
    }
  }

  private enum NodeKind {
    NODE,
    NUMERIC
  }

  private enum DirectionKind {
    LEFT,
    RIGHT
  }
}

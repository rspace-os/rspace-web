package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Relationship;
import com.researchspace.model.collection.CollectionDescription.Sort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  void rejectsUnknownTargetsAndDuplicateResourcesAtStartup() {
    assertThrows(IllegalArgumentException.class, () -> new ResourceRegistry(List.of(PARENTS)));
    assertThrows(
        IllegalArgumentException.class, () -> new ResourceRegistry(List.of(CHILDREN, CHILDREN)));
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

  private enum NodeKind {
    NODE
  }
}

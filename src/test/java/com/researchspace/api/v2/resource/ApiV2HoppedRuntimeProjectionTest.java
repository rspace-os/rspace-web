package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v2.model.ApiV2CollectionQuery;
import com.researchspace.api.v2.model.ApiV2FieldsetQuery;
import com.researchspace.api.v2.query.ApiV2ResourceRequestParser;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldCatalogPage;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldContext;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.collection.SplitReferenceBinding;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiV2HoppedRuntimeProjectionTest {

  private static final User ACTOR = new User("actor");

  @Test
  void attachesTargetValuesUnderTheSelectorTheRequestAskedWith() {
    Nodes nodes = new Nodes(new Node(1L, 7L), new Node(2L, 8L));
    ApiV2ResourceCatalog catalog = catalog(nodes, new StubTargetFields(Map.of(7L, "BSL-2")));

    Map<String, Object> first = list(catalog, nodes).get(0);
    Map<String, Object> second = list(catalog, nodes).get(1);

    assertEquals(Map.of("SF1", "BSL-2"), first.get("target.customFields"));
    assertEquals(Map.of(), second.get("target.customFields"));
  }

  @Test
  void refusesTheSelectorWhenTheTargetProviderIsFilterOnly() {
    Nodes nodes = new Nodes(new Node(1L, 7L));
    StubTargetFields filterOnly = new StubTargetFields(Map.of(7L, "BSL-2"));
    filterOnly.projectable = false;
    ApiV2ResourceCatalog catalog = catalog(nodes, filterOnly);

    assertThrows(CollectionQueryException.class, () -> request(catalog));
  }

  private List<Map<String, Object>> list(ApiV2ResourceCatalog catalog, Nodes nodes) {
    ApiV2ResourceRegistration<?, ?> registration = catalog.find("nodes").orElseThrow();
    return registration.list(request(catalog), ACTOR).docs();
  }

  private ResourceRequest request(ApiV2ResourceCatalog catalog) {
    ApiV2FieldsetQuery fieldsets = new ApiV2FieldsetQuery();
    fieldsets.setFields(Map.of("nodes", "id,target.customFields.SF1"));
    return ApiV2ResourceRequestParser.parse(
        new ApiV2CollectionQuery(),
        fieldsets,
        catalog.find("nodes").orElseThrow().description(),
        catalog.registry(),
        new RuntimeFieldContext(
            List.of(),
            ACTOR,
            name ->
                catalog.find(name).map(ApiV2ResourceRegistration::providers).orElseGet(List::of)));
  }

  private ApiV2ResourceCatalog catalog(Nodes nodes, StubTargetFields targetFields) {
    ApiV2ResourceSpec<Node, Long> nodeSpec =
        new ApiV2ResourceSpec<>(nodeDescription(), nodes, Long::valueOf, "error", "error");
    ApiV2ResourceSpec<Target, Long> targetSpec =
        new ApiV2ResourceSpec<>(
            targetDescription(),
            new Targets(),
            Long::valueOf,
            "error",
            "error",
            Set.copyOf(EnumSet.allOf(ResourceOperation.class)),
            Map.of(),
            Map.of(),
            CollectionMutationLimits.DEFAULT,
            List.of(targetFields));
    return new ApiV2ResourceCatalog(List.of(nodeSpec, targetSpec));
  }

  private static CollectionDescription<Node> nodeDescription() {
    return new CollectionDescription<>(
        "nodes",
        Node.class,
        List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Node::id)),
        List.of(
            CollectionDescription.Relationship.polymorphicToOne(
                    "target",
                    CollectionFieldTypes.longNumber(),
                    List.of(new RelationshipTarget<>("targets", "TARGET", Target.class)),
                    new SplitReferenceBinding<>(Node::reference, "targetType", "targetId"))
                .required()),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.authenticated());
  }

  private static CollectionDescription<Target> targetDescription() {
    return new CollectionDescription<>(
        "targets",
        Target.class,
        List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Target::id)),
        List.of(),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.authenticated());
  }

  private static final class StubTargetFields implements RuntimeCollectionFields<Target> {

    private final Map<Long, String> valuesById;
    private boolean projectable = true;

    private StubTargetFields(Map<Long, String> valuesById) {
      this.valuesById = valuesById;
    }

    @Override
    public String namespace() {
      return "customFields";
    }

    @Override
    public boolean projectsThroughRelationship() {
      return projectable;
    }

    @Override
    public RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query) {
      return new RuntimeFieldCatalogPage(List.of(definition()), 1L, false);
    }

    @Override
    public Optional<ResolvedRuntimeField> resolve(String selector, User actor) {
      return "customFields.SF1".equals(selector)
          ? Optional.of(new ResolvedRuntimeField(definition(), binding()))
          : Optional.empty();
    }

    @Override
    public Map<Object, Map<String, Object>> values(
        List<Target> resources, Set<String> fieldIds, User actor) {
      return valuesForIds(resources.stream().map(Target::id).toList(), fieldIds, actor);
    }

    @Override
    public Map<Object, Map<String, Object>> valuesForIds(
        Collection<?> resourceIds, Set<String> fieldIds, User actor) {
      Map<Object, Map<String, Object>> values = new LinkedHashMap<>();
      for (Object id : resourceIds) {
        String value = valuesById.get(id);
        values.put(id, value == null ? Map.of() : Map.of("SF1", value));
      }
      return values;
    }

    private static RuntimeFieldDefinition definition() {
      return new RuntimeFieldDefinition(
          "SF1",
          "customFields.SF1",
          "Hazard class",
          RuntimeFieldValueType.TEXT,
          "IT1",
          "Cell line",
          List.of());
    }

    private static RuntimeFieldBinding binding() {
      return new RuntimeFieldBinding(Target.class, "id", "value", Map.of("definition", 1L));
    }
  }

  private static final class Nodes implements ResourceOperations<Node, Long> {

    private final List<Node> nodes;

    private Nodes(Node... nodes) {
      this.nodes = List.of(nodes);
    }

    @Override
    public ResourcePage<Node> find(ResourceRequest request, User subject) {
      return new ResourcePage<>(nodes, nodes.size());
    }

    @Override
    public long count(ResourceRequest request, User subject) {
      return nodes.size();
    }

    @Override
    public Optional<Node> findById(Long id, User subject) {
      return nodes.stream().filter(node -> node.id().equals(id)).findFirst();
    }
  }

  private static final class Targets implements ResourceOperations<Target, Long> {

    @Override
    public ResourcePage<Target> find(ResourceRequest request, User subject) {
      return new ResourcePage<>(List.of(), 0);
    }

    @Override
    public long count(ResourceRequest request, User subject) {
      return 0;
    }

    @Override
    public Optional<Target> findById(Long id, User subject) {
      return Optional.of(new Target(id));
    }
  }

  private record Node(Long id, Long targetId) {

    private ResourceReference<String, Long> reference() {
      return targetId == null ? null : new ResourceReference<>("TARGET", targetId);
    }
  }

  private record Target(Long id) {}
}

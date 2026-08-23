package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldCatalogPage;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.RuntimeFieldValueType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiV2RuntimeNamespaceRenderingTest {

  record Widget(Long id, String name) {}

  private static final User ACTOR = mock(User.class);

  private static final class StubProvider implements RuntimeCollectionFields<Widget> {

    private final String namespace;
    private final Map<String, String> valuesById;
    private Set<String> asked;

    private StubProvider(String namespace, Map<String, String> valuesById) {
      this.namespace = namespace;
      this.valuesById = valuesById;
    }

    @Override
    public String namespace() {
      return namespace;
    }

    @Override
    public RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query) {
      return RuntimeFieldCatalogPage.empty();
    }

    @Override
    public Optional<ResolvedRuntimeField> resolve(String selector, User actor) {
      return Optional.empty();
    }

    @Override
    public Map<Object, Map<String, Object>> values(
        List<Widget> resources, Set<String> fieldIds, User actor) {
      asked = fieldIds;
      Map<String, Object> document = new LinkedHashMap<>();
      fieldIds.forEach(
          id -> {
            if (valuesById.containsKey(id)) {
              document.put(id, valuesById.get(id));
            }
          });
      Map<Object, Map<String, Object>> values = new LinkedHashMap<>();
      resources.forEach(widget -> values.put(widget.id(), document));
      return values;
    }
  }

  private static ResolvedRuntimeField field(String namespace, String id) {
    Map<String, Object> match = new LinkedHashMap<>();
    match.put("name", id);
    return new ResolvedRuntimeField(
        new RuntimeFieldDefinition(
            id, namespace + "." + id, id, RuntimeFieldValueType.TEXT, "", "", List.of()),
        new RuntimeFieldBinding(Widget.class, "widget.id", "data", match));
  }

  @Test
  void writesEachProvidersValuesUnderItsOwnNamespace() {
    StubProvider custom = new StubProvider("customFields", Map.of("SF1", "from template"));
    StubProvider extra = new StubProvider("extraFields", Map.of("XFtAA", "typed on this one"));
    ApiV2ResourceRegistration<Widget, Long> widgets = register(List.of(custom, extra));
    ResourceRequest request =
        request(
            new RuntimeFieldSelection(
                Map.of(
                    "customFields.SF1", field("customFields", "SF1"),
                    "extraFields.XFtAA", field("extraFields", "XFtAA")),
                Set.of("customFields.SF1", "extraFields.XFtAA")));

    Map<String, Object> document = widgets.list(request, ACTOR).docs().get(0);

    assertEquals(Map.of("SF1", "from template"), document.get("customFields"));
    assertEquals(Map.of("XFtAA", "typed on this one"), document.get("extraFields"));
    assertEquals(Set.of("SF1"), custom.asked);
    assertEquals(Set.of("XFtAA"), extra.asked);
  }

  @Test
  void asksNoProviderWhenNothingUnderItsNamespaceWasProjected() {
    StubProvider custom = new StubProvider("customFields", Map.of("SF1", "from template"));
    StubProvider extra = new StubProvider("extraFields", Map.of("XFtAA", "typed on this one"));
    ApiV2ResourceRegistration<Widget, Long> widgets = register(List.of(custom, extra));
    ResourceRequest request =
        request(
            new RuntimeFieldSelection(
                Map.of("customFields.SF1", field("customFields", "SF1")),
                Set.of("customFields.SF1")));

    Map<String, Object> document = widgets.list(request, ACTOR).docs().get(0);

    assertTrue(document.containsKey("customFields"));
    assertNull(extra.asked);
    assertFalse(document.containsKey("extraFields"));
  }

  @SuppressWarnings("unchecked")
  private ApiV2ResourceRegistration<Widget, Long> register(
      List<RuntimeCollectionFields<Widget>> providers) {
    CollectionDescription<Widget> widgets =
        new CollectionDescription<>(
            "widgets",
            Widget.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Widget::id),
                Field.readOnly("name", "name", CollectionFieldTypes.text(), Widget::name)),
            List.of(),
            "id",
            List.of(new Sort("id", true)),
            AccessPolicy.readOnly(AccessFunction.anyone()));
    ResourceOperations<Widget, Long> operations = mock(ResourceOperations.class);
    when(operations.find(any(), nullable(User.class)))
        .thenReturn(new ResourcePage<>(List.of(new Widget(1L, "one")), 1));
    ApiV2ResourceSpec<Widget, Long> spec =
        new ApiV2ResourceSpec<>(
            widgets,
            operations,
            Long::valueOf,
            "errors.api.v2.invalidRequest",
            "errors.api.v2.invalidRequest",
            Set.of(ResourceOperation.LIST),
            Map.of(),
            Map.of(),
            com.researchspace.model.collection.CollectionMutationLimits.DEFAULT,
            providers);
    return spec.bind(
        new ResourceRegistry(List.of(widgets)), ApiV2RelationshipResolver.unavailable());
  }

  private static ResourceRequest request(RuntimeFieldSelection runtime) {
    return new ResourceRequest(
        null,
        null,
        List.of(new Sort("id", true)),
        new ResourceRequest.Page(1, 20),
        com.researchspace.model.collection.ResourceFieldSelections.root(FieldSelection.all()),
        IncludeTree.empty(),
        runtime);
  }
}

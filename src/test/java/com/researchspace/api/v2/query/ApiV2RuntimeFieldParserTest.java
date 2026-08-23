package com.researchspace.api.v2.query;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.controller.ApiV2BadRequestException;
import com.researchspace.api.v2.model.ApiV2CollectionQuery;
import com.researchspace.api.v2.model.ApiV2FieldsetQuery;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldCatalogPage;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldContext;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ApiV2RuntimeFieldParserTest {

  private static final ResourceRegistry REGISTRY =
      new ResourceRegistry(List.of(ApiV2InstrumentResource.DESCRIPTION));
  private static final ResourceRegistry BOOKING_REGISTRY =
      new ResourceRegistry(
          List.of(
              ApiV2BookingConfigurationResource.DESCRIPTION,
              ApiV2InstrumentResource.DESCRIPTION,
              ApiV2UserResource.DESCRIPTION));
  private static final User READER = new User("reader");
  private static final User STRANGER = new User("stranger");

  private static final class StubFields implements RuntimeCollectionFields<Instrument> {

    private int resolveCalls;
    private int resolveAllCalls;

    @Override
    public boolean projectsThroughRelationship() {
      return true;
    }

    @Override
    public String namespace() {
      return "customFields";
    }

    @Override
    public RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query) {
      List<RuntimeFieldDefinition> all =
          READER.equals(actor) ? List.of(definition("SF1"), definition("SF2")) : List.of();
      List<RuntimeFieldDefinition> selected =
          query.hydratesIds()
              ? all.stream().filter(field -> query.ids().contains(field.id())).toList()
              : all;
      return new RuntimeFieldCatalogPage(selected, (long) selected.size(), false);
    }

    @Override
    public Optional<ResolvedRuntimeField> resolve(String selector, User actor) {
      resolveCalls++;
      if (!READER.equals(actor)) {
        return Optional.empty();
      }
      return discover(actor, RuntimeFieldCatalogQuery.firstPage()).fields().stream()
          .filter(definition -> definition.selector().equals(selector))
          .findFirst()
          .map(definition -> new ResolvedRuntimeField(definition, binding(definition.id())));
    }

    @Override
    public Map<String, ResolvedRuntimeField> resolveAll(Set<String> selectors, User actor) {
      resolveAllCalls++;
      return RuntimeCollectionFields.super.resolveAll(selectors, actor);
    }

    @Override
    public Map<Object, Map<String, Object>> values(
        List<Instrument> resources, Set<String> fieldIds, User actor) {
      return Map.of();
    }

    private static RuntimeFieldDefinition definition(String id) {
      return new RuntimeFieldDefinition(
          id,
          "customFields." + id,
          "Hazard class",
          "SF1".equals(id) ? RuntimeFieldValueType.TEXT : RuntimeFieldValueType.NUMBER,
          "IT9",
          "Cell line template",
          List.of());
    }

    private static RuntimeFieldBinding binding(String id) {
      Map<String, Object> match = new LinkedHashMap<>();
      match.put("templateField.id", Long.valueOf(id.substring(2)));
      match.put("deleted", false);
      return new RuntimeFieldBinding(
          InventoryEntityField.class, "instrumentEntity.id", "data", match);
    }
  }

  private static RuntimeFieldContext context(User actor) {
    return new RuntimeFieldContext(List.of(new StubFields()), actor);
  }

  private static final class StubExtraFields implements RuntimeCollectionFields<Instrument> {

    @Override
    public String namespace() {
      return "extraFields";
    }

    @Override
    public RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query) {
      return RuntimeFieldCatalogPage.empty();
    }

    @Override
    public Optional<ResolvedRuntimeField> resolve(String selector, User actor) {
      if (!READER.equals(actor) || !"extraFields.XFt526f6f6d".equals(selector)) {
        return Optional.empty();
      }
      RuntimeFieldDefinition definition =
          new RuntimeFieldDefinition(
              "XFt526f6f6d", selector, "Room", RuntimeFieldValueType.TEXT, "", "", List.of());
      Map<String, Object> match = new LinkedHashMap<>();
      match.put("editInfo.name", "Room");
      match.put("deleted", false);
      return Optional.of(
          new ResolvedRuntimeField(
              definition,
              new RuntimeFieldBinding(
                  InventoryEntityField.class,
                  "instrumentEntity.id",
                  "editInfo.description",
                  match)));
    }

    @Override
    public Map<Object, Map<String, Object>> values(
        List<Instrument> resources, Set<String> fieldIds, User actor) {
      return Map.of();
    }
  }

  @Test
  void routesEachSelectorToTheProviderOwningItsNamespace() {
    RuntimeFieldContext both =
        new RuntimeFieldContext(List.of(new StubFields(), new StubExtraFields()), READER);

    ResourceRequest request =
        ApiV2ResourceRequestParser.parse(
            collectionQuery("customFields.SF1==BSL-2;extraFields.XFt526f6f6d==\"Lab 4\""),
            fields(Map.of("instruments", "name,customFields.SF1,extraFields.XFt526f6f6d")),
            ApiV2InstrumentResource.DESCRIPTION,
            REGISTRY,
            both);

    assertEquals("SF1", request.runtime().find("customFields.SF1").id());
    assertEquals("XFt526f6f6d", request.runtime().find("extraFields.XFt526f6f6d").id());
    assertEquals(Set.of("SF1"), request.runtime().projectedIdsUnder("customFields"));
    assertEquals(Set.of("XFt526f6f6d"), request.runtime().projectedIdsUnder("extraFields"));
  }

  @Test
  void refusesASelectorInANamespaceTheCollectionDoesNotPublish() {
    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.filtered(
                "extraFields.XFt526f6f6d==x",
                ApiV2InstrumentResource.DESCRIPTION,
                REGISTRY,
                context(READER)));
  }

  private static ApiV2CollectionQuery collectionQuery(String where) {
    ApiV2CollectionQuery query = new ApiV2CollectionQuery();
    query.setWhere(where);
    return query;
  }

  private static ApiV2FieldsetQuery fields(Map<String, String> fields) {
    ApiV2FieldsetQuery query = new ApiV2FieldsetQuery();
    query.setFields(fields);
    return query;
  }

  private static ResourceRequest filtered(String where, User actor) {
    return ApiV2ResourceRequestParser.filtered(
        where, ApiV2InstrumentResource.DESCRIPTION, REGISTRY, context(actor));
  }

  @Test
  void resolvesAReadableRuntimeSelectorAndCarriesItOnTheRequest() {
    ResourceRequest request = filtered("customFields.SF1==BSL-2", READER);

    ResolvedRuntimeField resolved = request.runtime().find("customFields.SF1");
    assertEquals("SF1", resolved.id());
    assertEquals(RuntimeFieldValueType.TEXT, resolved.type());
    assertEquals(
        new FilterExpression.Comparison(
            "customFields.SF1", Operator.EQUAL, List.of("BSL-2"), false),
        request.filter());
  }

  @Test
  void parsesEveryPublishedOperatorForTheResolvedType() {
    assertDoesNotThrow(() -> filtered("customFields.SF1==a", READER));
    assertDoesNotThrow(() -> filtered("customFields.SF1!=a", READER));
    assertDoesNotThrow(() -> filtered("customFields.SF1=in=(a,b)", READER));
    assertDoesNotThrow(() -> filtered("customFields.SF1=out=(a,b)", READER));
    assertDoesNotThrow(() -> filtered("customFields.SF1=contains=a", READER));
    assertDoesNotThrow(() -> filtered("customFields.SF1=like=a", READER));
    assertDoesNotThrow(() -> filtered("customFields.SF1=exists=true", READER));
    assertDoesNotThrow(() -> filtered("customFields.SF2=ge=-80", READER));
  }

  @Test
  void parsesANumericValueRatherThanKeepingItAsText() {
    ResourceRequest request = filtered("customFields.SF2=ge=-80", READER);

    FilterExpression.Comparison comparison = (FilterExpression.Comparison) request.filter();
    assertEquals(List.of(Double.valueOf(-80)), comparison.values());
  }

  @Test
  void refusesAnOperatorTheTypeDoesNotPublish() {
    assertThrows(CollectionQueryException.class, () -> filtered("customFields.SF1=ge=a", READER));
    assertThrows(
        CollectionQueryException.class, () -> filtered("customFields.SF2=contains=8", READER));
  }

  @Test
  void refusesAnInvalidValueForTheResolvedType() {
    assertThrows(
        CollectionQueryException.class, () -> filtered("customFields.SF2==not-a-number", READER));
  }

  @Test
  void reportsUnknownAndInaccessibleDefinitionsIdentically() {
    CollectionQueryException unknown =
        assertThrows(
            CollectionQueryException.class, () -> filtered("customFields.SF404==x", READER));
    CollectionQueryException inaccessible =
        assertThrows(
            CollectionQueryException.class, () -> filtered("customFields.SF1==x", STRANGER));

    assertEquals(CollectionQueryException.Reason.FIELD, unknown.getReason());
    assertEquals(unknown.getReason(), inaccessible.getReason());
  }

  @Test
  void reportsAnInaccessibleDefinitionBeforeItsValueIsTypeChecked() {
    CollectionQueryException refused =
        assertThrows(
            CollectionQueryException.class,
            () -> filtered("customFields.SF2==not-a-number", STRANGER));

    assertEquals(CollectionQueryException.Reason.FIELD, refused.getReason());
  }

  @Test
  void refusesTheBareNamespace() {
    assertThrows(CollectionQueryException.class, () -> filtered("customFields==x", READER));
  }

  @Test
  void leavesADescribedFieldAlone() {
    ResourceRequest request = filtered("name==scope", READER);

    assertTrue(request.runtime().isEmpty());
  }

  @Test
  void selectsARuntimeFieldAsAColumnWithoutAddingAPredicate() {
    ResourceRequest request =
        ApiV2ResourceRequestParser.item(
            0,
            fields(Map.of("instruments", "id,name,customFields.SF1")),
            ApiV2InstrumentResource.DESCRIPTION,
            REGISTRY,
            context(READER));

    assertEquals(Set.of("SF1"), request.runtime().projectedIds());
    assertTrue(request.fields().includes("name", "id"));
    assertFalse(request.fields().includes("customFields.SF1", "id"));
  }

  @Test
  void filteringOnARuntimeFieldDoesNotProjectItsValue() {
    assertTrue(filtered("customFields.SF1==BSL-2", READER).runtime().projected().isEmpty());
  }

  @Test
  void refusesAnInaccessibleOrUnknownProjection() {
    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.item(
                0,
                fields(Map.of("instruments", "id,customFields.SF1")),
                ApiV2InstrumentResource.DESCRIPTION,
                REGISTRY,
                context(STRANGER)));
    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.item(
                0,
                fields(Map.of("instruments", "id,customFields")),
                ApiV2InstrumentResource.DESCRIPTION,
                REGISTRY,
                context(READER)));
  }

  @Test
  void limitsTheNumberOfProjectedRuntimeFields() {
    String tooMany =
        IntStream.rangeClosed(1, CollectionQueryLimits.MAX_RUNTIME_PROJECTIONS + 1)
            .mapToObj(index -> "customFields.SF" + index)
            .collect(Collectors.joining(","));

    assertThrows(
        ApiV2BadRequestException.class,
        () ->
            ApiV2ResourceRequestParser.item(
                0,
                fields(Map.of("instruments", tooMany)),
                ApiV2InstrumentResource.DESCRIPTION,
                REGISTRY,
                new RuntimeFieldContext(List.of(new AlwaysResolves()), READER)));
  }

  @Test
  void resolvesASelectorUsedAsBothAFilterAndAColumnOnlyOnce() {
    StubFields provider = new StubFields();
    ApiV2CollectionQuery query = new ApiV2CollectionQuery();
    query.setWhere("customFields.SF1==BSL-2");
    ApiV2ResourceRequestParser.parse(
        query,
        fields(Map.of("instruments", "id,customFields.SF1")),
        ApiV2InstrumentResource.DESCRIPTION,
        REGISTRY,
        new RuntimeFieldContext(List.of(provider), READER));

    assertEquals(1, provider.resolveCalls);
  }

  @Test
  void resolvesAllRuntimeFilterSelectorsThroughOneProviderBatch() {
    StubFields provider = new StubFields();

    ApiV2ResourceRequestParser.filtered(
        "customFields.SF1==BSL-2;customFields.SF2=ge=2",
        ApiV2InstrumentResource.DESCRIPTION,
        REGISTRY,
        new RuntimeFieldContext(List.of(provider), READER));

    assertEquals(1, provider.resolveAllCalls);
  }

  @Test
  void refusesARuntimeSelectorOnABulkMutation() {
    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.bulk(
                "customFields.SF1==BSL-2", ApiV2InstrumentResource.DESCRIPTION, REGISTRY));
  }

  @Test
  void resolvesARuntimeFieldReachedThroughARelationship() {
    StubFields instruments = new StubFields();
    RuntimeFieldContext context =
        new RuntimeFieldContext(
            List.of(),
            READER,
            name -> "instruments".equals(name) ? List.of(instruments) : List.of());

    ResourceRequest request =
        ApiV2ResourceRequestParser.filtered(
            "target.customFields.SF1==BSL-2",
            ApiV2BookingConfigurationResource.DESCRIPTION,
            BOOKING_REGISTRY,
            context);

    ResolvedRuntimeField resolved = request.runtime().find("target.customFields.SF1");
    assertEquals("SF1", resolved.id());
    assertEquals("target", request.runtime().relationshipFor("target.customFields.SF1"));
    FilterExpression.Comparison comparison = (FilterExpression.Comparison) request.filter();
    assertEquals("target.customFields.SF1", comparison.field());
  }

  @Test
  void projectsARuntimeFieldReachedThroughARelationship() {
    StubFields instruments = new StubFields();
    RuntimeFieldContext context =
        new RuntimeFieldContext(
            List.of(),
            READER,
            name -> "instruments".equals(name) ? List.of(instruments) : List.of());

    ResourceRequest request =
        ApiV2ResourceRequestParser.parse(
            collectionQuery(null),
            fields(Map.of("booking-configurations", "id,target.customFields.SF1")),
            ApiV2BookingConfigurationResource.DESCRIPTION,
            BOOKING_REGISTRY,
            context);

    assertEquals(Set.of("target.customFields.SF1"), request.runtime().projected());
    assertEquals("target", request.runtime().relationshipFor("target.customFields.SF1"));
    assertEquals("SF1", request.runtime().find("target.customFields.SF1").id());
    assertFalse(request.fields().includes("target.customFields.SF1", "id"));
  }

  @Test
  void refusesProjectingThroughAProviderThatCannotAnswerByTargetId() {
    RuntimeFieldContext context =
        new RuntimeFieldContext(
            List.of(),
            READER,
            name -> "instruments".equals(name) ? List.of(new FilterOnlyFields()) : List.of());

    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.parse(
                collectionQuery(null),
                fields(Map.of("booking-configurations", "id,target.customFields.SF1")),
                ApiV2BookingConfigurationResource.DESCRIPTION,
                BOOKING_REGISTRY,
                context));
  }

  private static final class FilterOnlyFields implements RuntimeCollectionFields<Instrument> {

    private final StubFields delegate = new StubFields();

    @Override
    public String namespace() {
      return delegate.namespace();
    }

    @Override
    public boolean projectsThroughRelationship() {
      return false;
    }

    @Override
    public RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query) {
      return delegate.discover(actor, query);
    }

    @Override
    public Optional<ResolvedRuntimeField> resolve(String selector, User actor) {
      return delegate.resolve(selector, actor);
    }

    @Override
    public Map<Object, Map<String, Object>> values(
        List<Instrument> resources, Set<String> fieldIds, User actor) {
      return delegate.values(resources, fieldIds, actor);
    }
  }

  @Test
  void refusesARelationshipHopOntoADefinitionTheActorCannotReach() {
    StubFields instruments = new StubFields();
    RuntimeFieldContext context =
        new RuntimeFieldContext(
            List.of(),
            STRANGER,
            name -> "instruments".equals(name) ? List.of(instruments) : List.of());

    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.filtered(
                "target.customFields.SF1==BSL-2",
                ApiV2BookingConfigurationResource.DESCRIPTION,
                BOOKING_REGISTRY,
                context));
  }

  private static final class AlwaysResolves implements RuntimeCollectionFields<Instrument> {

    private final String namespace;

    private AlwaysResolves() {
      this("customFields");
    }

    private AlwaysResolves(String namespace) {
      this.namespace = namespace;
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
      String id = selector.substring(selector.indexOf('.') + 1);
      return Optional.of(
          new ResolvedRuntimeField(
              new RuntimeFieldDefinition(
                  id, selector, id, RuntimeFieldValueType.TEXT, "IT9", "Template", List.of()),
              StubFields.binding(id)));
    }

    @Override
    public Map<Object, Map<String, Object>> values(
        List<Instrument> resources, Set<String> fieldIds, User actor) {
      return Map.of();
    }
  }

  @Test
  void appliesTheProjectionLimitIndependentlyToEachNamespace() {
    String fields =
        java.util.stream.Stream.concat(
                IntStream.rangeClosed(1, 30).mapToObj(index -> "customFields.SF" + index),
                IntStream.rangeClosed(31, 60).mapToObj(index -> "extraFields.SF" + index))
            .collect(Collectors.joining(","));

    assertDoesNotThrow(
        () ->
            ApiV2ResourceRequestParser.item(
                0,
                fields(Map.of("instruments", fields)),
                ApiV2InstrumentResource.DESCRIPTION,
                REGISTRY,
                new RuntimeFieldContext(
                    List.of(new AlwaysResolves("customFields"), new AlwaysResolves("extraFields")),
                    READER)));
  }
}

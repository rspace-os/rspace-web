package com.researchspace.api.v2.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.DeclaredFieldType;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.DocumentValidationException.Reason;
import com.researchspace.model.collection.EmbeddedFieldException;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.WriteOperation;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.webapp.controller.MVCTestBase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Declared-array contracts through the production MVC, parser, renderer and OpenAPI chain. */
@ActiveProfiles("declared-array-http-contract")
class ApiV2DeclaredArrayContractMVCIT extends MVCTestBase {

  private static final String RESOURCE = "contract-arrays";
  private static final String ENDPOINT = "/api/v2/" + RESOURCE;
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final List<Field<Detail, ?>> DETAIL_FIELDS =
      List.of(
          Field.embedded("name", CollectionFieldTypes.text(10), Detail::name).required(),
          Field.embedded("number", CollectionFieldTypes.integer(), Detail::number).defaultValue(2));
  private static final DeclaredFieldType<Detail> DETAIL =
      CollectionFieldTypes.object(
          Detail.class, DETAIL_FIELDS, ApiV2DeclaredArrayContractMVCIT::detail);
  private static final DeclaredFieldType<List<Detail>> NESTED =
      CollectionFieldTypes.array(
          Detail.class, DETAIL_FIELDS, ApiV2DeclaredArrayContractMVCIT::detail);
  private static final DeclaredFieldType<List<Row>> ROWS =
      CollectionFieldTypes.array(
          Row.class,
          List.of(
              Field.embedded("name", CollectionFieldTypes.text(10), Row::name).required(),
              Field.embedded("detail", DETAIL, Row::detail).required(),
              Field.embedded("nested", NESTED, Row::nested).defaultValue(List.of()),
              Field.embedded("restricted", CollectionFieldTypes.text(), Row::restricted)
                  .writableBy(AccessFunction.sysadmin())
                  .readableBy(AccessFunction.sysadmin())),
          values ->
              new Row(
                  (String) values.get("name"),
                  (Detail) values.get("detail"),
                  details(values.get("nested")),
                  (String) values.get("restricted"),
                  "private"));
  private static final List<Row> DEFAULT_ROWS =
      List.of(
          new Row("default", new Detail("child", 2, "private"), List.of(), "secret", "private"));
  private static final CollectionDescription<Item> DESCRIPTION =
      new CollectionDescription<>(
          RESOURCE,
          Item.class,
          List.of(
              Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), (Item item) -> item.id),
              Field.writable(
                      "rows",
                      "rows",
                      ROWS,
                      (Item item) -> item.rows,
                      (item, rows) -> item.rows = rows)
                  .defaultValue(DEFAULT_ROWS),
              Field.writable(
                  "label",
                  "label",
                  CollectionFieldTypes.text(),
                  (Item item) -> item.label,
                  (item, label) -> item.label = label)),
          "id",
          List.of(new Sort("id", true)));

  private final ArrayOperations operations;

  @Autowired
  ApiV2DeclaredArrayContractMVCIT(ArrayOperations operations) {
    this.operations = operations;
  }

  private ApiV2Fixture fixture;

  @Override
  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    operations.resetStore();
    fixture = ApiV2Fixture.in(wac);
    mockMvc = fixture.mockMvc();
  }

  @AfterEach
  void cleanFixture() {
    if (fixture != null) fixture.cleanUp();
  }

  @Test
  void createDefaultsNestedChildrenAndReplacementUpdatesReachTheAdapter() throws Exception {
    JsonNode omitted = response(write(post(ENDPOINT), "{}", fixture.userKey()), 201);
    assertEquals("default", omitted.at("/rows/0/name").asText());
    assertEquals(2, omitted.at("/rows/0/detail/number").asInt());
    assertFalse(omitted.at("/rows/0").has("restricted"));

    JsonNode created =
        response(
            write(
                post(ENDPOINT),
                """
                {"rows":[{"name":"first","detail":{"name":"inner"},
                          "nested":[{"name":"nested"}]}],"label":"kept"}
                """,
                fixture.userKey()),
            201);
    assertEquals(2, created.at("/rows/0/nested/0/number").asInt());
    long id = created.get("id").asLong();
    JsonNode updated =
        response(
            write(
                patch(ENDPOINT + "/" + id),
                "{\"rows\":[{\"name\":\"new\",\"detail\":{\"name\":\"other\"}}]}",
                fixture.userKey()),
            200);
    assertEquals(1, updated.get("rows").size());
    assertEquals("new", updated.at("/rows/0/name").asText());
    assertEquals(List.of(), JSON.convertValue(updated.at("/rows/0/nested"), List.class));
    assertEquals("kept", updated.get("label").asText());
    assertEquals(
        List.of(new Row("new", new Detail("other", 2, "private"), List.of(), null, "private")),
        operations.stored.get(id).rows);
    verify(operations.audit, times(3)).notify(any(HistoricalEvent.class));
  }

  @Test
  void indexedReplacementAndChildAccessFailuresNeverMutateOrAudit() throws Exception {
    Item before = operations.stored.get(1L);
    rejected(
        patch(ENDPOINT + "/1"),
        "{\"rows\":[{\"detail\":{\"name\":\"child\"}}]}",
        "rows[0].name",
        Reason.REQUIRED);
    rejected(
        patch(ENDPOINT + "/1"),
        "{\"rows\":[{\"name\":\"first\",\"detail\":{}}]}",
        "rows[0].detail.name",
        Reason.REQUIRED);
    rejected(
        patch(ENDPOINT + "/1"),
        "{\"rows\":[{\"name\":\"first\",\"detail\":{\"name\":\"child\"},\"restricted\":\"set\"}]}",
        "rows[0].restricted",
        Reason.READ_ONLY);
    rejected(
        post(ENDPOINT + "/bulk"),
        "{\"docs\":[{}, {\"rows\":[1]}]}",
        "docs[1].rows[0]",
        Reason.WRONG_TYPE);
    assertSame(before, operations.stored.get(1L));
    assertEquals(1, operations.stored.size());
    assertEquals(0, operations.mutations);
    verifyNoInteractions(operations.audit);
  }

  @Test
  void dtoAndSparseAlternateReadsMatchThePublishedChildSchemas() throws Exception {
    JsonNode dto = response(get(ENDPOINT + "/1").header("apiKey", fixture.userKey()), 200);
    assertEquals(Set.of("name", "detail", "nested"), keys(dto.at("/rows/0")));
    assertEquals(Set.of("name", "number"), keys(dto.at("/rows/0/detail")));

    operations.overrides =
        Map.of(
            1L,
            Map.of(
                "rows",
                List.of(
                    Map.of(
                        "name", "alternate",
                        "detail", Map.of("name", "wire", "privateValue", "hidden"),
                        "restricted", "hidden",
                        "privateValue", "hidden"))));
    JsonNode sparse =
        response(
            get(ENDPOINT)
                .header("apiKey", fixture.userKey())
                .param("fields[" + RESOURCE + "]", "rows"),
            200);
    assertEquals(Set.of("id", "rows"), keys(sparse.at("/docs/0")));
    assertEquals(Set.of("name", "detail"), keys(sparse.at("/docs/0/rows/0")));
    assertEquals(Set.of("name"), keys(sparse.at("/docs/0/rows/0/detail")));
    assertEquals("alternate", sparse.at("/docs/0/rows/0/name").asText());

    JsonNode schema = response(get("/api/v2/openapi.json"), 200).at("/components/schemas");
    for (String component :
        List.of("ContractArraysRead", "ContractArraysCreate", "ContractArraysUpdate")) {
      JsonNode rows = schema.path(component).at("/properties/rows");
      assertEquals("array", rows.path("type").asText());
      JsonNode item = rows.path("items");
      assertEquals(Set.of("name", "detail", "nested", "restricted"), keys(item.path("properties")));
      assertFalse(item.path("additionalProperties").asBoolean(true));
      JsonNode detail = item.at("/properties/detail");
      assertEquals(Set.of("name", "number"), keys(detail.path("properties")));
      assertEquals("object", item.at("/properties/nested/items/type").asText());
      if (!component.equals("ContractArraysRead")) {
        assertEquals(2, detail.at("/properties/number/default").asInt());
        assertEquals(Set.of("name", "detail"), JSON.convertValue(item.get("required"), Set.class));
        assertEquals(Set.of("name"), JSON.convertValue(detail.get("required"), Set.class));
      }
    }
    assertEquals(
        "default", schema.at("/ContractArraysCreate/properties/rows/default/0/name").asText());
  }

  @Test
  void malformedDtoAndAlternateRowsAreRefusedBeforeAResponseCanExposeThem() throws Exception {
    operations.stored.get(1L).rows =
        List.of(
            new Row(
                "name-too-long", new Detail("child", 2, "private"), List.of(), null, "private"));
    MvcResult dto =
        mockMvc
            .perform(get(ENDPOINT + "/1").header("apiKey", fixture.userKey()))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
            .andReturn();
    assertEquals(
        Reason.INVALID_VALUE,
        assertInstanceOf(EmbeddedFieldException.class, dto.getResolvedException()).reason());
    operations.stored.get(1L).rows = DEFAULT_ROWS;
    operations.overrides = Map.of(1L, Map.of("rows", List.of(1)));
    MvcResult alternate =
        mockMvc
            .perform(get(ENDPOINT + "/1").header("apiKey", fixture.userKey()))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
            .andReturn();
    assertEquals(
        Reason.WRONG_TYPE,
        assertInstanceOf(EmbeddedFieldException.class, alternate.getResolvedException()).reason());
    assertEquals(
        "errors.api.v2.unexpected",
        JSON.readTree(alternate.getResponse().getContentAsString()).path("code").asText());
    verifyNoInteractions(operations.audit);
  }

  private MockHttpServletRequestBuilder write(
      MockHttpServletRequestBuilder request, String body, String key) {
    return request.header("apiKey", key).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private JsonNode response(MockHttpServletRequestBuilder request, int expectedStatus)
      throws Exception {
    return JSON.readTree(
        mockMvc
            .perform(request)
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void rejected(
      MockHttpServletRequestBuilder request, String body, String field, Reason reason)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(write(request, body, fixture.userKey()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
            .andReturn();
    DocumentValidationException exception =
        assertInstanceOf(DocumentValidationException.class, result.getResolvedException());
    assertEquals(
        List.of(new DocumentValidationException.Violation(field, reason)),
        exception.getViolations());
    JsonNode error = JSON.readTree(result.getResponse().getContentAsString());
    assertEquals(field, error.at("/invalidParams/0/name").asText());
    assertEquals(reason.code(), error.at("/invalidParams/0/reason").asText());
  }

  private static Set<String> keys(JsonNode object) {
    Set<String> names = new java.util.LinkedHashSet<>();
    object.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static List<Detail> details(Object value) {
    return value == null ? null : ((List<?>) value).stream().map(Detail.class::cast).toList();
  }

  private static Detail detail(Map<String, Object> values) {
    return new Detail((String) values.get("name"), (Integer) values.get("number"), "private");
  }

  private record Detail(String name, Integer number, String privateValue) {}

  private record Row(
      String name, Detail detail, List<Detail> nested, String restricted, String privateValue) {}

  private static final class Item {
    private final Long id;
    private List<Row> rows;
    private String label;

    private Item(Long id, List<Row> rows, String label) {
      this.id = id;
      this.rows = rows;
      this.label = label;
    }
  }

  // The profile keeps the synthetic resource out of every other application's component scan.
  @Configuration(proxyBeanMethods = false)
  @Profile("declared-array-http-contract")
  public static class Resources {
    @Bean
    ArrayOperations declaredArrayOperations() {
      return new ArrayOperations();
    }

    @Bean
    ApiV2ResourceSpec<Item, Long> declaredArrayResource(ArrayOperations operations) {
      return new ApiV2ResourceSpec<>(
          DESCRIPTION,
          operations,
          Long::valueOf,
          "errors.api.v2.invalidRequest",
          "errors.api.v2.invalidRequest",
          Set.of(
              ResourceOperation.LIST,
              ResourceOperation.COUNT,
              ResourceOperation.READ,
              ResourceOperation.CREATE,
              ResourceOperation.BULK_CREATE,
              ResourceOperation.UPDATE),
          Map.of());
    }
  }

  private static final class ArrayOperations implements ResourceOperations<Item, Long> {
    private final Map<Long, Item> stored = new LinkedHashMap<>();
    private final AuditTrailService audit = mock(AuditTrailService.class);
    private Map<Object, Map<String, Object>> overrides = Map.of();
    private int mutations;

    private void resetStore() {
      stored.clear();
      stored.put(1L, new Item(1L, DEFAULT_ROWS, "initial"));
      overrides = Map.of();
      mutations = 0;
      reset(audit);
    }

    @Override
    public ResourcePage<Item> find(ResourceRequest request, User subject) {
      return new ResourcePage<>(List.copyOf(stored.values()), stored.size());
    }

    @Override
    public long count(ResourceRequest request, User subject) {
      return stored.size();
    }

    @Override
    public Optional<Item> findById(Long id, User subject) {
      return Optional.ofNullable(stored.get(id));
    }

    @Override
    public Item create(ParsedDocument document, ApiV2Caller caller) {
      Item item = new Item((long) stored.size() + 1, DEFAULT_ROWS, "initial");
      DESCRIPTION.apply(item, document.values(), WriteOperation.CREATE);
      stored.put(item.id, item);
      mutations++;
      audit.notify(new GenericEvent(caller.actor(), item, AuditAction.CREATE));
      return item;
    }

    @Override
    public List<Item> createMany(List<ParsedDocument> documents, ApiV2Caller caller) {
      return documents.stream().map(document -> create(document, caller)).toList();
    }

    @Override
    public Optional<Item> update(Long id, ParsedDocument document, ApiV2Caller caller) {
      return findById(id, caller.subject())
          .map(
              previous -> {
                Item item = new Item(id, previous.rows, previous.label);
                DESCRIPTION.apply(item, document.values(), WriteOperation.UPDATE);
                stored.put(id, item);
                mutations++;
                audit.notify(new GenericEvent(caller.actor(), item, AuditAction.WRITE));
                return item;
              });
    }

    @Override
    public Set<String> readOverrideFields() {
      return Set.of("rows");
    }

    @Override
    public Map<Object, Map<String, Object>> readOverrides(List<Item> resources, User subject) {
      return overrides;
    }
  }
}

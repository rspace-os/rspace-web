package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.booking.api.v2.BookingConfigurationResourceOperations;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.inventory.api.v2.ApiV2InstrumentResource;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.MessageSourceUtils;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Seeded HTTP fuzz scenarios for booking configurations through the generic REST API v2 routes. */
class ApiV2BookingConfigurationFuzzTest {

  private static final String ENDPOINT = "/api/v2/booking-configurations";
  private static final long CRUD_SEED = 0xB00C_C0DEL;
  private static final long QUERY_SEED = 0x51A7_C0DEL;
  private static final long BULK_SEED = 0xB01C_C0DEL;
  private static final long INVALID_SEED = 0xBAD5_EEDL;
  private static final int CRUD_CASES = 64;
  private static final int QUERY_CASES = 96;
  private static final int BULK_CASES = 48;
  private static final int INVALID_CASES = 128;

  private final ObjectMapper mapper = new ObjectMapper();
  private final InMemoryBookingConfigurationDao dao = new InMemoryBookingConfigurationDao();
  private final User sysadmin = org.mockito.Mockito.mock(User.class);
  private ValidatorFactory validatorFactory;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.when(sysadmin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    validatorFactory = Validation.buildDefaultValidatorFactory();
    BookingConfigurationManager manager =
        new BookingConfigurationManager(dao, validatorFactory.getValidator());
    BookingConfigurationResourceOperations operations =
        new BookingConfigurationResourceOperations(manager);
    ApiV2ResourceSpec<BookingConfiguration, Long> bookingConfigurations =
        new ApiV2ResourceSpec<>(
            ApiV2BookingConfigurationResource.DESCRIPTION,
            operations,
            Long::valueOf,
            "errors.api.v2.bookingConfiguration.create",
            "errors.api.v2.bookingConfiguration.patch");
    ApiV2RelationshipTargetSpec<Instrument, Long> instruments =
        new ApiV2RelationshipTargetSpec<>(
            ApiV2InstrumentResource.DESCRIPTION,
            (id, actor) ->
                id > 0 ? Optional.of(instrument(id, "Instrument " + id)) : Optional.empty());
    ApiV2CrudController controller =
        new ApiV2CrudController(
            new ApiV2ResourceCatalog(List.of(bookingConfigurations), List.of(instruments)));
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(problemAdvice()).build();
  }

  @AfterEach
  void tearDown() {
    validatorFactory.close();
  }

  @Test
  void fuzzesValidCrudAndPolymorphicRelationshipRoundTrips() throws Exception {
    Random random = new Random(CRUD_SEED);
    List<String> zones = ZoneId.getAvailableZoneIds().stream().sorted().toList();

    for (int iteration = 0; iteration < CRUD_CASES; iteration++) {
      String initialZone = zones.get(random.nextInt(zones.size()));
      String updatedZone = zones.get(random.nextInt(zones.size()));
      boolean initialEnabled = random.nextBoolean();
      boolean updatedEnabled = !initialEnabled;
      long initialTarget = 10_000L + iteration * 2L;
      long updatedTarget = initialTarget + 1;

      ObjectNode createBody = mapper.createObjectNode();
      createBody.put("enabled", initialEnabled);
      createBody.put("timeZone", initialZone);
      createBody.putObject("target").put("relationTo", "instruments").put("value", initialTarget);
      JsonNode created =
          json(
              mockMvc
                  .perform(
                      post(ENDPOINT)
                          .requestAttr("user", sysadmin)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(mapper.writeValueAsBytes(createBody)))
                  .andReturn(),
              201,
              iteration,
              CRUD_SEED);
      long id = created.path("id").asLong();
      assertTrue(id > 0, caseMessage(iteration, CRUD_SEED));
      assertEquals(initialEnabled, created.path("enabled").asBoolean());
      assertEquals(initialZone, created.path("timeZone").asText());
      assertEquals("instruments", created.path("target").path("relationTo").asText());
      assertEquals(initialTarget, created.path("target").path("value").asLong());

      int depth = random.nextBoolean() ? 0 : 1;
      JsonNode fetched =
          json(
              mockMvc
                  .perform(
                      get(ENDPOINT + "/" + id)
                          .requestAttr("user", sysadmin)
                          .param("depth", Integer.toString(depth)))
                  .andReturn(),
              200,
              iteration,
              CRUD_SEED);
      assertEquals(id, fetched.path("id").asLong());
      JsonNode fetchedTarget = fetched.path("target").path("value");
      if (depth == 0) {
        assertEquals(initialTarget, fetchedTarget.asLong());
      } else {
        assertEquals(initialTarget, fetchedTarget.path("id").asLong());
        assertEquals("Instrument " + initialTarget, fetchedTarget.path("name").asText());
      }

      ObjectNode patchBody = mapper.createObjectNode();
      patchBody.put("enabled", updatedEnabled);
      patchBody.put("timeZone", updatedZone);
      patchBody.put("target", "IN" + updatedTarget);
      JsonNode patched =
          json(
              mockMvc
                  .perform(
                      patch(ENDPOINT + "/" + id)
                          .requestAttr("user", sysadmin)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(mapper.writeValueAsBytes(patchBody)))
                  .andReturn(),
              200,
              iteration,
              CRUD_SEED);
      assertEquals(id, patched.path("id").asLong());
      assertEquals(updatedEnabled, patched.path("enabled").asBoolean());
      assertEquals(updatedZone, patched.path("timeZone").asText());
      assertEquals(updatedTarget, patched.path("target").path("value").asLong());

      JsonNode removed =
          json(
              mockMvc
                  .perform(delete(ENDPOINT + "/" + id).requestAttr("user", sysadmin))
                  .andReturn(),
              200,
              iteration,
              CRUD_SEED);
      assertEquals(id, removed.path("id").asLong());
      assertFalse(dao.exists(id), caseMessage(iteration, CRUD_SEED));
    }
  }

  @Test
  void appliesFieldsetsPerResourceTypeToExpandedRelationships() throws Exception {
    long targetId = 19_001L;
    long id = createConfiguration(targetId, "UTC", true, 0, QUERY_SEED);

    JsonNode selected =
        json(
            mockMvc
                .perform(
                    get(ENDPOINT + "/" + id)
                        .requestAttr("user", sysadmin)
                        .param("depth", "1")
                        .param("fields[booking-configurations]", "target")
                        .param("fields[instruments]", "id"))
                .andReturn(),
            200,
            0,
            QUERY_SEED);

    assertEquals(2, selected.size());
    assertTrue(selected.has("id"));
    JsonNode instrument = selected.path("target").path("value");
    assertEquals(targetId, instrument.path("id").asLong());
    assertEquals(1, instrument.size());
    assertFalse(instrument.has("name"));

    JsonNode excluded =
        json(
            mockMvc
                .perform(
                    get(ENDPOINT + "/" + id)
                        .requestAttr("user", sysadmin)
                        .param("depth", "1")
                        .param("fields[booking-configurations]", "target")
                        .param("exclude[instruments]", "name"))
                .andReturn(),
            200,
            1,
            QUERY_SEED);
    assertEquals(1, excluded.path("target").path("value").size());
    assertFalse(excluded.path("target").path("value").has("name"));
  }

  @Test
  void fuzzesFilteringSortingPaginationSelectionAndCount() throws Exception {
    List<String> zones = ZoneId.getAvailableZoneIds().stream().sorted().limit(80).toList();
    for (int i = 0; i < zones.size(); i++) {
      createConfiguration(20_000L + i, zones.get(i), i % 2 == 0, i, QUERY_SEED);
    }

    Random random = new Random(QUERY_SEED);
    for (int iteration = 0; iteration < QUERY_CASES; iteration++) {
      boolean enabled = random.nextBoolean();
      boolean descending = random.nextBoolean();
      int limit = 1 + random.nextInt(12);
      int page = 1 + random.nextInt(9);
      String selectedField = random.nextBoolean() ? "timeZone" : "configurationVersion";
      String where = "enabled==" + enabled;

      JsonNode list =
          json(
              mockMvc
                  .perform(
                      get(ENDPOINT)
                          .requestAttr("user", sysadmin)
                          .param("where", where)
                          .param("sort", descending ? "-id" : "id")
                          .param("limit", Integer.toString(limit))
                          .param("page", Integer.toString(page))
                          .param("fields[booking-configurations]", "enabled," + selectedField))
                  .andReturn(),
              200,
              iteration,
              QUERY_SEED);
      JsonNode docs = list.path("docs");
      long expectedTotal = zones.size() / 2L;
      assertEquals(
          expectedTotal, list.path("totalDocs").asLong(), caseMessage(iteration, QUERY_SEED));
      assertTrue(docs.size() <= limit, caseMessage(iteration, QUERY_SEED));
      long previousId = descending ? Long.MAX_VALUE : Long.MIN_VALUE;
      for (JsonNode document : docs) {
        long id = document.path("id").asLong();
        assertEquals(enabled, document.path("enabled").asBoolean());
        assertTrue(document.has("id"), caseMessage(iteration, QUERY_SEED));
        assertTrue(document.has(selectedField), caseMessage(iteration, QUERY_SEED));
        assertEquals(3, document.size(), caseMessage(iteration, QUERY_SEED));
        assertTrue(
            descending ? id < previousId : id > previousId, caseMessage(iteration, QUERY_SEED));
        previousId = id;
      }

      JsonNode count =
          json(
              mockMvc
                  .perform(
                      get(ENDPOINT + "/count").requestAttr("user", sysadmin).param("where", where))
                  .andReturn(),
              200,
              iteration,
              QUERY_SEED);
      assertEquals(
          expectedTotal, count.path("totalDocs").asLong(), caseMessage(iteration, QUERY_SEED));
    }
  }

  @Test
  void fuzzesFilteredBulkUpdatesAndDeletes() throws Exception {
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < BULK_CASES; i++) {
      ids.add(createConfiguration(30_000L + i, "UTC", i % 2 == 0, i, BULK_SEED));
    }
    Random random = new Random(BULK_SEED);

    for (int iteration = 0; iteration < BULK_CASES; iteration++) {
      long id = ids.get(random.nextInt(ids.size()));
      boolean enabled = random.nextBoolean();
      ObjectNode patchBody = mapper.createObjectNode().put("enabled", enabled);
      JsonNode result =
          json(
              mockMvc
                  .perform(
                      patch(ENDPOINT)
                          .requestAttr("user", sysadmin)
                          .param("where", "id==" + id)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(mapper.writeValueAsBytes(patchBody)))
                  .andReturn(),
              200,
              iteration,
              BULK_SEED);
      assertEquals(1, result.path("docs").size(), caseMessage(iteration, BULK_SEED));
      assertEquals(id, result.path("docs").get(0).path("id").asLong());
      assertEquals(enabled, result.path("docs").get(0).path("enabled").asBoolean());

      if (iteration % 3 == 0) {
        JsonNode removed =
            json(
                mockMvc
                    .perform(
                        delete(ENDPOINT).requestAttr("user", sysadmin).param("where", "id==" + id))
                    .andReturn(),
                200,
                iteration,
                BULK_SEED);
        assertEquals(1, removed.path("docs").size(), caseMessage(iteration, BULK_SEED));
        ids.remove(id);
      }
    }

    assertStatus(
        mockMvc
            .perform(
                patch(ENDPOINT)
                    .requestAttr("user", sysadmin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":true}"))
            .andReturn(),
        400,
        0,
        BULK_SEED);
    assertStatus(
        mockMvc.perform(delete(ENDPOINT).requestAttr("user", sysadmin)).andReturn(),
        400,
        1,
        BULK_SEED);

    assertStatus(
        mockMvc
            .perform(
                patch(ENDPOINT)
                    .requestAttr("user", sysadmin)
                    .param("where", "id>0")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"target\":\"IN999999\"}"))
            .andReturn(),
        409,
        2,
        BULK_SEED);
  }

  @Test
  void fuzzesInvalidDocumentsRelationshipsAndUniqueTargets() throws Exception {
    Random random = new Random(INVALID_SEED);
    int initialSize = dao.getAll().size();
    for (int iteration = 0; iteration < INVALID_CASES; iteration++) {
      long target = 40_000L + iteration;
      String body = invalidCreateBody(random, target, iteration);
      assertStatus(
          mockMvc
              .perform(
                  post(ENDPOINT)
                      .requestAttr("user", sysadmin)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body))
              .andReturn(),
          400,
          iteration,
          INVALID_SEED);
      assertEquals(initialSize, dao.getAll().size(), caseMessage(iteration, INVALID_SEED));
    }

    long target = 50_000L;
    createConfiguration(target, "UTC", true, INVALID_CASES, INVALID_SEED);
    assertStatus(
        mockMvc
            .perform(
                post(ENDPOINT)
                    .requestAttr("user", sysadmin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"enabled\":true,\"timeZone\":\"UTC\",\"target\":{"
                            + "\"relationTo\":\"instruments\",\"value\":"
                            + target
                            + "}}"))
            .andReturn(),
        409,
        INVALID_CASES + 1,
        INVALID_SEED);
  }

  @Test
  void fuzzesAuthenticationAndMutationAuthorization() throws Exception {
    User member = org.mockito.Mockito.mock(User.class);
    long id = createConfiguration(60_000L, "UTC", true, 0, INVALID_SEED);

    assertStatus(mockMvc.perform(get(ENDPOINT)).andReturn(), 401, 0, INVALID_SEED);
    assertStatus(
        mockMvc.perform(get(ENDPOINT).requestAttr("user", member)).andReturn(),
        200,
        1,
        INVALID_SEED);
    assertStatus(
        mockMvc.perform(get(ENDPOINT + "/" + id).requestAttr("user", member)).andReturn(),
        200,
        2,
        INVALID_SEED);

    for (int iteration = 0; iteration < 32; iteration++) {
      boolean anonymous = iteration % 2 == 0;
      MockHttpServletRequestBuilder request =
          switch (iteration % 3) {
            case 0 ->
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"enabled\":true,\"timeZone\":\"UTC\",\"target\":{"
                            + "\"relationTo\":\"instruments\",\"value\":"
                            + (61_000L + iteration)
                            + "}}");
            case 1 ->
                patch(ENDPOINT + "/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":false}");
            default -> delete(ENDPOINT + "/" + id);
          };
      if (!anonymous) {
        request.requestAttr("user", member);
      }
      assertStatus(
          mockMvc.perform(request).andReturn(), anonymous ? 401 : 403, iteration, INVALID_SEED);
    }
  }

  @Test
  void fuzzesMalformedQueriesPaginationDepthAndSelections() throws Exception {
    Random random = new Random(INVALID_SEED);
    for (int iteration = 0; iteration < INVALID_CASES; iteration++) {
      MockHttpServletRequestBuilder request = get(ENDPOINT).requestAttr("user", sysadmin);
      switch (random.nextInt(9)) {
        case 0 -> request.param("where", "unknown" + iteration + "==1");
        case 1 -> request.param("where", "(id==" + iteration);
        case 2 -> request.param("sort", (random.nextBoolean() ? "-" : "") + "unknown" + iteration);
        case 3 -> request.param("fields[booking-configurations]", "unknown" + iteration);
        case 4 ->
            request
                .param("fields[booking-configurations]", "enabled")
                .param("exclude[booking-configurations]", "timeZone");
        case 5 -> request.param("page", Integer.toString(-random.nextInt(10)));
        case 6 -> request.param("limit", Integer.toString(101 + random.nextInt(10_000)));
        case 7 -> request.param("depth", Integer.toString(11 + random.nextInt(100)));
        default ->
            request.param(
                "where",
                "id=in=("
                    + IntStream.range(0, 101)
                        .mapToObj(Integer::toString)
                        .collect(Collectors.joining(","))
                    + ")");
      }
      assertStatus(mockMvc.perform(request).andReturn(), 400, iteration, INVALID_SEED);
    }
  }

  @Test
  void rejectsFuzzedBulkSelectionsAboveTheAtomicLimit() throws Exception {
    for (int i = 0; i <= 1000; i++) {
      dao.seed(70_000L + i, 80_000L + i, true, "UTC");
    }

    assertStatus(
        mockMvc
            .perform(delete(ENDPOINT).requestAttr("user", sysadmin).param("where", "enabled==true"))
            .andReturn(),
        422,
        0,
        BULK_SEED);
    assertEquals(1001, dao.getAll().size());
  }

  private String invalidCreateBody(Random random, long target, int iteration) throws Exception {
    ObjectNode valid = mapper.createObjectNode();
    valid.put("enabled", random.nextBoolean());
    valid.put("timeZone", "UTC");
    valid.putObject("target").put("relationTo", "instruments").put("value", target);
    return switch (iteration % 12) {
      case 0 -> "{}";
      case 1 -> valid.remove("timeZone").toString();
      case 2 -> valid.remove("target").toString();
      case 3 -> valid.put("enabled", "not-a-boolean-" + random.nextLong()).toString();
      case 4 -> valid.put("timeZone", random.nextLong()).toString();
      case 5 ->
          valid.put("timeZone", "Invalid/" + Long.toUnsignedString(random.nextLong())).toString();
      case 6 -> valid.putNull("target").toString();
      case 7 -> {
        valid.withObject("target").put("relationTo", "unknown-" + iteration);
        yield valid.toString();
      }
      case 8 -> {
        valid.withObject("target").put("value", "not-a-number");
        yield valid.toString();
      }
      case 9 -> valid.put("unknown-" + Long.toUnsignedString(random.nextLong()), true).toString();
      case 10 -> valid.put("id", random.nextLong()).toString();
      default -> "[]";
    };
  }

  private long createConfiguration(
      long targetId, String timeZone, boolean enabled, int iteration, long seed) throws Exception {
    ObjectNode body = mapper.createObjectNode();
    body.put("enabled", enabled);
    body.put("timeZone", timeZone);
    body.putObject("target").put("relationTo", "instruments").put("value", targetId);
    return json(
            mockMvc
                .perform(
                    post(ENDPOINT)
                        .requestAttr("user", sysadmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body)))
                .andReturn(),
            201,
            iteration,
            seed)
        .path("id")
        .asLong();
  }

  private JsonNode json(MvcResult result, int expectedStatus, int iteration, long seed)
      throws Exception {
    assertStatus(result, expectedStatus, iteration, seed);
    JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
    assertNotNull(body, caseMessage(iteration, seed));
    return body;
  }

  private static void assertStatus(MvcResult result, int expectedStatus, int iteration, long seed) {
    assertEquals(
        expectedStatus,
        result.getResponse().getStatus(),
        () ->
            caseMessage(iteration, seed)
                + ", response="
                + new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
  }

  private static String caseMessage(int iteration, long seed) {
    return "fuzz failure at iteration=" + iteration + ", seed=" + seed;
  }

  private static Instrument instrument(long id, String name) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    instrument.setName(name);
    return instrument;
  }

  private static ApiV2ControllerAdvice problemAdvice() {
    StaticMessageSource source = new StaticMessageSource();
    List.of(
            "errors.api.v2.authenticationRequired",
            "errors.api.v2.forbidden",
            "errors.api.v2.invalidRequest",
            "errors.api.v2.notFound",
            "errors.api.v2.query.syntax",
            "errors.api.v2.query.field",
            "errors.api.v2.query.operator",
            "errors.api.v2.query.value",
            "errors.api.v2.query.complexity",
            "errors.api.v2.select.mode",
            "errors.api.v2.bulk.filter.required",
            "errors.api.v2.bulk.limit",
            "errors.api.v2.bookingConfiguration.create",
            "errors.api.v2.bookingConfiguration.patch",
            "errors.api.v2.bookingConfiguration.target.invalid",
            "errors.api.v2.bookingConfiguration.target.conflict")
        .forEach(key -> source.addMessage(key, Locale.ENGLISH, key));
    source.addMessage("errors.api.pagination.page.min", Locale.ENGLISH, "page");
    source.addMessage("errors.api.pagination.limit.range", Locale.ENGLISH, "limit {0}");
    source.addMessage("errors.api.v2.depth.range", Locale.ENGLISH, "depth {0}");
    source.addMessage("errors.api.v2.where.length", Locale.ENGLISH, "where {0}");
    return new ApiV2ControllerAdvice(new MessageSourceUtils(source));
  }

  /** Local persistence adapter used only by the public HTTP fuzz seam. */
  private static final class InMemoryBookingConfigurationDao implements BookingConfigurationDao {

    private final Map<Long, BookingConfiguration> configurations = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public BookingConfiguration saveAndFlush(BookingConfiguration configuration) {
      if (configuration.getId() == null) {
        configuration.setId(nextId++);
      }
      configurations.put(configuration.getId(), configuration);
      return configuration;
    }

    @Override
    public Optional<BookingConfiguration> findByTarget(BookableTargetReference target) {
      return configurations.values().stream()
          .filter(configuration -> target.equals(configuration.getTarget()))
          .findFirst();
    }

    @Override
    public ISearchResults<BookingConfiguration> getResources(ResourceRequest request) {
      List<BookingConfiguration> all = selected(request);
      int from = Math.min((request.page().number() - 1) * request.page().size(), all.size());
      int to = Math.min(from + request.page().size(), all.size());
      return new SearchResultsImpl<>(
          all.subList(from, to), request.page().number() - 1, all.size(), request.page().size());
    }

    @Override
    public long countResources(ResourceRequest request) {
      return selected(request).size();
    }

    @Override
    public List<BookingConfiguration> getResources(ResourceRequest request, int limit) {
      return selected(request).stream().limit(limit).toList();
    }

    @Override
    public List<BookingConfiguration> getAll() {
      return List.copyOf(configurations.values());
    }

    @Override
    public List<BookingConfiguration> getAllDistinct() {
      return getAll();
    }

    @Override
    public BookingConfiguration get(Long id) {
      return configurations.get(id);
    }

    @Override
    public Optional<BookingConfiguration> getSafeNull(Long id) {
      return Optional.ofNullable(configurations.get(id));
    }

    @Override
    public boolean exists(Long id) {
      return configurations.containsKey(id);
    }

    @Override
    public BookingConfiguration save(BookingConfiguration configuration) {
      return saveAndFlush(configuration);
    }

    @Override
    public void remove(Long id) {
      configurations.remove(id);
    }

    @Override
    public <N> Optional<BookingConfiguration> getBySimpleNaturalId(N naturalId) {
      return Optional.empty();
    }

    @Override
    public BookingConfiguration load(Long id) {
      return get(id);
    }

    @Override
    public Long getCount() {
      return (long) configurations.size();
    }

    void seed(long id, long targetId, boolean enabled, String timeZone) {
      BookingConfiguration configuration = new BookingConfiguration();
      configuration.setId(id);
      configuration.setEnabled(enabled);
      configuration.setTimeZone(timeZone);
      configuration.replaceTarget(
          new BookableTargetReference(
              com.researchspace.model.booking.BookableTargetType.INSTRUMENT, targetId));
      configurations.put(id, configuration);
      nextId = Math.max(nextId, id + 1);
    }

    @Override
    public ISearchResults<BookingConfiguration> search(
        PaginationCriteria<BookingConfiguration> criteria, User searcher) {
      return SearchResultsImpl.emptyResult(criteria);
    }

    private List<BookingConfiguration> selected(ResourceRequest request) {
      List<BookingConfiguration> selected =
          configurations.values().stream()
              .filter(configuration -> matches(configuration, request.filter()))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
      Comparator<BookingConfiguration> comparator = null;
      for (Sort sort : request.sort()) {
        Comparator<BookingConfiguration> next =
            Comparator.comparing(
                configuration -> (Comparable<Object>) value(configuration, sort.field()),
                Comparator.nullsFirst(Comparator.naturalOrder()));
        if (!sort.ascending()) {
          next = next.reversed();
        }
        comparator = comparator == null ? next : comparator.thenComparing(next);
      }
      if (comparator != null) {
        selected.sort(comparator);
      }
      return selected;
    }

    private static boolean matches(
        BookingConfiguration configuration, FilterExpression expression) {
      if (expression == null) {
        return true;
      }
      if (expression instanceof FilterExpression.And and) {
        return and.children().stream().allMatch(child -> matches(configuration, child));
      }
      if (expression instanceof FilterExpression.Or or) {
        return or.children().stream().anyMatch(child -> matches(configuration, child));
      }
      FilterExpression.Comparison comparison = (FilterExpression.Comparison) expression;
      Object actual = value(configuration, comparison.field());
      Object expected = comparison.values().get(0);
      return switch (comparison.operator()) {
        case EQUAL ->
            comparison.wildcard() ? wildcard(actual, expected) : Objects.equals(actual, expected);
        case NOT_EQUAL ->
            comparison.wildcard() ? !wildcard(actual, expected) : !Objects.equals(actual, expected);
        case IN -> comparison.values().contains(actual);
        case NOT_IN -> !comparison.values().contains(actual);
        case GREATER_THAN -> compare(actual, expected) > 0;
        case GREATER_THAN_OR_EQUAL -> compare(actual, expected) >= 0;
        case LESS_THAN -> compare(actual, expected) < 0;
        case LESS_THAN_OR_EQUAL -> compare(actual, expected) <= 0;
        case CONTAINS -> String.valueOf(actual).contains(String.valueOf(expected));
        case LIKE -> String.valueOf(actual).contains(String.valueOf(expected));
        case EXISTS -> (actual != null) == Boolean.TRUE.equals(expected);
      };
    }

    private static Object value(BookingConfiguration configuration, String field) {
      return switch (field) {
        case "id" -> configuration.getId();
        case "enabled" -> configuration.isEnabled();
        case "timeZone" -> configuration.getTimeZone();
        case "configurationVersion" -> configuration.getConfigurationVersion();
        case "target.value" -> configuration.getTarget().id();
        case "target.relationTo" -> configuration.getTarget().type();
        default -> throw new IllegalArgumentException("Unsupported fuzz field " + field);
      };
    }

    @SuppressWarnings("unchecked")
    private static int compare(Object left, Object right) {
      return ((Comparable<Object>) left).compareTo(right);
    }

    private static boolean wildcard(Object actual, Object pattern) {
      String regex =
          java.util.regex.Pattern.quote(String.valueOf(pattern)).replace("*", "\\E.*\\Q");
      return String.valueOf(actual).matches(regex);
    }
  }
}

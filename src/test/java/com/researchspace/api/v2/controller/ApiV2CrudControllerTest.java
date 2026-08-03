package com.researchspace.api.v2.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.maintenance.api.v2.MaintenanceResourceOperations;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.MessageSourceUtils;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiV2CrudControllerTest {

  private static final String ENDPOINT = "/api/v2/maintenances";
  private final MaintenanceManager maintenanceManager = mock(MaintenanceManager.class);
  private final User user = mock(User.class);
  private ApiV2ResourceSpec<ScheduledMaintenance, Long> maintenance;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    when(user.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    maintenance =
        new ApiV2ResourceSpec<>(
            ApiV2MaintenanceResource.DESCRIPTION,
            new MaintenanceResourceOperations(maintenanceManager),
            Long::valueOf,
            "errors.api.v2.invalidRequest",
            "errors.api.v2.maintenance.patch");
    ApiV2CrudController controller =
        new ApiV2CrudController(new ApiV2ResourceCatalog(List.of(maintenance)));
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(problemAdvice()).build();
  }

  @Test
  void appliesDefaultPaginationWhenNoMaintenanceIsScheduled() throws Exception {
    stubPage(Collections.emptyList(), 0, 1, 20);

    mockMvc
        .perform(get(ENDPOINT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs").isEmpty())
        .andExpect(jsonPath("$.totalDocs").value(0))
        .andExpect(jsonPath("$.limit").value(20))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pagingCounter").value(1))
        .andExpect(jsonPath("$.totalPages").value(0))
        .andExpect(jsonPath("$.hasPrevPage").value(false))
        .andExpect(jsonPath("$.hasNextPage").value(false))
        .andExpect(jsonPath("$.prevPage").isEmpty())
        .andExpect(jsonPath("$.nextPage").isEmpty());
  }

  @Test
  void returnsMethodNotAllowedForAnOperationTheResourceDoesNotExpose() throws Exception {
    ApiV2ResourceSpec<ScheduledMaintenance, Long> readOnly =
        new ApiV2ResourceSpec<>(
            ApiV2MaintenanceResource.DESCRIPTION,
            new MaintenanceResourceOperations(maintenanceManager),
            Long::valueOf,
            "errors.api.v2.invalidRequest",
            "errors.api.v2.maintenance.patch",
            EnumSet.of(ResourceOperation.LIST, ResourceOperation.COUNT, ResourceOperation.READ),
            Map.of());
    MockMvc readOnlyMvc =
        MockMvcBuilders.standaloneSetup(
                new ApiV2CrudController(new ApiV2ResourceCatalog(List.of(readOnly))))
            .setControllerAdvice(problemAdvice())
            .build();

    readOnlyMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void pagesAllFutureMaintenancesThroughTheEnvelope() throws Exception {
    when(maintenanceManager.getResources(any(ResourceRequest.class)))
        .thenReturn(
            new SearchResultsImpl<>(
                List.of(
                    futureMaintenance(2, "Planned database upgrade"),
                    futureMaintenance(26, "Second window")),
                0,
                3,
                2),
            new SearchResultsImpl<>(List.of(futureMaintenance(50, "Third window")), 1, 3, 2));

    mockMvc
        .perform(get(ENDPOINT).param("limit", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs.length()").value(2))
        .andExpect(jsonPath("$.docs[0].startDate", isoDateTime()))
        .andExpect(jsonPath("$.docs[0].endDate", isoDateTime()))
        .andExpect(jsonPath("$.docs[0].stopUserLoginDate", isoDateTime()))
        .andExpect(jsonPath("$.docs[0].message").value("Planned database upgrade"))
        .andExpect(jsonPath("$.docs[0].canUserLoginNow").doesNotExist())
        .andExpect(jsonPath("$.docs[0].activeNow").doesNotExist())
        .andExpect(jsonPath("$.totalDocs").value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.hasPrevPage").value(false))
        .andExpect(jsonPath("$.hasNextPage").value(true))
        .andExpect(jsonPath("$.nextPage").value(2));

    mockMvc
        .perform(get(ENDPOINT).param("limit", "2").param("page", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs.length()").value(1))
        .andExpect(jsonPath("$.docs[0].message").value("Third window"))
        .andExpect(jsonPath("$.totalDocs").value(3))
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.hasPrevPage").value(true))
        .andExpect(jsonPath("$.prevPage").value(1))
        .andExpect(jsonPath("$.hasNextPage").value(false));
  }

  @Test
  void returnsAnEmptyPageWhenPagingPastTheEnd() throws Exception {
    stubPage(Collections.emptyList(), 1, 999, 20);

    mockMvc
        .perform(get(ENDPOINT).param("page", "999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs").isEmpty())
        .andExpect(jsonPath("$.totalDocs").value(1));
    verify(maintenanceManager, never()).getAllFutureMaintenances();
  }

  @Test
  void rejectsInvalidOrNonNumericPaginationWithProblemDetails() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("page", "0").param("limit", "101"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get(ENDPOINT).param("page", "not-a-number")).andExpect(status().isBadRequest());
  }

  @Test
  void returnsProtocolCorrectMethodAndAcceptErrors() throws Exception {
    stubPage(Collections.emptyList(), 0, 1, 20);
    mockMvc
        .perform(put(ENDPOINT))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")));
    mockMvc
        .perform(get(ENDPOINT).accept(MediaType.APPLICATION_XML))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void doesNotRequireAnAuthenticatedUserRequestAttribute() throws Exception {
    stubPage(Collections.emptyList(), 0, 1, 20);
    mockMvc.perform(get(ENDPOINT)).andExpect(status().isOk());
  }

  @Test
  void passesRsqlAndSortToTheManager() throws Exception {
    stubPage(Collections.emptyList(), 0, 1, 20);

    mockMvc
        .perform(
            get(ENDPOINT).param("where", "message==*upgrade*").param("sort", "-startDate,message"))
        .andExpect(status().isOk());

    ArgumentCaptor<ResourceRequest> request = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(maintenanceManager).getResources(request.capture());
    assertEquals(
        List.of(new Sort("startDate", false), new Sort("message", true), new Sort("id", true)),
        request.getValue().sort());
    assertEquals(
        new FilterExpression.Comparison("message", Operator.EQUAL, List.of("*upgrade*"), true),
        assertAnonymousMaintenanceConstraint(request.getValue().filter()));
  }

  @Test
  void selectsRequestedFieldsWhileAlwaysReturningId() throws Exception {
    ScheduledMaintenance maintenance = futureMaintenance(2, "Planned database upgrade");
    maintenance.setId(42L);
    stubPage(List.of(maintenance), 1, 1, 20);

    mockMvc
        .perform(get(ENDPOINT).param("fields[maintenances]", "message"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs[0].id").value(42))
        .andExpect(jsonPath("$.docs[0].message").value("Planned database upgrade"))
        .andExpect(jsonPath("$.docs[0].startDate").doesNotExist());
  }

  @Test
  void countsAndFindsFutureMaintenance() throws Exception {
    ScheduledMaintenance maintenance = futureMaintenance(2, "Planned database upgrade");
    maintenance.setId(42L);
    when(maintenanceManager.countResources(any(ResourceRequest.class))).thenReturn(3L);
    when(maintenanceManager.getResources(any(ResourceRequest.class)))
        .thenReturn(new SearchResultsImpl<>(List.of(maintenance), 0, 1, 1));

    mockMvc
        .perform(get(ENDPOINT + "/count").param("where", "message==*upgrade*"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(3));
    mockMvc
        .perform(get(ENDPOINT + "/42").param("fields[maintenances]", "message"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(42))
        .andExpect(jsonPath("$.message").value("Planned database upgrade"))
        .andExpect(jsonPath("$.endDate").doesNotExist());
  }

  @Test
  void returnsNotFoundForExpiredOrUnknownMaintenance() throws Exception {
    when(maintenanceManager.getResources(any(ResourceRequest.class)))
        .thenReturn(new SearchResultsImpl<>(List.of(), 0, 0, 1));

    mockMvc
        .perform(get(ENDPOINT + "/42"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("errors.api.v2.notFound"));
  }

  private static FilterExpression assertAnonymousMaintenanceConstraint(FilterExpression filter) {
    FilterExpression.And constrained = assertInstanceOf(FilterExpression.And.class, filter);
    FilterExpression.Comparison endDate =
        assertInstanceOf(FilterExpression.Comparison.class, constrained.children().get(0));
    assertEquals("endDate", endDate.field());
    assertEquals(Operator.GREATER_THAN, endDate.operator());
    assertInstanceOf(Date.class, endDate.values().get(0));
    return constrained.children().get(1);
  }

  @Test
  void rejectsUnknownResourcesAndInvalidIds() throws Exception {
    mockMvc
        .perform(get("/api/v2/not-registered"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("errors.api.v2.notFound"));

    mockMvc
        .perform(get(ENDPOINT + "/not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.query.value"));
  }

  @Test
  void concreteControllerCanOverrideAnAutomaticRoute() throws Exception {
    MockMvc mockMvcWithOverride =
        MockMvcBuilders.standaloneSetup(
                new ApiV2CrudController(new ApiV2ResourceCatalog(List.of(maintenance))),
                new MaintenanceOverrideController())
            .setControllerAdvice(problemAdvice())
            .build();

    mockMvcWithOverride
        .perform(get(ENDPOINT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("manual"));
  }

  @Test
  void rejectsDuplicateResourceSpecs() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ApiV2ResourceCatalog(List.of(maintenance, maintenance)));

    assertEquals("Duplicate resource name maintenances", exception.getMessage());
  }

  @Test
  void rejectsInvalidSelectionDepthAndWhere() throws Exception {
    stubPage(List.of(futureMaintenance(2, "Window")), 1, 1, 20);

    mockMvc
        .perform(
            get(ENDPOINT)
                .param("fields[maintenances]", "message")
                .param("exclude[maintenances]", "startDate"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.query.value"));

    mockMvc
        .perform(get(ENDPOINT).param("depth", "11"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));

    // .param() leaves getQueryString() null, so the controller's raw-where advice cannot see this
    // and the parser's decoded check rejects it from inside the handler instead. In production a
    // real query string is always present, so the raw advice rejects first; either way the caller
    // gets 400 with the same message key, which ApiV2ErrorContractMVCIT asserts end to end.
    mockMvc
        .perform(get(ENDPOINT).param("where", "x".repeat(4097)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.where.length"));

    // A real query string does reach the raw advice, which rejects before binding.
    mockMvc
        .perform(get(ENDPOINT + "?where=" + "%61".repeat(1366)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.where.length"));
  }

  @Test
  void rejectsMalformedRepeatedAndLegacyFieldsets() throws Exception {
    stubPage(List.of(futureMaintenance(2, "Window")), 1, 1, 20);

    mockMvc
        .perform(get(ENDPOINT).param("fields[maintenances]", "message", "startDate"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.query.value"));

    mockMvc
        .perform(get(ENDPOINT).param("fields[maintenances][nested]", "message"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(get(ENDPOINT).param("select[message]", "true"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.query.field"));
  }

  @Test
  void createsUpdatesAndDeletesMaintenanceDocuments() throws Exception {
    ScheduledMaintenance created = futureMaintenance(2, "Created");
    created.setId(42L);
    when(maintenanceManager.createResource(any(ScheduledMaintenance.class), eq(user)))
        .thenReturn(created);

    mockMvc
        .perform(
            post(ENDPOINT)
                .requestAttr("user", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "startDate": "2026-08-01T10:00:00Z",
                      "endDate": "2026-08-01T11:00:00Z",
                      "message": "Created"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(42))
        .andExpect(jsonPath("$.message").value("Created"));

    mockMvc
        .perform(
            post(ENDPOINT)
                .requestAttr("user", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2026-08-01T10:00:00Z\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
    mockMvc
        .perform(
            post(ENDPOINT)
                .requestAttr("user", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "startDate": 42,
                      "endDate": "2026-08-01T11:00:00Z"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));

    ScheduledMaintenance updated = futureMaintenance(3, null);
    updated.setId(42L);
    when(maintenanceManager.updateResource(eq(42L), any(ParsedDocument.class), eq(user)))
        .thenReturn(Optional.of(updated));
    mockMvc
        .perform(
            patch(ENDPOINT + "/42")
                .requestAttr("user", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(42))
        .andExpect(jsonPath("$.message").isEmpty());

    when(maintenanceManager.removeResource(42L, user)).thenReturn(Optional.of(updated));
    mockMvc
        .perform(delete(ENDPOINT + "/42").requestAttr("user", user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(42));
  }

  @Test
  void bulkCreatesMaintenanceDocumentsInAPayloadEnvelope() throws Exception {
    ScheduledMaintenance first = futureMaintenance(2, "First");
    first.setId(41L);
    ScheduledMaintenance second = futureMaintenance(4, "Second");
    second.setId(42L);
    when(maintenanceManager.createResources(any(), eq(user))).thenReturn(List.of(first, second));

    mockMvc
        .perform(
            post(ENDPOINT + "/bulk")
                .requestAttr("user", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"docs":[
                      {
                        "startDate":"2026-08-01T10:00:00Z",
                        "endDate":"2026-08-01T11:00:00Z",
                        "message":"First"
                      },
                      {
                        "startDate":"2026-08-02T10:00:00Z",
                        "endDate":"2026-08-02T11:00:00Z",
                        "message":"Second"
                      }
                    ]}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.docs[0].id").value(41))
        .andExpect(jsonPath("$.docs[1].id").value(42))
        .andExpect(jsonPath("$.errors").isEmpty());

    mockMvc
        .perform(
            post(ENDPOINT + "/bulk")
                .requestAttr("user", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"docs\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
  }

  @Test
  void bulkOperationsRequireAFilterAndReturnPayloadEnvelope() throws Exception {
    ScheduledMaintenance maintenance = futureMaintenance(2, "Updated");
    maintenance.setId(42L);
    when(maintenanceManager.updateResources(
            any(ResourceRequest.class), any(ParsedDocument.class), eq(user)))
        .thenReturn(List.of(maintenance));

    mockMvc
        .perform(
            patch(ENDPOINT)
                .param("where", "id==42")
                .requestAttr("user", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Updated\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs[0].id").value(42))
        .andExpect(jsonPath("$.errors").isEmpty());

    mockMvc
        .perform(delete(ENDPOINT).requestAttr("user", user).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.bulk.filter.required"));
  }

  private void stubPage(
      List<ScheduledMaintenance> results, long total, int page, int resultsPerPage) {
    when(maintenanceManager.getResources(any(ResourceRequest.class)))
        .thenReturn(new SearchResultsImpl<>(results, page - 1, total, resultsPerPage));
  }

  private static ApiV2ControllerAdvice problemAdvice() {
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage(
        "errors.api.pagination.page.min", Locale.ENGLISH, "Page must be 1 or greater.");
    source.addMessage(
        "errors.api.pagination.limit.range", Locale.ENGLISH, "Limit must be between 1 and {0}.");
    source.addMessage(
        "errors.api.v2.invalidRequest", Locale.ENGLISH, "The request contains an invalid value.");
    source.addMessage(
        "errors.api.v2.depth.range", Locale.ENGLISH, "Depth must be between 0 and {0}.");
    source.addMessage(
        "errors.api.v2.select.mode",
        Locale.ENGLISH,
        "Select cannot mix inclusive and exclusive fields.");
    source.addMessage(
        "errors.api.v2.query.field", Locale.ENGLISH, "The query contains an unsupported field.");
    source.addMessage(
        "errors.api.v2.query.value", Locale.ENGLISH, "The query contains an invalid value.");
    source.addMessage(
        "errors.api.v2.query.complexity", Locale.ENGLISH, "The query is too complex.");
    source.addMessage(
        "errors.api.v2.methodNotAllowed", Locale.ENGLISH, "This HTTP method is not supported.");
    source.addMessage(
        "errors.api.v2.notAcceptable",
        Locale.ENGLISH,
        "The requested response content type is not available.");
    source.addMessage(
        "errors.api.v2.notFound", Locale.ENGLISH, "The requested resource was not found.");
    source.addMessage(
        "errors.api.v2.where.length", Locale.ENGLISH, "Where must not exceed {0} characters.");
    source.addMessage(
        "errors.api.v2.bulk.filter.required", Locale.ENGLISH, "Bulk operations require a filter.");
    return new ApiV2ControllerAdvice(new MessageSourceUtils(source));
  }

  private static ScheduledMaintenance futureMaintenance(int hoursFromNow, String message) {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.HOUR_OF_DAY, hoursFromNow);
    Date startDate = calendar.getTime();
    calendar.add(Calendar.HOUR_OF_DAY, 1);
    ScheduledMaintenance maintenance = new ScheduledMaintenance(startDate, calendar.getTime());
    maintenance.setMessage(message);
    return maintenance;
  }

  private static Matcher<String> isoDateTime() {
    return allOf(startsWith("20"), containsString("T"), endsWith("Z"));
  }

  /**
   * Stands in for a collection using the escape hatch: a concrete route that outranks the generic
   * {@code /{resource}} one.
   *
   * <p>{@code @Profile} names a profile that is never active, purely to keep this out of component
   * scanning. {@code @RestController} is a {@code @Component}, so the application's {@code
   * com.researchspace} scan picked this test class up from the classpath and its concrete {@code
   * /api/v2/maintenances} mapping outranked the description-backed generic route, silently turning
   * a public production endpoint into a 401 in every Spring integration test. {@code
   * standaloneSetup} registers the instance directly and never evaluates profiles, so detection
   * here is unaffected.
   */
  @Profile("never-active-outside-standalone-mockmvc")
  @RestController
  private static class MaintenanceOverrideController {

    @GetMapping(ENDPOINT)
    Map<String, String> list() {
      return Map.of("source", "manual");
    }
  }
}

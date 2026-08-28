package com.researchspace.api.v2.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.maintenance.api.v2.MaintenanceResourceOperations;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldCatalogPage;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiV2CrudControllerTest {

  private static final String ENDPOINT = "/api/v2/maintenances";
  private final MaintenanceManager maintenanceManager = mock(MaintenanceManager.class);
  private final User user = mock(User.class);
  private final LocalValidatorFactoryBean validator = jsonValidator();
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
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(problemAdvice())
            .setValidator(validator)
            .build();
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
            .setValidator(validator)
            .build();

    readOnlyMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void pagesAllFutureMaintenancesThroughTheEnvelope() throws Exception {
    ScheduledMaintenance first = futureMaintenance(2, "Planned database upgrade");
    first.setId(1L);
    ScheduledMaintenance second = futureMaintenance(26, "Second window");
    second.setId(2L);
    ScheduledMaintenance third = futureMaintenance(50, "Third window");
    third.setId(3L);
    when(maintenanceManager.getResources(any(ResourceRequest.class), any()))
        .thenReturn(
            new ResourcePage<>(List.of(first, second), 3), new ResourcePage<>(List.of(third), 3));

    mockMvc
        .perform(get(ENDPOINT).param("limit", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs.length()").value(2))
        .andExpect(jsonPath("$.docs[0].startDate", isoDateTime()))
        .andExpect(jsonPath("$.docs[0].endDate", isoDateTime()))
        .andExpect(jsonPath("$.docs[0].stopUserLoginDate", isoDateTime()))
        .andExpect(jsonPath("$.docs[0].message").value("Planned database upgrade"))
        .andExpect(jsonPath("$.docs[0].canUserLoginNow").value(true))
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
        .perform(get(ENDPOINT).param("limit", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Limit must be 1 or greater."));
    mockMvc
        .perform(get(ENDPOINT).param("limit", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Limit must not exceed 100."));
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
  void runtimeFieldResponsesCannotBeStored() throws Exception {
    ApiV2ResourceSpec<ScheduledMaintenance, Long> withRuntimeFields =
        new ApiV2ResourceSpec<>(
            maintenance.description(),
            maintenance.operations(),
            maintenance.idParser(),
            maintenance.createErrorKey(),
            maintenance.updateErrorKey(),
            maintenance.exposedOperations(),
            maintenance.operationDocumentation(),
            maintenance.errorMappings(),
            maintenance.mutationLimits(),
            List.of(new EmptyRuntimeFields()));
    MockMvc runtimeMvc =
        MockMvcBuilders.standaloneSetup(
                new ApiV2CrudController(new ApiV2ResourceCatalog(List.of(withRuntimeFields))))
            .setControllerAdvice(problemAdvice())
            .setValidator(validator)
            .build();

    runtimeMvc
        .perform(get(ENDPOINT + "/fields/customFields"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CACHE_CONTROL,
                    allOf(containsString("no-store"), containsString("private"))));
  }

  private static final class EmptyRuntimeFields
      implements RuntimeCollectionFields<ScheduledMaintenance> {

    @Override
    public String namespace() {
      return "customFields";
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
        List<ScheduledMaintenance> resources, Set<String> fieldIds, User actor) {
      return Map.of();
    }
  }

  @Test
  void passesRsqlAndSortToTheManager() throws Exception {
    stubPage(Collections.emptyList(), 0, 1, 20);

    mockMvc
        .perform(
            get(ENDPOINT).param("where", "message==*upgrade*").param("sort", "-startDate,message"))
        .andExpect(status().isOk());

    ArgumentCaptor<ResourceRequest> request = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(maintenanceManager).getResources(request.capture(), any());
    assertEquals(
        List.of(new Sort("startDate", false), new Sort("message", true), new Sort("id", true)),
        request.getValue().sort());
    assertEquals(
        new FilterExpression.Comparison("message", Operator.EQUAL, List.of("*upgrade*"), true),
        request.getValue().filter());
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
    when(maintenanceManager.countResources(any(ResourceRequest.class), any())).thenReturn(3L);
    when(maintenanceManager.getResource(42L, null)).thenReturn(Optional.of(maintenance));

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
    when(maintenanceManager.getResources(any(ResourceRequest.class), any()))
        .thenReturn(new ResourcePage<>(List.of(), 0));

    mockMvc
        .perform(get(ENDPOINT + "/42"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("errors.api.v2.notFound"));
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
            .setValidator(validator)
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
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"))
        .andExpect(jsonPath("$.detail").value("Depth must not exceed 10."));

    mockMvc
        .perform(get(ENDPOINT).param("depth", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Depth must be 0 or greater."));

    // .param() leaves getQueryString() null, so the controller's raw-where advice cannot see this
    // and the parser's decoded check rejects it from inside the handler instead. In production a
    // real query string is always present, so the raw advice rejects first; either way the caller
    // gets 400 with the same message key, which ApiV2ErrorContractMVCIT asserts end to end.
    mockMvc
        .perform(
            get(ENDPOINT).param("where", "x".repeat(CollectionQueryLimits.MAX_WHERE_LENGTH + 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.where.length"));

    // A real query string does reach the raw advice, which rejects before binding.
    String oversizedEncodedWhere = "%61".repeat(CollectionQueryLimits.MAX_WHERE_LENGTH / 3 + 1);
    mockMvc
        .perform(get(ENDPOINT + "?where=" + oversizedEncodedWhere))
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
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user))
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
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2026-08-01T10:00:00Z\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
    mockMvc
        .perform(
            post(ENDPOINT)
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user))
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
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(42))
        .andExpect(jsonPath("$.message").isEmpty());

    when(maintenanceManager.removeResource(42L, user)).thenReturn(Optional.of(updated));
    mockMvc
        .perform(
            delete(ENDPOINT + "/42")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user)))
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
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user))
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
        .andExpect(jsonPath("$.errors").doesNotExist());

    mockMvc
        .perform(
            post(ENDPOINT + "/bulk")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user))
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
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Updated\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs[0].id").value(42))
        .andExpect(jsonPath("$.errors").doesNotExist());

    mockMvc
        .perform(
            delete(ENDPOINT)
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.bulk.filter.required"));
  }

  @Test
  void bulkFiltersResolveRuntimeFieldsForTheSubjectRatherThanTheActor() throws Exception {
    User subject = mock(User.class);
    User actor = mock(User.class);
    when(subject.getId()).thenReturn(73L);
    when(actor.getId()).thenReturn(91L);
    when(subject.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    CapturingRuntimeFields runtimeFields = new CapturingRuntimeFields();
    ApiV2ResourceSpec<ScheduledMaintenance, Long> withRuntimeFields =
        new ApiV2ResourceSpec<>(
            ApiV2MaintenanceResource.DESCRIPTION,
            new MaintenanceResourceOperations(maintenanceManager),
            Long::valueOf,
            "errors.api.v2.invalidRequest",
            "errors.api.v2.maintenance.patch",
            EnumSet.allOf(ResourceOperation.class),
            Map.of(),
            Map.of(),
            CollectionMutationLimits.DEFAULT,
            List.of(runtimeFields));
    MockMvc runtimeMvc =
        MockMvcBuilders.standaloneSetup(
                new ApiV2CrudController(new ApiV2ResourceCatalog(List.of(withRuntimeFields))))
            .setControllerAdvice(problemAdvice())
            .setValidator(validator)
            .build();
    ApiV2Caller caller = new ApiV2Caller(subject, actor);
    when(maintenanceManager.updateResources(
            any(ResourceRequest.class), any(ParsedDocument.class), eq(subject)))
        .thenReturn(List.of());
    when(maintenanceManager.removeResources(any(ResourceRequest.class), eq(subject)))
        .thenReturn(List.of());

    runtimeMvc
        .perform(
            patch(ENDPOINT)
                .param("where", "customFields.SF1==mine")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Updated\"}"))
        .andExpect(status().isOk());
    runtimeMvc
        .perform(
            delete(ENDPOINT)
                .param("where", "customFields.SF1==mine")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller))
        .andExpect(status().isOk());

    assertEquals(List.of(subject, subject), runtimeFields.resolvedBy);
  }

  private static final class CapturingRuntimeFields
      implements RuntimeCollectionFields<ScheduledMaintenance> {

    private final List<User> resolvedBy = new ArrayList<>();

    @Override
    public String namespace() {
      return "customFields";
    }

    @Override
    public RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query) {
      return RuntimeFieldCatalogPage.empty();
    }

    @Override
    public Optional<ResolvedRuntimeField> resolve(String selector, User actor) {
      resolvedBy.add(actor);
      if (!"customFields.SF1".equals(selector)) {
        return Optional.empty();
      }
      RuntimeFieldDefinition definition =
          new RuntimeFieldDefinition(
              "SF1",
              selector,
              "Owner",
              RuntimeFieldValueType.TEXT,
              "maintenance",
              "Maintenance",
              List.of());
      return Optional.of(
          new ResolvedRuntimeField(
              definition,
              new RuntimeFieldBinding(ScheduledMaintenance.class, "id", "message", Map.of())));
    }

    @Override
    public Map<Object, Map<String, Object>> values(
        List<ScheduledMaintenance> resources, Set<String> fieldIds, User actor) {
      return Map.of();
    }
  }

  private void stubPage(
      List<ScheduledMaintenance> results, long total, int page, int resultsPerPage) {
    when(maintenanceManager.getResources(any(ResourceRequest.class), any()))
        .thenReturn(new ResourcePage<>(results, total));
  }

  private static ApiV2ControllerAdvice problemAdvice() {
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage(
        "errors.api.pagination.page.min", Locale.ENGLISH, "Page must be 1 or greater.");
    source.addMessage(
        "errors.api.pagination.limit.min", Locale.ENGLISH, "Limit must be 1 or greater.");
    source.addMessage(
        "errors.api.pagination.limit.max", Locale.ENGLISH, "Limit must not exceed {value}.");
    source.addMessage(
        "errors.api.v2.invalidRequest", Locale.ENGLISH, "The request contains an invalid value.");
    source.addMessage(
        "errors.api.v2.depth.range", Locale.ENGLISH, "Depth must be between 0 and {0}.");
    source.addMessage("errors.api.v2.depth.min", Locale.ENGLISH, "Depth must be 0 or greater.");
    source.addMessage("errors.api.v2.depth.max", Locale.ENGLISH, "Depth must not exceed {value}.");
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

  private static LocalValidatorFactoryBean jsonValidator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(new JsonMessageSource());
    validator.afterPropertiesSet();
    return validator;
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

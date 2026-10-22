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
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.researchspace.api.v2.json.ApiV2JsonMappers;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceAccessSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.maintenance.api.v2.MaintenanceResourceOperations;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
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
import com.researchspace.model.collection.Sort;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import com.researchspace.service.resourceaccess.ReplaceResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessCallerCapabilities;
import com.researchspace.service.resourceaccess.ResourceAccessCallerDocument;
import com.researchspace.service.resourceaccess.ResourceAccessDirectoryManager;
import com.researchspace.service.resourceaccess.ResourceAccessDocument;
import com.researchspace.service.resourceaccess.ResourceAccessGrant;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import com.researchspace.webapp.config.WebConfig.YamlJackson2HttpMessageConverter;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

class ApiV2CrudControllerTest {

  private static final String ENDPOINT = "/api/v2/maintenances";
  private static final String ACCESS_ENDPOINT = ENDPOINT + "/1/access";
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
            .setMessageConverters(bodyConverter(), new YamlJackson2HttpMessageConverter())
            .setControllerAdvice(problemAdvice())
            .setValidator(validator)
            .build();
  }

  @Test
  void appliesDefaultPaginationWhenNoMaintenanceIsScheduled() throws Exception {
    stubPage(Collections.emptyList(), 0);

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
    stubPage(Collections.emptyList(), 0);
    mockMvc
        .perform(put(ENDPOINT))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")));
    mockMvc
        .perform(get(ENDPOINT).accept(MediaType.APPLICATION_XML))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void rejectsYamlCrudRequestBodies() throws Exception {
    registerPreHandlerProblems(mockMvc);
    for (var request :
        List.of(
            post(ENDPOINT), post(ENDPOINT + "/bulk"), patch(ENDPOINT + "/1"), patch(ENDPOINT))) {
      mockMvc
          .perform(request.contentType("application/x-yaml").content("message: maintenance"))
          .andExpect(status().isUnsupportedMediaType())
          .andExpect(jsonPath("$.code").value("errors.api.v2.unsupportedMediaType"));
    }
    verifyNoInteractions(maintenanceManager);
  }

  @Test
  void rejectsYamlAccessReplacementBodies() throws Exception {
    AccessMvc fixture = accessMvc();
    registerPreHandlerProblems(fixture.mockMvc());

    fixture
        .mockMvc()
        .perform(put(ACCESS_ENDPOINT).contentType("application/x-yaml").content("assignments: []"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("errors.api.v2.unsupportedMediaType"));
    verify(fixture.accessManager(), never()).replace(any(), any(), any(), any());
  }

  @Test
  void mapsMalformedAccessAssignmentsToFieldViolations() throws Exception {
    AccessMvc fixture = accessMvc();
    List<InvalidAccessBody> invalidBodies =
        List.of(
            new InvalidAccessBody("{}", "assignments", "required"),
            new InvalidAccessBody("{\"assignments\":null}", "assignments", "nullNotAllowed"),
            new InvalidAccessBody("{\"assignments\":[null]}", "assignments[0]", "nullNotAllowed"),
            new InvalidAccessBody("{\"assignments\":[],\"extra\":true}", "extra", "unknownField"),
            new InvalidAccessBody(
                "{\"assignments\":[{\"granteeKey\":\"user:1\",\"role\":\"Editor\",\"extra\":true}]}",
                "assignments[0].extra",
                "unknownField"),
            new InvalidAccessBody(
                "{\"assignments\":[{\"granteeKey\":\"user:1\"}]}",
                "assignments[0].role",
                "required"),
            new InvalidAccessBody(
                "{\"assignments\":[{\"granteeKey\":\"user:1\",\"role\":1}]}",
                "assignments[0].role",
                "wrongType"),
            new InvalidAccessBody(
                "{\"assignments\":[{\"granteeKey\":\"user:1\",\"role\":true}]}",
                "assignments[0].role",
                "wrongType"),
            new InvalidAccessBody(
                "{\"assignments\":[{\"granteeKey\":\" \",\"role\":\"Editor\"}]}",
                "assignments[0]",
                "invalidValue"));

    for (InvalidAccessBody invalidBody : invalidBodies) {
      fixture
          .mockMvc()
          .perform(
              put(ACCESS_ENDPOINT)
                  .header(HttpHeaders.IF_MATCH, "\"1\"")
                  .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, fixture.caller())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidBody.json()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"))
          .andExpect(jsonPath("$.invalidParams[0].name").value(invalidBody.field()))
          .andExpect(jsonPath("$.invalidParams[0].reason").value(invalidBody.reason()));
    }
  }

  @Test
  void validatesEveryOversizedAccessRowBeforeApplyingTheNamedAssignmentLimit() throws Exception {
    AccessMvc fixture = accessMvc();

    fixture
        .mockMvc()
        .perform(
            put(ACCESS_ENDPOINT)
                .header(HttpHeaders.IF_MATCH, "\"1\"")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, fixture.caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignmentsWithInvalidFinalRole(ResourceAccessController.MAX_ASSIGNMENTS)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"))
        .andExpect(
            jsonPath("$.invalidParams[0].name")
                .value("assignments[" + ResourceAccessController.MAX_ASSIGNMENTS + "].role"))
        .andExpect(jsonPath("$.invalidParams[0].reason").value("wrongType"));
    verify(fixture.accessManager(), never()).replace(any(), any(), any(), any());
  }

  @Test
  void permitsOneAudienceAssignmentInAdditionToOneHundredNamedAssignments() throws Exception {
    AccessMvc fixture = accessMvc();

    fixture
        .mockMvc()
        .perform(
            put(ACCESS_ENDPOINT)
                .header(HttpHeaders.IF_MATCH, "\"1\"")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, fixture.caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignmentsJson(ResourceAccessController.MAX_ASSIGNMENTS, true)))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));

    ArgumentCaptor<ReplaceResourceAccess<Long>> command = ArgumentCaptor.captor();
    verify(fixture.accessManager()).replace(any(), command.capture(), eq(user), eq(user));
    assertEquals(
        ResourceAccessController.MAX_ASSIGNMENTS + 1, command.getValue().assignments().size());
    assertEquals(
        "audience:all-users",
        command
            .getValue()
            .assignments()
            .get(ResourceAccessController.MAX_ASSIGNMENTS)
            .granteeKey());
    assertThrows(
        UnsupportedOperationException.class,
        () -> command.getValue().assignments().add(new ResourceAccessGrant("user:2", "Viewer")));
  }

  @Test
  void resolvesTheRegisteredResourceAndIfMatchBeforeStrictAssignmentConversion() throws Exception {
    AccessMvc fixture = accessMvc();
    String invalidBody = "{\"assignments\":[{\"granteeKey\":\"user:1\",\"role\":true}]}";

    fixture
        .mockMvc()
        .perform(
            put("/api/v2/unknown/1/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
        .andExpect(status().isNotFound());
    fixture
        .mockMvc()
        .perform(put(ACCESS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(invalidBody))
        .andExpect(status().isPreconditionRequired());
    verify(fixture.accessManager(), never()).replace(any(), any(), any(), any());
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
    stubPage(Collections.emptyList(), 0);

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
    stubPage(List.of(maintenance), 1);

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
  void rejectsInvalidSelectionDepthAndWhere() throws Exception {
    stubPage(List.of(futureMaintenance(2, "Window")), 1);

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
    stubPage(List.of(futureMaintenance(2, "Window")), 1);

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
  void rejectsPermanentDeleteForAResourceThatDidNotOptIn() throws Exception {
    mockMvc
        .perform(
            delete(ENDPOINT + "/42")
                .param("permanent", "true")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.delete.permanent.unsupported"));

    verify(maintenanceManager, never()).removeResource(any(), any());
  }

  @Test
  void bulkCreatesMaintenanceDocumentsInADocsEnvelope() throws Exception {
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
  void bulkOperationsRequireAFilterAndReturnABulkEnvelope() throws Exception {
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

  @Test
  void rejectsOverDepthCrudBodiesBeforeCollectionValidation() throws Exception {
    mockMvc
        .perform(
            patch(ENDPOINT + "/1")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[".repeat(101) + "0" + "]".repeat(101)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
    verifyNoInteractions(maintenanceManager);
  }

  @Test
  void preservesCollectionErrorCodesForExplicitNullBodies() throws Exception {
    mockMvc
        .perform(
            patch(ENDPOINT + "/1")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.maintenance.patch"));
  }

  private static void registerPreHandlerProblems(MockMvc mvc) {
    // Standalone MVC omits WebConfig's resolver for errors raised before handler selection.
    var composite =
        mvc.getDispatcherServlet()
            .getWebApplicationContext()
            .getBean(HandlerExceptionResolverComposite.class);
    var resolvers = new ArrayList<>(composite.getExceptionResolvers());
    resolvers.add(
        0, new ApiV2PreHandlerProblemResolver(problemAdvice(), bodyConverter().getObjectMapper()));
    composite.setExceptionResolvers(resolvers);
  }

  private static MappingJackson2HttpMessageConverter bodyConverter() {
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    converter
        .getObjectMapper()
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    ApiV2JsonMappers.register(converter);
    return converter;
  }

  private AccessMvc accessMvc() {
    ResourceAccessManager accessManager = mock(ResourceAccessManager.class);
    ResourceAccessDirectoryManager directoryManager = mock(ResourceAccessDirectoryManager.class);
    ResourceAccessDocument document =
        new ResourceAccessDocument(
            "maintenances",
            1,
            List.of(),
            new ResourceAccessCallerDocument(
                Optional.empty(),
                List.of(),
                new ResourceAccessCallerCapabilities(false, false, false),
                "user:1"));
    when(accessManager.replace(any(), any(), any(), any())).thenReturn(document);

    ApiV2ResourceSpec<ScheduledMaintenance, Long> protectedMaintenance =
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
            maintenance.runtimeFields(),
            Optional.of(new ResourceAccessSpec<>(mockProtectedResourceAccess())));
    ApiV2ResourceCatalog catalog = new ApiV2ResourceCatalog(List.of(protectedMaintenance));

    MappingJackson2HttpMessageConverter converter = bodyConverter();
    RequestMappingHandlerAdapter adapter = mock(RequestMappingHandlerAdapter.class);
    when(adapter.getMessageConverters()).thenReturn(List.of(converter));
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerSingleton("requestMappingHandlerAdapter", adapter);
    ObjectProvider<RequestMappingHandlerAdapter> adapterProvider =
        beanFactory.getBeanProvider(RequestMappingHandlerAdapter.class);

    MockMvc accessMockMvc =
        MockMvcBuilders.standaloneSetup(
                new ResourceAccessController(
                    catalog, accessManager, directoryManager, adapterProvider))
            .setControllerAdvice(problemAdvice())
            .setMessageConverters(converter, new YamlJackson2HttpMessageConverter())
            .setValidator(validator)
            .build();
    return new AccessMvc(accessMockMvc, accessManager, ApiV2Caller.direct(user));
  }

  @SuppressWarnings("unchecked")
  private static ProtectedResourceAccess<ScheduledMaintenance, Long> mockProtectedResourceAccess() {
    return mock(ProtectedResourceAccess.class);
  }

  private static String assignmentsWithInvalidFinalRole(int validRows) {
    StringBuilder json = new StringBuilder("{\"assignments\":[");
    for (int index = 0; index < validRows; index++) {
      if (index > 0) {
        json.append(',');
      }
      json.append("{\"granteeKey\":\"user:").append(index).append("\",\"role\":\"Editor\"}");
    }
    if (validRows > 0) {
      json.append(',');
    }
    return json.append("{\"granteeKey\":\"user:bad\",\"role\":true}]}").toString();
  }

  private static String assignmentsJson(int namedRows, boolean audience) {
    StringBuilder json = new StringBuilder("{\"assignments\":[");
    for (int index = 0; index < namedRows; index++) {
      if (index > 0) {
        json.append(',');
      }
      json.append("{\"granteeKey\":\"user:").append(index).append("\",\"role\":\"Editor\"}");
    }
    if (audience) {
      if (namedRows > 0) {
        json.append(',');
      }
      json.append("{\"granteeKey\":\"audience:all-users\",\"role\":\"Viewer\"}");
    }
    return json.append("]}").toString();
  }

  private record AccessMvc(
      MockMvc mockMvc, ResourceAccessManager accessManager, ApiV2Caller caller) {}

  private record InvalidAccessBody(String json, String field, String reason) {}

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

  private void stubPage(List<ScheduledMaintenance> results, long total) {
    when(maintenanceManager.getResources(any(ResourceRequest.class), any()))
        .thenReturn(new ResourcePage<>(results, total));
  }

  private static ApiV2ControllerAdvice problemAdvice() {
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage(
        "errors.api.v2.unsupportedMediaType", Locale.ENGLISH, "Unsupported media type.");
    source.addMessage(
        "errors.api.pagination.page.min", Locale.ENGLISH, "Page must be 1 or greater.");
    source.addMessage(
        "errors.api.pagination.limit.min", Locale.ENGLISH, "Limit must be 1 or greater.");
    source.addMessage(
        "errors.api.pagination.limit.max", Locale.ENGLISH, "Limit must not exceed {value}.");
    source.addMessage(
        "errors.api.v2.invalidRequest", Locale.ENGLISH, "The request contains an invalid value.");
    source.addMessage(
        "errors.api.v2.maintenance.patch", Locale.ENGLISH, "The maintenance patch is invalid.");
    source.addMessage(
        "errors.api.v2.resourceAccess.ifMatchRequired",
        Locale.ENGLISH,
        "A strong If-Match header is required.");
    source.addMessage(
        "errors.api.v2.resourceAccess.assignmentLimit", Locale.ENGLISH, "Too many assignments.");
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
    source.addMessage(
        "errors.api.v2.delete.permanent.unsupported",
        Locale.ENGLISH,
        "This resource does not support permanent deletion.");
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

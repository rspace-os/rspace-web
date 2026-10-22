package com.researchspace.api.v2.controller;

import static com.researchspace.api.v2.controller.ApiV2ControllerTestSupport.bodyConverter;
import static com.researchspace.api.v2.controller.ApiV2ControllerTestSupport.jsonValidator;
import static com.researchspace.api.v2.controller.ApiV2ControllerTestSupport.maintenanceSpec;
import static com.researchspace.api.v2.controller.ApiV2ControllerTestSupport.problemAdvice;
import static com.researchspace.api.v2.controller.ApiV2ControllerTestSupport.registerPreHandlerProblems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceAccessSpec;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import com.researchspace.service.resourceaccess.ReplaceResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessCallerCapabilities;
import com.researchspace.service.resourceaccess.ResourceAccessCallerDocument;
import com.researchspace.service.resourceaccess.ResourceAccessDirectoryManager;
import com.researchspace.service.resourceaccess.ResourceAccessDocument;
import com.researchspace.service.resourceaccess.ResourceAccessGrant;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import com.researchspace.webapp.config.WebConfig.YamlJackson2HttpMessageConverter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

class ResourceAccessControllerTest {

  private static final String ACCESS_ENDPOINT = "/api/v2/maintenances/1/access";
  private final MaintenanceManager maintenanceManager = mock(MaintenanceManager.class);
  private final User user = mock(User.class);
  private final LocalValidatorFactoryBean validator = jsonValidator();
  private ApiV2ResourceSpec<ScheduledMaintenance, Long> maintenance;

  @BeforeEach
  void setUp() {
    when(user.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    maintenance = maintenanceSpec(maintenanceManager);
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
}

package com.researchspace.api.v2.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2AuditLog;
import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditData;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiV2AuditControllerTest {

  private final ApiV2AuditStrictSearch strictSearch = mock(ApiV2AuditStrictSearch.class);
  private final ResourceOperations<User, Long> operations = operations();
  private final User actor = mock(User.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    User target = new User("target");
    target.setId(7L);
    when(actor.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    when(operations.find(any(), eq(actor))).thenReturn(new ResourcePage<>(List.of(target), 1));
    ApiV2ResourceSpec<User, Long> spec =
        new ApiV2ResourceSpec<>(
            ApiV2UserResource.DESCRIPTION,
            operations,
            Long::valueOf,
            "create-error",
            "update-error");
    ApiV2AuditController controller =
        new ApiV2AuditController(
            new ApiV2ResourceCatalog(List.of(spec)),
            new ApiV2AuditLog(
                strictSearch,
                Clock.fixed(Instant.parse("2026-01-03T12:00:00Z"), ZoneOffset.UTC),
                100));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void exposesPagedAndCountAuditRoutes() throws Exception {
    HistoricData event =
        new HistoricData(
            AuditDomain.USER,
            AuditAction.WRITE,
            "Audit User",
            AuditData.fromJson("{\"id\":\"US7\"}"),
            "auditor");
    AuditTrailSearchResult result =
        new AuditTrailSearchResult(event, Instant.parse("2026-01-02T12:00:00Z").toEpochMilli());
    when(strictSearch.search(any())).thenReturn(List.of(result, result, result, result));

    mockMvc
        .perform(
            get("/api/v2/users/7/audit")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(actor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs[0].action").value("WRITE"))
        .andExpect(jsonPath("$.totalDocs").value(4))
        .andExpect(jsonPath("$.limit").value(20));

    mockMvc
        .perform(
            get("/api/v2/users/7/audit/count")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(actor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(4));
  }

  private static ResourceOperations<User, Long> operations() {
    return operationsMock();
  }

  @SuppressWarnings("unchecked") // Mockito creates an erased interface mock; ID use is test-owned.
  private static <T> ResourceOperations<T, Long> operationsMock() {
    return (ResourceOperations<T, Long>) mock(ResourceOperations.class);
  }
}

package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.ResourceAccessSpec;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiV2OpenApiResourceAccessDocsTest {

  @Test
  void documentsGranteeSearchContract() {
    JsonNode operation = granteeSearchOperation();
    assertEquals("q", operation.at("/parameters/1/name").asText());
    assertEquals("query", operation.at("/parameters/1/in").asText());
  }

  @SuppressWarnings("unchecked")
  private JsonNode granteeSearchOperation() {
    ApiV2ResourceRegistration<ScheduledMaintenance, Long> resource =
        mock(ApiV2ResourceRegistration.class);
    ProtectedResourceAccess<ScheduledMaintenance, Long> access =
        mock(ProtectedResourceAccess.class);
    when(resource.resourceAccess()).thenReturn(Optional.of(new ResourceAccessSpec<>(access)));
    when(resource.resourceName()).thenReturn("maintenances");
    when(resource.description()).thenReturn(ApiV2MaintenanceResource.DESCRIPTION);
    Map<String, Object> paths = new LinkedHashMap<>();
    new ApiV2OpenApiResourceAccessDocs().addResourceAccessPaths(paths, resource, "Maintenances");
    return new ObjectMapper()
        .valueToTree(paths)
        .at("/~1api~1v2~1maintenances~1{id}~1access~1grantees/get");
  }
}

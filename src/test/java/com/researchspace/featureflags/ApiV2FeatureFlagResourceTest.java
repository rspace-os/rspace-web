package com.researchspace.featureflags;

import static com.researchspace.api.v2.resource.ResourceOperation.UPDATE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.api.v2.resource.ApiV2ResourceException;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.FeatureFlagManager.Patch;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiV2FeatureFlagResourceTest {

  private final FeatureFlagManager manager = mock(FeatureFlagManager.class);
  private final FeatureFlagResourceOperations operations =
      new FeatureFlagResourceOperations(manager);

  @Test
  void passesTheCallerAndValidatedPatchToTheManager() {
    ResourceRequest request = ResourceRequest.unpaged(null);
    User actor = mock(User.class);
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("baselineValue", true);
    values.put("overrideValue", null);
    ParsedDocument document = ParsedDocument.update(values);
    FeatureFlagResource state =
        new FeatureFlagResource(
            "testFlag", true, false, true, FeatureFlagSource.USER_OVERRIDE, true);
    when(manager.getFeatureFlags(actor)).thenReturn(List.of(state));
    when(manager.updateFeatureFlag("testFlag", new Patch(true, true, null), actor, actor))
        .thenReturn(Optional.of(state));

    assertEquals(List.of(state), operations.find(request, actor).resources());
    assertEquals(
        state, operations.update("testFlag", document, ApiV2Caller.direct(actor)).orElseThrow());
  }

  @Test
  void definesUpdateFailuresInTheResourceSpec() {
    ApiV2ResourceSpec<FeatureFlagResource, String> spec = operations.featureFlagApiV2Resource();

    ApiV2ErrorMapping permission = mapping(spec, FeatureFlagPermissionException.class);
    assertEquals(HttpStatus.FORBIDDEN, permission.status());
    assertEquals("api.v2.featureFlags.errors.notPermitted", permission.errorCode());

    ApiV2ErrorMapping readOnly = mapping(spec, FeatureFlagReadOnlyException.class);
    ApiV2ResourceException translated =
        readOnly.translate(new FeatureFlagReadOnlyException("someFlag"));
    assertEquals(HttpStatus.CONFLICT, translated.status());
    assertEquals("api.v2.featureFlags.errors.readOnly", translated.errorCode());
    assertArrayEquals(new Object[] {"someFlag"}, translated.arguments());
  }

  private static ApiV2ErrorMapping mapping(
      ApiV2ResourceSpec<FeatureFlagResource, String> spec,
      Class<? extends RuntimeException> exceptionType) {
    return spec.errorMappings().get(UPDATE).stream()
        .filter(mapping -> mapping.exceptionType().equals(exceptionType))
        .findFirst()
        .orElseThrow();
  }
}

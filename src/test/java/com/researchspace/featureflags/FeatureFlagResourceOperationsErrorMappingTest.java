package com.researchspace.featureflags;

import static com.researchspace.api.v2.resource.ResourceOperation.UPDATE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.api.v2.resource.ApiV2ResourceException;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.service.FeatureFlagManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FeatureFlagResourceOperationsErrorMappingTest {

  @Test
  void definesUpdateFailuresInTheResourceSpec() {
    ApiV2ResourceSpec<FeatureFlagResource, String> spec =
        new FeatureFlagResourceOperations(mock(FeatureFlagManager.class))
            .featureFlagApiV2Resource();

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

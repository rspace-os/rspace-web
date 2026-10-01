package com.researchspace.featureflags;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v2.auth.ApiV2Caller;
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
import org.mockito.Mockito;

class ApiV2FeatureFlagResourceTest {

  private final FeatureFlagManager manager = Mockito.mock(FeatureFlagManager.class);
  private final FeatureFlagResourceOperations operations =
      new FeatureFlagResourceOperations(manager);

  @Test
  void passesTheCallerAndValidatedPatchToTheManager() {
    ResourceRequest request = ResourceRequest.unpaged(null);
    User actor = Mockito.mock(User.class);
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("baselineValue", true);
    values.put("overrideValue", null);
    ParsedDocument document = ParsedDocument.update(values);
    FeatureFlagResource state =
        new FeatureFlagResource(
            "bookingEnabled", true, false, true, FeatureFlagSource.USER_OVERRIDE, true);
    Mockito.when(manager.getFeatureFlags(actor)).thenReturn(List.of(state));
    Mockito.when(
            manager.updateFeatureFlag("bookingEnabled", new Patch(true, true, null), actor, actor))
        .thenReturn(Optional.of(state));

    assertEquals(List.of(state), operations.find(request, actor).resources());
    assertEquals(
        state,
        operations.update("bookingEnabled", document, ApiV2Caller.direct(actor)).orElseThrow());

    Mockito.verify(manager).getFeatureFlags(actor);
    Mockito.verify(manager)
        .updateFeatureFlag("bookingEnabled", new Patch(true, true, null), actor, actor);
  }
}

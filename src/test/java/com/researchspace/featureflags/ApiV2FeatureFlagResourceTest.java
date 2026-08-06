package com.researchspace.featureflags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.CollectionDescription.FieldSchema;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.FeatureFlagManager.Patch;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ApiV2FeatureFlagResourceTest {

  private final FeatureFlagManager manager = Mockito.mock(FeatureFlagManager.class);
  private final FeatureFlagResourceOperations operations =
      new FeatureFlagResourceOperations(manager);

  @Test
  void describesTheCallerSpecificReadModelAndPatchFields() {
    Map<String, FieldSchema> fields =
        ApiV2FeatureFlagResource.DESCRIPTION.schema().fields().stream()
            .collect(Collectors.toMap(FieldSchema::name, Function.identity()));

    assertEquals(
        List.of("name", "value", "baselineValue", "overrideValue", "source", "canOverride"),
        ApiV2FeatureFlagResource.DESCRIPTION.fields().stream().map(field -> field.name()).toList());
    assertTrue(fields.get("name").readOnly());
    assertTrue(fields.get("value").readOnly());
    assertFalse(fields.get("baselineValue").readOnly());
    assertFalse(fields.get("overrideValue").readOnly());
    assertTrue(fields.get("overrideValue").nullable());
    AccessContext read =
        new AccessContext(Mockito.mock(User.class), Operation.READ, "feature-flags");
    assertFalse(ApiV2FeatureFlagResource.DESCRIPTION.fieldReadable("overrideValue", read));
    assertTrue(ApiV2FeatureFlagResource.DESCRIPTION.fieldReadable("baselineValue", read));
    assertEquals(
        List.of("DEFAULT", "DATABASE", "USER_OVERRIDE", "PROPERTIES_FILE"),
        fields.get("source").openApi().enumValues());
  }

  @Test
  void exposesOnlyListCountReadAndSingleUpdate() {
    assertEquals(
        java.util.Set.of(
            ResourceOperation.LIST,
            ResourceOperation.COUNT,
            ResourceOperation.READ,
            ResourceOperation.UPDATE),
        operations.featureFlagApiV2Resource().exposedOperations());
  }

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
    Mockito.when(manager.updateFeatureFlag("bookingEnabled", new Patch(true, true, null), actor))
        .thenReturn(Optional.of(state));

    assertEquals(List.of(state), operations.find(request, actor).resources());
    assertEquals(state, operations.update("bookingEnabled", document, actor).orElseThrow());

    ArgumentCaptor<User> caller = ArgumentCaptor.forClass(User.class);
    Mockito.verify(manager).getFeatureFlags(caller.capture());
    assertEquals(actor, caller.getValue());
    Mockito.verify(manager).updateFeatureFlag("bookingEnabled", new Patch(true, true, null), actor);
  }
}

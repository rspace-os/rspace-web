package com.researchspace.featureflags;

import static com.researchspace.model.collection.ApiV2ResourceField.AccessPreset.AUTHENTICATED;
import static com.researchspace.model.collection.ApiV2ResourceField.AccessPreset.NEVER;

import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ApiV2ResourceDefinition;
import com.researchspace.model.collection.ApiV2ResourceField;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Sort;
import java.util.List;

/** Public field allowlist for the Feature Flags REST v2 collection. */
@ApiV2ResourceDefinition(
    name = "feature-flags",
    entity = FeatureFlagResource.class,
    id = "name",
    auditFields = false)
public record ApiV2FeatureFlagResource(
    @ApiV2ResourceField(
            maxLength = FeatureFlagDefinition.MAX_NAME_LENGTH,
            description = "Stable lower-camel-case feature flag name.",
            example = "bookingEnabled")
        String name,
    @ApiV2ResourceField(description = "Effective value for this caller.") boolean value,
    @ApiV2ResourceField(
            createAccess = NEVER,
            updateAccess = AUTHENTICATED,
            description =
                "Instance value before a user override is applied. Only a real sysadmin can change"
                    + " it.")
        boolean baselineValue,
    @ApiV2ResourceField(
            readAccess = NEVER,
            createAccess = NEVER,
            updateAccess = AUTHENTICATED,
            nullable = true,
            description = "Caller override. Set null to clear the override.")
        Boolean overrideValue,
    @ApiV2ResourceField(
            enumValues = {"DEFAULT", "DATABASE", "USER_OVERRIDE", "PROPERTIES_FILE"},
            description = "Source of the effective value.")
        String source,
    @ApiV2ResourceField(description = "Whether this caller can set a user override.")
        boolean canOverride) {

  private static final AccessPolicy ACCESS =
      new AccessPolicy(
          AccessFunction.anyone(),
          AccessFunction.never(),
          AccessFunction.authenticated(),
          AccessFunction.never(),
          AccessFunction.never());

  public static final CollectionDescription<FeatureFlagResource> DESCRIPTION =
      CollectionDescription.fromApiV2Resource(
          ApiV2FeatureFlagResource.class, List.of(), List.of(new Sort("name", true)), ACCESS);
}

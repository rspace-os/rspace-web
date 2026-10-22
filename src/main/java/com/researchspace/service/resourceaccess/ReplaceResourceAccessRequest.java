package com.researchspace.service.resourceaccess;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Request body for replacing a resource's direct access assignments. */
@Schema(
    name = "ResourceAccessReplacement",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ReplaceResourceAccessRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonSetter(contentNulls = Nulls.FAIL)
        List<ResourceAccessGrant> assignments) {}

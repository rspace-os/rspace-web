package com.researchspace.api.v2.openapi;

import com.researchspace.service.JsonMessageSource;

/** OpenAPI text resolved from the server message catalogs, as the application context does. */
public final class ApiV2OpenApiTestText {
  private static final ApiV2OpenApiText ENGLISH = new ApiV2OpenApiText(new JsonMessageSource());

  private ApiV2OpenApiTestText() {}

  public static ApiV2OpenApiText english() {
    return ENGLISH;
  }
}

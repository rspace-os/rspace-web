package com.researchspace.api.v2.model;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/** JSON:API-style sparse fieldsets bound from {@code fields[type]} and {@code exclude[type]}. */
@Data
public class ApiV2FieldsetQuery {

  private Map<String, String> fields = new LinkedHashMap<>();

  private Map<String, String> exclude = new LinkedHashMap<>();
}

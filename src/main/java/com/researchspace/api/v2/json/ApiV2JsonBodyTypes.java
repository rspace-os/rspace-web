package com.researchspace.api.v2.json;

import java.util.List;

/** Feature-contributed fixed-shape request types requiring strict JSON binding. */
public record ApiV2JsonBodyTypes(List<Class<?>> types) {
  public ApiV2JsonBodyTypes {
    types = List.copyOf(types);
  }
}

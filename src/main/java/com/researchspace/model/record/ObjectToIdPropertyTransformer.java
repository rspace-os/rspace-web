package com.researchspace.model.record;

import com.researchspace.model.core.UniquelyIdentifiable;
import java.util.function.Function;

public class ObjectToIdPropertyTransformer implements Function<UniquelyIdentifiable, Long> {

  @Override
  public Long apply(UniquelyIdentifiable toTransform) {
    return toTransform.getId();
  }
}

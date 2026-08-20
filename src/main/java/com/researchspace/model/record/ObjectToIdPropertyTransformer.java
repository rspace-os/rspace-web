package com.researchspace.model.record;

import java.util.function.Function;

import com.researchspace.model.core.UniquelyIdentifiable;

public class ObjectToIdPropertyTransformer implements Function<UniquelyIdentifiable, Long> {

	@Override
	public Long apply(UniquelyIdentifiable toTransform) {
		return toTransform.getId();
	}
}

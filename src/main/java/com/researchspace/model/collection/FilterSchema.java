package com.researchspace.model.collection;

import java.util.Set;

public record FilterSchema(String selector, Set<Operator> operators, boolean supportsWildcards) {}

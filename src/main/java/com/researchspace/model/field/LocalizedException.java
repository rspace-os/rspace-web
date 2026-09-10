package com.researchspace.model.field;

import java.util.function.BiFunction;

/** Exception whose user-facing text is resolved at an application boundary. */
public interface LocalizedException {

  String resolve(BiFunction<String, Object[], String> resolver);
}

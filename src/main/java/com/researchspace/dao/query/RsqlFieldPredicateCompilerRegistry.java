package com.researchspace.dao.query;

import com.researchspace.model.collection.RuntimeFieldValueType;
import java.util.Map;

/** Explicit storage-semantic mappings. New runtime types must choose a compiler. */
final class RsqlFieldPredicateCompilerRegistry {
  private static final RsqlFieldPredicateCompiler SCALAR = new RsqlScalarPredicateCompiler();
  private static final Map<RuntimeFieldValueType, RsqlFieldPredicateCompiler> RUNTIME =
      Map.of(
          RuntimeFieldValueType.TEXT, SCALAR,
          RuntimeFieldValueType.DATE, SCALAR,
          RuntimeFieldValueType.TIME, SCALAR,
          RuntimeFieldValueType.RADIO, SCALAR,
          RuntimeFieldValueType.NUMBER, new RsqlNumericTextPredicateCompiler(),
          RuntimeFieldValueType.CHOICE, new RsqlChoicePredicateCompiler());

  private RsqlFieldPredicateCompilerRegistry() {}

  static RsqlFieldPredicateCompiler scalar() {
    return SCALAR;
  }

  static RsqlFieldPredicateCompiler runtime(RuntimeFieldValueType type) {
    RsqlFieldPredicateCompiler compiler = RUNTIME.get(type);
    if (compiler == null) {
      throw new IllegalStateException(
          "No predicate compiler registered for runtime field type " + type);
    }
    return compiler;
  }
}

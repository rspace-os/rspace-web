package com.researchspace.dao.query;

import com.researchspace.model.collection.RuntimeFieldValueType;

/** Explicit storage-semantic mappings. New runtime types must choose a compiler. */
final class RsqlFieldPredicateCompilerRegistry {
  private static final RsqlFieldPredicateCompiler SCALAR = new RsqlScalarPredicateCompiler();
  private static final RsqlFieldPredicateCompiler NUMBER = new RsqlNumericTextPredicateCompiler();
  private static final RsqlFieldPredicateCompiler CHOICE = new RsqlChoicePredicateCompiler();

  private RsqlFieldPredicateCompilerRegistry() {}

  static RsqlFieldPredicateCompiler scalar() {
    return SCALAR;
  }

  static RsqlFieldPredicateCompiler runtime(RuntimeFieldValueType type) {
    return switch (type) {
      case TEXT, DATE, TIME, RADIO -> SCALAR;
      case NUMBER -> NUMBER;
      case CHOICE -> CHOICE;
    };
  }
}

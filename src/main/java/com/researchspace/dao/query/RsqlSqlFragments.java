package com.researchspace.dao.query;

import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourceReference;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Builds the individual JPQL fragments the compiler stitches together: comparison symbols, LIKE
 * clauses and their escaping, and the paired kind/id predicate a polymorphic reference needs.
 *
 * <p>Pure string handling. Anything here that binds a value takes the {@link RsqlCompilationState}
 * so the parameter name it generates stays unique across the whole predicate.
 */
final class RsqlSqlFragments {

  private RsqlSqlFragments() {}

  static String jsonQuoted(String value) {
    return JacksonUtil.toJson(value);
  }

  static String referencePair(
      Object value, String kindPath, String idPath, RsqlCompilationState state) {
    if (!(value instanceof ResourceReference<?, ?> reference)) {
      throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
    }
    return "("
        + kindPath
        + " = :"
        + state.add(reference.kind())
        + " AND "
        + idPath
        + " = :"
        + state.add(reference.id())
        + ")";
  }

  static String symbol(Operator operator) {
    return switch (operator) {
      case EQUAL -> "=";
      case NOT_EQUAL -> "<>";
      case GREATER_THAN -> ">";
      case GREATER_THAN_OR_EQUAL -> ">=";
      case LESS_THAN -> "<";
      case LESS_THAN_OR_EQUAL -> "<=";
      default -> throw new CollectionQueryException(CollectionQueryException.Reason.OPERATOR);
    };
  }

  static String wordsLike(String path, String value, RsqlCompilationState state) {
    return Arrays.stream(value.trim().split("\\s+"))
        .map(
            word -> {
              state.recordLikePredicate();
              return like(path, state.add("%" + LikeEscaper.escape(word) + "%"), false);
            })
        .collect(
            Collectors.collectingAndThen(
                Collectors.joining(" AND "), expression -> "(" + expression + ")"));
  }

  static String like(String path, String parameter, boolean negated) {
    return "LOWER("
        + path
        + ") "
        + (negated ? "NOT LIKE" : "LIKE")
        + " LOWER(:"
        + parameter
        + ") ESCAPE '!'";
  }

  // ponytail: '*' is always a wildcard here, so a value containing a literal '*' can never be
  // matched by EQUAL/NOT_EQUAL. Add a '\*' escape convention in RsqlFilterParser if that's needed.
  static String wildcardPattern(String value) {
    return LikeEscaper.escape(value).replace('*', '%');
  }
}

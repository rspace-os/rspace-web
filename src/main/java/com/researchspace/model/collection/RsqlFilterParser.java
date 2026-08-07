package com.researchspace.model.collection;

import static cz.jirutka.rsql.parser.ast.RSQLOperators.EQUAL;
import static cz.jirutka.rsql.parser.ast.RSQLOperators.GREATER_THAN;
import static cz.jirutka.rsql.parser.ast.RSQLOperators.GREATER_THAN_OR_EQUAL;
import static cz.jirutka.rsql.parser.ast.RSQLOperators.IN;
import static cz.jirutka.rsql.parser.ast.RSQLOperators.LESS_THAN;
import static cz.jirutka.rsql.parser.ast.RSQLOperators.LESS_THAN_OR_EQUAL;
import static cz.jirutka.rsql.parser.ast.RSQLOperators.NOT_EQUAL;
import static cz.jirutka.rsql.parser.ast.RSQLOperators.NOT_IN;

import com.researchspace.model.collection.CollectionDescription.Operator;
import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.RSQLParserException;
import cz.jirutka.rsql.parser.ast.AndNode;
import cz.jirutka.rsql.parser.ast.Arity;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.LogicalNode;
import cz.jirutka.rsql.parser.ast.OrNode;
import cz.jirutka.rsql.parser.ast.RSQLOperators;
import cz.jirutka.rsql.parser.ast.RSQLVisitor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses bounded RSQL into a typed, persistence-neutral filter tree. */
public final class RsqlFilterParser {

  static final int MAX_COMPARISONS = CollectionQueryLimits.MAX_COMPARISONS;
  static final int MAX_DEPTH = CollectionQueryLimits.MAX_FILTER_NESTING;
  static final int MAX_ARGUMENTS = CollectionQueryLimits.MAX_ARGUMENTS;

  private static final ComparisonOperator CONTAINS =
      new ComparisonOperator("=contains=", Arity.of(1, 1));
  private static final ComparisonOperator LIKE = new ComparisonOperator("=like=", Arity.of(1, 1));
  private static final ComparisonOperator EXISTS =
      new ComparisonOperator("=exists=", Arity.of(1, 1));
  private static final Map<ComparisonOperator, Operator> OPERATOR_MAPPING =
      Map.ofEntries(
          Map.entry(EQUAL, Operator.EQUAL),
          Map.entry(NOT_EQUAL, Operator.NOT_EQUAL),
          Map.entry(GREATER_THAN, Operator.GREATER_THAN),
          Map.entry(GREATER_THAN_OR_EQUAL, Operator.GREATER_THAN_OR_EQUAL),
          Map.entry(LESS_THAN, Operator.LESS_THAN),
          Map.entry(LESS_THAN_OR_EQUAL, Operator.LESS_THAN_OR_EQUAL),
          Map.entry(IN, Operator.IN),
          Map.entry(NOT_IN, Operator.NOT_IN),
          Map.entry(CONTAINS, Operator.CONTAINS),
          Map.entry(LIKE, Operator.LIKE),
          Map.entry(EXISTS, Operator.EXISTS));
  private static final Set<ComparisonOperator> OPERATORS;

  static {
    Set<ComparisonOperator> operators = new HashSet<>(RSQLOperators.defaultOperators());
    operators.add(CONTAINS);
    operators.add(LIKE);
    operators.add(EXISTS);
    OPERATORS = Set.copyOf(operators);
  }

  private final CollectionDescription<?> description;

  public RsqlFilterParser(CollectionDescription<?> description) {
    this.description = description;
  }

  public FilterExpression parse(String rsql) {
    if (rsql == null || rsql.isBlank()) {
      return null;
    }
    validateNesting(rsql);
    try {
      return new RSQLParser(OPERATORS).parse(rsql).accept(new Visitor(), new State());
    } catch (RSQLParserException ex) {
      throw new CollectionQueryException(CollectionQueryException.Reason.SYNTAX);
    }
  }

  private static void validateNesting(String rsql) {
    int depth = 0;
    char quote = 0;
    boolean escaped = false;
    for (int index = 0; index < rsql.length(); index++) {
      char current = rsql.charAt(index);
      if (escaped) {
        escaped = false;
      } else if (current == '\\') {
        escaped = true;
      } else if (quote != 0) {
        if (current == quote) {
          quote = 0;
        }
      } else if (current == '\'' || current == '"') {
        quote = current;
      } else if (current == '(') {
        if (++depth > MAX_DEPTH) {
          throw new CollectionQueryException(CollectionQueryException.Reason.COMPLEXITY);
        }
      } else if (current == ')') {
        depth--;
      }
    }
  }

  private final class Visitor implements RSQLVisitor<FilterExpression, State> {

    @Override
    public FilterExpression visit(AndNode node, State state) {
      return new FilterExpression.And(visitLogical(node, state));
    }

    @Override
    public FilterExpression visit(OrNode node, State state) {
      return new FilterExpression.Or(visitLogical(node, state));
    }

    @Override
    public FilterExpression visit(ComparisonNode node, State state) {
      state.recordComparison();
      FilterSelector<?> selector = description.requireFilterSelector(node.getSelector());
      Operator operator = toOperator(node.getOperator());
      if (!selector.operators().contains(operator)) {
        throw new CollectionQueryException(CollectionQueryException.Reason.OPERATOR);
      }

      List<String> arguments = node.getArguments();
      state.recordArguments(arguments.size());
      if (operator == Operator.EXISTS) {
        String value = arguments.get(0);
        if (!"true".equals(value) && !"false".equals(value)) {
          throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
        }
        return comparison(selector, operator, List.of(Boolean.valueOf(value)), false);
      }
      if (operator == Operator.IN || operator == Operator.NOT_IN) {
        if (arguments.isEmpty()) {
          throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
        }
        return comparison(selector, operator, parseAll(selector, arguments), false);
      }

      String rawValue = arguments.get(0);
      if ((operator == Operator.LIKE || operator == Operator.CONTAINS)
          && rawValue.trim().isEmpty()) {
        throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
      }
      if (operator == Operator.CONTAINS || operator == Operator.LIKE) {
        state.recordLikeComparison();
        return comparison(selector, operator, List.of(rawValue), false);
      }
      boolean wildcard =
          (operator == Operator.EQUAL || operator == Operator.NOT_EQUAL)
              && selector.supportsWildcards()
              && rawValue.indexOf('*') >= 0;
      // parse() still runs for a wildcard value: it only validates (e.g. max length) and returns
      // the string unchanged, since supportsWildcards() is true only for text fields today.
      if (wildcard) {
        state.recordLikeComparison();
      }
      return comparison(selector, operator, List.of(parse(selector, rawValue)), wildcard);
    }

    private List<FilterExpression> visitLogical(LogicalNode node, State state) {
      state.enter();
      try {
        return node.getChildren().stream().map(child -> child.accept(this, state)).toList();
      } finally {
        state.exit();
      }
    }
  }

  private static FilterExpression.Comparison comparison(
      FilterSelector<?> selector, Operator operator, List<Object> values, boolean wildcard) {
    return new FilterExpression.Comparison(selector.name(), operator, values, wildcard);
  }

  private static Operator toOperator(ComparisonOperator operator) {
    Operator mapped = OPERATOR_MAPPING.get(operator);
    if (mapped == null) {
      throw new CollectionQueryException(CollectionQueryException.Reason.OPERATOR);
    }
    return mapped;
  }

  private static Object parse(FilterSelector<?> selector, String argument) {
    try {
      return selector.parse(argument);
    } catch (RuntimeException ex) {
      throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
    }
  }

  private static List<Object> parseAll(FilterSelector<?> selector, List<String> arguments) {
    List<Object> values = new ArrayList<>(arguments.size());
    arguments.forEach(argument -> values.add(parse(selector, argument)));
    return List.copyOf(values);
  }

  private static final class State {

    private int comparisons;
    private int depth;
    private int arguments;
    private int likeComparisons;

    private void recordComparison() {
      if (++comparisons > MAX_COMPARISONS) {
        throw new CollectionQueryException(CollectionQueryException.Reason.COMPLEXITY);
      }
    }

    private void enter() {
      if (++depth > MAX_DEPTH) {
        throw new CollectionQueryException(CollectionQueryException.Reason.COMPLEXITY);
      }
    }

    private void exit() {
      depth--;
    }

    private void recordArguments(int count) {
      arguments += count;
      if (arguments > MAX_ARGUMENTS) {
        throw new CollectionQueryException(CollectionQueryException.Reason.COMPLEXITY);
      }
    }

    private void recordLikeComparison() {
      if (++likeComparisons > CollectionQueryLimits.MAX_LIKE_COMPARISONS) {
        throw new CollectionQueryException(CollectionQueryException.Reason.COMPLEXITY);
      }
    }
  }
}

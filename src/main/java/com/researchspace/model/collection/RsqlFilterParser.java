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
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.OrNode;
import cz.jirutka.rsql.parser.ast.RSQLOperators;
import cz.jirutka.rsql.parser.ast.RSQLVisitor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private static final String CURRENT_SUBJECT_ALIAS = "me";
  private static final String USERS_RESOURCE = "users";

  static {
    Set<ComparisonOperator> operators = new HashSet<>(RSQLOperators.defaultOperators());
    operators.add(CONTAINS);
    operators.add(LIKE);
    operators.add(EXISTS);
    OPERATORS = Set.copyOf(operators);
  }

  private final CollectionDescription<?> description;
  private final ResourceRegistry registry;
  private final RuntimeFieldContext runtimeFields;
  private final Map<String, ResolvedRuntimeField> resolvedRuntimeFields = new LinkedHashMap<>();
  private final Map<String, String> runtimeRelationships = new LinkedHashMap<>();

  private record TargetRuntimeSelector(
      String resourceName, String relationshipName, String original, String targetSelector) {}

  public RsqlFilterParser(CollectionDescription<?> description) {
    this(description, null);
  }

  /**
   * Parses filters that can also name a field reached through a relationship, such as {@code
   * primary.name}.
   *
   * <p>Resolving that needs the target's own description, which a collection does not hold, so it
   * comes from the registry. Without a registry those selectors stay unknown, which is the safe
   * default for any caller that cannot supply one.
   */
  public RsqlFilterParser(CollectionDescription<?> description, ResourceRegistry registry) {
    this(description, registry, RuntimeFieldContext.empty());
  }

  /**
   * Parses filters that can also name a runtime field, such as {@code customFields.SF104}.
   *
   * <p>Resolution happens here rather than after parsing because the value is parsed with the
   * resolved type. A caller who may not see a definition therefore gets the unknown-field answer
   * before any type-specific error can reveal that the definition exists.
   */
  public RsqlFilterParser(
      CollectionDescription<?> description,
      ResourceRegistry registry,
      RuntimeFieldContext runtimeFields) {
    this.description = description;
    this.registry = registry;
    this.runtimeFields = Objects.requireNonNull(runtimeFields, "Runtime field context");
  }

  public Map<String, ResolvedRuntimeField> resolvedRuntimeFields() {
    return Map.copyOf(resolvedRuntimeFields);
  }

  public Map<String, String> runtimeRelationships() {
    return Map.copyOf(runtimeRelationships);
  }

  private FilterSelector<?> runtimeFieldSelector(String name) {
    if (runtimeFields.isBareNamespace(name)) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    if (!runtimeFields.namespaced(name)) {
      return null;
    }
    ResolvedRuntimeField resolved = resolvedRuntimeFields.get(name);
    if (resolved == null) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    return new FilterSelector.RuntimeField<>(resolved, name);
  }

  /**
   * Resolves {@code <relationship>.<namespace>.<id>} through the target resource's own provider.
   *
   * <p>One hop only, and only onto a relationship this collection already declares, so this stays
   * the same bounded shape as the existing {@code relationship.field} selectors rather than an
   * arbitrary path. A polymorphic relationship is refused: a definition ID identifies one template
   * on one resource, so "the same field on every target" is not a thing that exists.
   */
  private FilterSelector<?> relationshipRuntimeSelector(String name) {
    TargetRuntimeSelector target = targetRuntimeSelector(name);
    if (target == null) {
      return null;
    }
    ResolvedRuntimeField resolved = resolvedRuntimeFields.get(name);
    if (resolved == null) {
      return null;
    }
    return new FilterSelector.RuntimeField<>(resolved, name);
  }

  private FilterSelector<?> relationshipFieldSelector(String name) {
    return registry == null
        ? null
        : registry
            .findRelationshipQueryPath(description.resourceName(), name)
            .map(ResourceRegistry.RelationshipQueryPath::filterSelector)
            .orElse(null);
  }

  public FilterExpression parse(String rsql) {
    if (rsql == null || rsql.isBlank()) {
      return null;
    }
    validateNesting(rsql);
    try {
      Node root = new RSQLParser(OPERATORS).parse(rsql);
      prepareRuntimeFields(root);
      return root.accept(new Visitor(), new State());
    } catch (RSQLParserException ex) {
      throw new CollectionQueryException(CollectionQueryException.Reason.SYNTAX);
    }
  }

  private void prepareRuntimeFields(Node root) {
    resolvedRuntimeFields.clear();
    runtimeRelationships.clear();
    Set<String> own = new java.util.LinkedHashSet<>();
    Map<String, List<TargetRuntimeSelector>> targets = new LinkedHashMap<>();
    collectRuntimeSelectors(root, own, targets);
    resolvedRuntimeFields.putAll(runtimeFields.resolveAll(own));
    targets.forEach(
        (resourceName, selectors) -> {
          Set<String> requested = new java.util.LinkedHashSet<>();
          selectors.forEach(selector -> requested.add(selector.targetSelector()));
          Map<String, ResolvedRuntimeField> resolved =
              runtimeFields.resolveAllOnTarget(resourceName, requested);
          for (TargetRuntimeSelector selector : selectors) {
            ResolvedRuntimeField field = resolved.get(selector.targetSelector());
            if (field != null) {
              resolvedRuntimeFields.put(selector.original(), field);
              runtimeRelationships.put(selector.original(), selector.relationshipName());
            }
          }
        });
  }

  private void collectRuntimeSelectors(
      Node node, Set<String> own, Map<String, List<TargetRuntimeSelector>> targets) {
    if (node instanceof ComparisonNode comparison) {
      String selector = comparison.getSelector();
      if (runtimeFields.namespaced(selector)) {
        own.add(selector);
        return;
      }
      TargetRuntimeSelector target = targetRuntimeSelector(selector);
      if (target != null) {
        targets.computeIfAbsent(target.resourceName(), ignored -> new ArrayList<>()).add(target);
      }
      return;
    }
    if (node instanceof LogicalNode logical) {
      logical.getChildren().forEach(child -> collectRuntimeSelectors(child, own, targets));
    }
  }

  private TargetRuntimeSelector targetRuntimeSelector(String name) {
    if (registry == null || runtimeFields.actor() == null) {
      return null;
    }
    int dot = name.indexOf('.');
    if (dot <= 0 || dot == name.length() - 1) {
      return null;
    }
    CollectionDescription.Relationship<?> relationship =
        description.findRelationship(name.substring(0, dot)).orElse(null);
    if (relationship == null || relationship.targets().size() != 1) {
      return null;
    }
    String resourceName = relationship.targets().get(0).resourceName();
    String targetSelector = name.substring(dot + 1);
    return runtimeFields.namespaceOnTarget(resourceName, targetSelector).isEmpty()
        ? null
        : new TargetRuntimeSelector(resourceName, relationship.name(), name, targetSelector);
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
      FilterSelector<?> runtimeField = runtimeFieldSelector(node.getSelector());
      if (runtimeField == null) {
        runtimeField = relationshipRuntimeSelector(node.getSelector());
      }
      FilterSelector<?> relationshipField =
          runtimeField != null ? null : relationshipFieldSelector(node.getSelector());
      FilterSelector<?> selector =
          runtimeField != null
              ? runtimeField
              : relationshipField != null
                  ? relationshipField
                  : description.requirePublicFilterSelector(node.getSelector());
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

  private Object parse(FilterSelector<?> selector, String argument) {
    try {
      return selector.parse(resolveCurrentSubjectAlias(selector, argument));
    } catch (RuntimeException ex) {
      throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
    }
  }

  private List<Object> parseAll(FilterSelector<?> selector, List<String> arguments) {
    List<Object> values = new ArrayList<>(arguments.size());
    arguments.forEach(argument -> values.add(parse(selector, argument)));
    return List.copyOf(values);
  }

  private String resolveCurrentSubjectAlias(FilterSelector<?> selector, String argument) {
    if (!CURRENT_SUBJECT_ALIAS.equals(argument) || !isUserRelationshipId(selector)) {
      return argument;
    }
    Long subjectId = runtimeFields.actor() == null ? null : runtimeFields.actor().getId();
    return subjectId == null ? argument : subjectId.toString();
  }

  private boolean isUserRelationshipId(FilterSelector<?> selector) {
    if (selector instanceof FilterSelector.RelationshipPart<?> relationshipPart) {
      return relationshipPart.part() == FilterSelector.RelationshipComponent.ID
          && relationshipPart.relationship().targets().stream()
              .allMatch(target -> USERS_RESOURCE.equals(target.resourceName()));
    }
    if (!(selector instanceof FilterSelector.RelationshipProperty<?>) || registry == null) {
      return false;
    }
    ResourceRegistry.RelationshipQueryPath path =
        registry
            .findRelationshipQueryPath(description.resourceName(), selector.name())
            .orElse(null);
    return path != null
        && path.relationship().targets().stream()
            .allMatch(target -> USERS_RESOURCE.equals(target.resourceName()))
        && path.targets().stream()
            .allMatch(target -> path.targetField().equals(target.description().idField()));
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

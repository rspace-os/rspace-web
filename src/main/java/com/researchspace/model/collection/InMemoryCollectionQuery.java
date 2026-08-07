package com.researchspace.model.collection;

import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Applies a typed resource request to a bounded in-memory collection. */
public final class InMemoryCollectionQuery<T> {

  private final CollectionDescription<T> description;

  public InMemoryCollectionQuery(CollectionDescription<T> description) {
    this.description = Objects.requireNonNull(description, "Collection description");
  }

  /** Returns the requested page and the total number of matching resources. */
  public ResourcePage<T> page(Collection<T> resources, ResourceRequest request) {
    List<T> matches = matching(resources, request);
    long offset = ((long) request.page().number() - 1) * request.page().size();
    if (offset >= matches.size()) {
      return new ResourcePage<>(List.of(), matches.size());
    }
    int from = Math.toIntExact(offset);
    int to = (int) Math.min((long) matches.size(), offset + request.page().size());
    return new ResourcePage<>(matches.subList(from, to), matches.size());
  }

  /** Counts the resources that match the request filter. */
  public long count(Collection<T> resources, ResourceRequest request) {
    Objects.requireNonNull(resources, "Resources");
    Objects.requireNonNull(request, "Resource request");
    return resources.stream().filter(resource -> matches(resource, request.filter())).count();
  }

  private List<T> matching(Collection<T> resources, ResourceRequest request) {
    Objects.requireNonNull(resources, "Resources");
    Objects.requireNonNull(request, "Resource request");
    List<T> matches =
        resources.stream().filter(resource -> matches(resource, request.filter())).toList();
    if (request.sort().isEmpty()) {
      return matches;
    }
    List<T> sorted = new ArrayList<>(matches);
    sorted.sort(comparator(request.sort()));
    return List.copyOf(sorted);
  }

  private boolean matches(T resource, FilterExpression filter) {
    if (filter == null) {
      return true;
    }
    if (filter instanceof FilterExpression.And and) {
      return and.children().stream().allMatch(child -> matches(resource, child));
    }
    if (filter instanceof FilterExpression.Or or) {
      return or.children().stream().anyMatch(child -> matches(resource, child));
    }
    FilterExpression.Comparison comparison = (FilterExpression.Comparison) filter;
    Object actual = description.readFilterValue(resource, comparison.field());
    Object expected = comparison.values().get(0);
    Operator operator = comparison.operator();
    if (operator == Operator.EXISTS) {
      return (actual != null) == Boolean.TRUE.equals(expected);
    }
    if (actual == null) {
      return false;
    }
    return switch (operator) {
      case EQUAL ->
          comparison.wildcard()
              ? wildcard(String.valueOf(actual), String.valueOf(expected))
              : Objects.equals(actual, expected);
      case NOT_EQUAL ->
          comparison.wildcard()
              ? !wildcard(String.valueOf(actual), String.valueOf(expected))
              : !Objects.equals(actual, expected);
      case IN -> comparison.values().contains(actual);
      case NOT_IN -> !comparison.values().contains(actual);
      case CONTAINS -> contains(String.valueOf(actual), String.valueOf(expected));
      case LIKE -> wordsLike(String.valueOf(actual), String.valueOf(expected));
      case GREATER_THAN -> compare(actual, expected) > 0;
      case GREATER_THAN_OR_EQUAL -> compare(actual, expected) >= 0;
      case LESS_THAN -> compare(actual, expected) < 0;
      case LESS_THAN_OR_EQUAL -> compare(actual, expected) <= 0;
      case EXISTS -> throw new IllegalStateException("EXISTS was handled before value comparison");
    };
  }

  private Comparator<T> comparator(List<Sort> sorts) {
    Comparator<T> result = (left, right) -> 0;
    for (Sort sort : sorts) {
      Comparator<T> next =
          (left, right) ->
              compareNullable(
                  description.readSortValue(left, sort.field()),
                  description.readSortValue(right, sort.field()),
                  sort.ascending());
      result = result.thenComparing(next);
    }
    return result;
  }

  private static int compareNullable(Object left, Object right, boolean ascending) {
    if (left == null || right == null) {
      int result = left == right ? 0 : left == null ? -1 : 1;
      return ascending ? result : -result;
    }
    int result = compare(left, right);
    return ascending ? result : -result;
  }

  @SuppressWarnings("unchecked") // Class equality above proves both values have the same T.
  private static int compare(Object left, Object right) {
    if (!(left instanceof Comparable<?> comparable) || !left.getClass().isInstance(right)) {
      throw new IllegalStateException("Collection values are not mutually comparable");
    }
    return ((Comparable<Object>) comparable).compareTo(right);
  }

  private static boolean contains(String actual, String expected) {
    return actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
  }

  private static boolean wordsLike(String actual, String expected) {
    return Pattern.compile("\\s+")
        .splitAsStream(expected.trim())
        .allMatch(word -> contains(actual, word));
  }

  private static boolean wildcard(String actual, String expected) {
    StringBuilder regex = new StringBuilder("^");
    String[] parts = expected.split("\\*", -1);
    for (int index = 0; index < parts.length; index++) {
      if (index > 0) {
        regex.append(".*");
      }
      regex.append(Pattern.quote(parts[index]));
    }
    return Pattern.compile(regex.append('$').toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
        .matcher(actual)
        .matches();
  }
}

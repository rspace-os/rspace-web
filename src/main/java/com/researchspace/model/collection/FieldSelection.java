package com.researchspace.model.collection;

import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable inclusive, exclusive, or unrestricted field selection. */
public record FieldSelection(Mode mode, Set<String> fields) {

  public enum Mode {
    ALL,
    INCLUDE,
    EXCLUDE
  }

  public FieldSelection {
    fields = Set.copyOf(fields);
    if (mode == Mode.ALL && !fields.isEmpty()) {
      throw new IllegalArgumentException("Unrestricted selection must not list fields");
    }
    if (mode != Mode.ALL && fields.isEmpty()) {
      throw new IllegalArgumentException("Restricted selection must list fields");
    }
  }

  public static FieldSelection all() {
    return new FieldSelection(Mode.ALL, Set.of());
  }

  public static FieldSelection include(Set<String> fields) {
    return new FieldSelection(Mode.INCLUDE, fields);
  }

  public static FieldSelection exclude(Set<String> fields) {
    return new FieldSelection(Mode.EXCLUDE, fields);
  }

  public boolean includes(String field, String idField) {
    return idField.equals(field)
        || mode == Mode.ALL
        || (mode == Mode.INCLUDE && fields.contains(field))
        || (mode == Mode.EXCLUDE && !fields.contains(field));
  }

  /** Returns fields permitted by both selections, retaining the resource identifier. */
  public FieldSelection intersect(FieldSelection other, String idField) {
    if (mode == Mode.ALL) {
      return other;
    }
    if (other.mode == Mode.ALL) {
      return this;
    }
    if (mode == Mode.EXCLUDE && other.mode == Mode.EXCLUDE) {
      Set<String> excluded = new LinkedHashSet<>(fields);
      excluded.addAll(other.fields);
      return exclude(excluded);
    }
    if (mode == Mode.INCLUDE && other.mode == Mode.INCLUDE) {
      Set<String> included = new LinkedHashSet<>(fields);
      included.retainAll(other.fields);
      return include(included.isEmpty() ? Set.of(idField) : included);
    }

    Set<String> included = new LinkedHashSet<>(mode == Mode.INCLUDE ? fields : other.fields);
    Set<String> excluded = mode == Mode.EXCLUDE ? fields : other.fields;
    included.removeAll(excluded);
    return include(included.isEmpty() ? Set.of(idField) : included);
  }
}

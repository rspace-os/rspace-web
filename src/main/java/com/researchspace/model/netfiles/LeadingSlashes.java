package com.researchspace.model.netfiles;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * How many slashes a filesystem path starts with: what its normalised form ({@link
 * NfsFieldLink#normalisePath}) leaves out, and what can make a client open another file.
 */
public enum LeadingSlashes {
  NONE,
  ONE,
  MORE;

  public static LeadingSlashes of(String path) {
    return path.startsWith("//") ? MORE : path.startsWith("/") ? ONE : NONE;
  }

  /** The normalised path spelled with these leading slashes. */
  public String spell(String normalisedPath) {
    return "/".repeat(ordinal()) + normalisedPath;
  }

  /**
   * An HQL condition, starting with {@code and}, keeping the paths that {@code pathExpression}
   * spells with one of {@code spellings}; empty when that is every spelling.
   */
  public static String hqlCondition(String pathExpression, Set<LeadingSlashes> spellings) {
    if (spellings == null || spellings.size() == values().length) {
      return "";
    }
    return spellings.stream()
        .map(spelling -> spelling.hql(pathExpression))
        .collect(Collectors.joining(" or ", " and (", ")"));
  }

  private String hql(String path) {
    return switch (this) {
      case NONE -> path + " not like '/%'";
      case ONE -> "(" + path + " like '/%' and " + path + " not like '//%')";
      case MORE -> path + " like '//%'";
    };
  }
}

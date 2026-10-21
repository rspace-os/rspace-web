package com.researchspace.dao.query;

/** Escapes values used with SQL {@code LIKE ... ESCAPE '!'} expressions. */
public final class LikeEscaper {

  private LikeEscaper() {}

  public static String escape(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}

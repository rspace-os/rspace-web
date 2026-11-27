package com.researchspace.model.record;

/**
 * The character that document, folder and notebook names must not contain. A name typed by a user
 * (UI rename, new notebook, the API) is rejected if it contains {@link #INVALID_CHARACTER}. A name
 * taken from an import (protocols.io) has every {@link #INVALID_CHARACTER} replaced with {@link
 * #REPLACEMENT}, so the import never fails on its name; for example "BA.2/BA.3" becomes
 * "BA.2-BA.3".
 */
public final class RecordNames {

  public static final String INVALID_CHARACTER = "/";
  public static final String REPLACEMENT = "-";

  private RecordNames() {}

  /**
   * @return true if the name contains {@link #INVALID_CHARACTER}; false for null
   */
  public static boolean hasInvalidCharacters(String name) {
    return name != null && name.contains(INVALID_CHARACTER);
  }

  /**
   * @return the name with every {@link #INVALID_CHARACTER} replaced by {@link #REPLACEMENT}; null
   *     for null
   */
  public static String replaceInvalidCharacters(String name) {
    return name == null ? null : name.replace(INVALID_CHARACTER, REPLACEMENT);
  }
}

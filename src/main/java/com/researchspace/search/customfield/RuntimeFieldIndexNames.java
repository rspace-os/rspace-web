package com.researchspace.search.customfield;

import com.researchspace.model.collection.RuntimeFieldNamespaces;

/**
 * The index field naming shared by the writer and the reader.
 *
 * <p>One index field per definition, named for the namespace and the definition's own stable ID. A
 * single flattened field holding every value would match text found in *any* field of an item, so
 * filtering "hazard class contains BSL" would hit an item whose unrelated notes field mentions BSL.
 * Per-definition naming is what keeps a filter about the field it names.
 *
 * <p>The namespace is part of the name because two providers can issue the same ID shape for
 * different things, and because it lets one glob cover every runtime field a resource publishes.
 */
public final class RuntimeFieldIndexNames {

  public static final String VALUE_PREFIX = "rtFieldValue_";

  public static final String VALUE_TEMPLATE = "runtimeFieldValueTemplate";

  public static final String VALUE_GLOB = VALUE_PREFIX + "*";

  private RuntimeFieldIndexNames() {}

  /**
   * The index field for one definition, or null when either part is not a safe name.
   *
   * <p>Both parts are server-owned: a namespace is a constant of {@link RuntimeFieldNamespaces} and
   * an ID is issued by a provider. The check is here anyway because the result becomes a Lucene
   * field name, and a provider bug should cost the index rather than corrupt the schema.
   */
  public static String valueField(String namespace, String definitionId) {
    if (!safe(namespace) || !safe(definitionId)) {
      return null;
    }
    return VALUE_PREFIX + namespace + "_" + definitionId;
  }

  /**
   * The index field for a {@code namespace.id} selector, or null when it is not one.
   *
   * <p>The selector is what a resolved field carries, so a reader never has to know which provider
   * issued the definition it is narrowing.
   */
  public static String fieldForSelector(String selector) {
    int separator = selector == null ? -1 : selector.indexOf('.');
    if (separator <= 0 || separator == selector.length() - 1) {
      return null;
    }
    return valueField(selector.substring(0, separator), selector.substring(separator + 1));
  }

  private static boolean safe(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    for (int index = 0; index < value.length(); index++) {
      if (!Character.isLetterOrDigit(value.charAt(index))) {
        return false;
      }
    }
    return true;
  }
}

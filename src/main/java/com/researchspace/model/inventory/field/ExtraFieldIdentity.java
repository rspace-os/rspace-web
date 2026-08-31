package com.researchspace.model.inventory.field;

import com.researchspace.model.field.FieldType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/**
 * The stable identity of an ad-hoc extra-field definition: its exact name and its declared type.
 *
 * <p>An extra field has no shared definition row, so two rows named {@code Voltage} on two items
 * are "the same field" only by name and type. That pair is the identity, encoded reversibly:
 * resolving a selector has to recover the name to build the query, and a hash would force an
 * unindexable comparison. Hexadecimal rather than a denser encoding because it uses only characters
 * every layer between here and the client accepts unescaped: the RSQL selector grammar, a
 * comma-separated ID list, a URL path, and a Lucene field name.
 *
 * <p>Shared rather than owned by the provider because the search index writes values under these
 * IDs while the provider reads them. A second copy of the encoding would let the two drift, and the
 * only symptom would be an index that silently never matches.
 */
public final class ExtraFieldIdentity {

  /**
   * Most characters of a name this API will publish.
   *
   * <p>The name is carried inside the selector, and a selector goes in a {@code where} expression
   * whose length is bounded. A longer name is excluded rather than truncated, because a truncated
   * name is a different definition that would silently match the wrong fields.
   */
  public static final int MAX_NAME_LENGTH = 128;

  private static final String ID_PREFIX = "XF";

  private static final Map<FieldType, Character> TYPE_CODES =
      Map.of(FieldType.TEXT, 't', FieldType.NUMBER, 'n', FieldType.LINK, 'l');

  private static final Map<Character, FieldType> TYPES_BY_CODE =
      Map.of('t', FieldType.TEXT, 'n', FieldType.NUMBER, 'l', FieldType.LINK);

  /** One definition: an exact name and the type it was declared with. */
  public record Definition(String name, FieldType type) {}

  private ExtraFieldIdentity() {}

  public static Set<FieldType> publishedTypes() {
    return TYPE_CODES.keySet();
  }

  /**
   * The ID for one definition, or null when it cannot be published.
   *
   * @return null for an unpublished type, a blank name or a name over {@link #MAX_NAME_LENGTH}, so
   *     a caller writing index values or building a catalog skips it the same way
   */
  public static String encode(String name, FieldType type) {
    Character code = type == null ? null : TYPE_CODES.get(type);
    if (code == null || name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
      return null;
    }
    return ID_PREFIX + code + HexFormat.of().formatHex(name.getBytes(StandardCharsets.UTF_8));
  }

  public static Definition decode(String id) {
    if (id == null || id.length() < ID_PREFIX.length() + 1 || !id.startsWith(ID_PREFIX)) {
      return null;
    }
    FieldType type = TYPES_BY_CODE.get(id.charAt(ID_PREFIX.length()));
    if (type == null) {
      return null;
    }
    String hex = id.substring(ID_PREFIX.length() + 1);
    if (hex.length() % 2 != 0) {
      return null;
    }
    try {
      String name = new String(HexFormat.of().parseHex(hex), StandardCharsets.UTF_8);
      return name.isEmpty() || name.length() > MAX_NAME_LENGTH ? null : new Definition(name, type);
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }
}

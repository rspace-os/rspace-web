package com.researchspace.model.inventory.field;

import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.field.FieldType;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
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

  private static final String ID_PREFIX = "XF";

  public enum PublishedType {
    TEXT(FieldType.TEXT, 't', ExtraTextField.class, RuntimeFieldValueType.TEXT),
    NUMBER(FieldType.NUMBER, 'n', ExtraNumberField.class, RuntimeFieldValueType.NUMBER),
    LINK(FieldType.LINK, 'l', ExtraLinkField.class, RuntimeFieldValueType.TEXT);

    private final FieldType fieldType;
    private final char code;
    private final Class<? extends ExtraField> entityType;
    private final RuntimeFieldValueType runtimeType;

    PublishedType(
        FieldType fieldType,
        char code,
        Class<? extends ExtraField> entityType,
        RuntimeFieldValueType runtimeType) {
      this.fieldType = fieldType;
      this.code = code;
      this.entityType = entityType;
      this.runtimeType = runtimeType;
    }

    public FieldType fieldType() {
      return fieldType;
    }

    public char code() {
      return code;
    }

    public Class<? extends ExtraField> entityType() {
      return entityType;
    }

    public RuntimeFieldValueType runtimeType() {
      return runtimeType;
    }

    public static PublishedType fromFieldType(FieldType type) {
      for (PublishedType published : values()) {
        if (published.fieldType == type) return published;
      }
      return null;
    }

    public static PublishedType fromCode(char code) {
      for (PublishedType published : values()) {
        if (published.code == code) return published;
      }
      return null;
    }
  }

  /** One definition: an exact name and the type it was declared with. */
  public record Definition(String name, FieldType type) {}

  private ExtraFieldIdentity() {}

  public static Set<FieldType> publishedTypes() {
    return Set.of(
        PublishedType.TEXT.fieldType, PublishedType.NUMBER.fieldType, PublishedType.LINK.fieldType);
  }

  /**
   * The ID for one definition, or null when it cannot be published.
   *
   * @return null for an unpublished type or a blank name
   */
  public static String encode(String name, FieldType type) {
    PublishedType published = PublishedType.fromFieldType(type);
    if (published == null || name == null || name.isEmpty()) {
      return null;
    }
    return ID_PREFIX
        + published.code
        + HexFormat.of().formatHex(name.getBytes(StandardCharsets.UTF_8));
  }

  public static Definition decode(String id) {
    if (id == null || id.length() < ID_PREFIX.length() + 1 || !id.startsWith(ID_PREFIX)) {
      return null;
    }
    PublishedType published = PublishedType.fromCode(id.charAt(ID_PREFIX.length()));
    if (published == null) {
      return null;
    }
    String hex = id.substring(ID_PREFIX.length() + 1);
    if (hex.length() % 2 != 0) {
      return null;
    }
    try {
      String name = decodeUtf8(HexFormat.of().parseHex(hex));
      return name.isEmpty() ? null : new Definition(name, published.fieldType);
    } catch (IllegalArgumentException | CharacterCodingException malformed) {
      return null;
    }
  }

  private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString();
  }
}

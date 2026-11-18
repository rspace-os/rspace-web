package com.researchspace.model.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.researchspace.model.field.FieldForm;
import java.io.IOException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One field in a {@code FormManager.saveFields} command: the full, ordered field list of the form
 * being edited. A field with an id is a saved field of that form; a field without one is created.
 * Saved fields the list omits are deleted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize(using = FormFieldSaveItem.Deserializer.class)
public class FormFieldSaveItem {

  /** The saved field's id in the form being edited; null for a new field. */
  private Long id;

  /** The field type, as {@code FieldType#getType()} spells it, for example {@code Radio}. */
  private String type;

  /**
   * Optional on a new field (a lowercase UUID unused in the form; one is generated when absent). On
   * a saved field it may be omitted, but must otherwise equal the stored key.
   */
  private String fieldKey;

  /** The field's display rule, or null when it is always shown. */
  private FieldDisplayRuleDTO displayRule;

  /** The field's settings; for the form field editor, the legacy DTO for {@link #type}. */
  private FormFieldSource<? extends FieldForm> source;

  /**
   * When true, a saved field keeps its stored rule and {@link #displayRule} is ignored. Set by the
   * forms API when a request omits the rule; never read from JSON.
   */
  @JsonIgnore private boolean keepStoredDisplayRule;

  public FormFieldSaveItem(
      Long id,
      String type,
      String fieldKey,
      FieldDisplayRuleDTO displayRule,
      FormFieldSource<? extends FieldForm> source) {
    this(id, type, fieldKey, displayRule, source, false);
  }

  /** Reads {@code source} as the legacy DTO class that {@code type} names. */
  static class Deserializer extends JsonDeserializer<FormFieldSaveItem> {
    @Override
    public FormFieldSaveItem deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      ObjectCodec codec = parser.getCodec();
      JsonNode node = codec.readTree(parser);
      FormFieldSaveItem item = new FormFieldSaveItem();
      if (node.hasNonNull("id")) {
        if (!node.get("id").canConvertToLong() || !node.get("id").isIntegralNumber()) {
          return context.reportInputMismatch(FormFieldSaveItem.class, "Field id must be a number");
        }
        item.setId(node.get("id").asLong());
      }
      item.setType(textOrNull(node, "type"));
      item.setFieldKey(textOrNull(node, "fieldKey"));
      if (node.hasNonNull("displayRule")) {
        item.setDisplayRule(codec.treeToValue(node.get("displayRule"), FieldDisplayRuleDTO.class));
      }
      Class<? extends AbstractFormFieldDTO<?>> sourceClass =
          LegacyFormFieldDTOs.dtoClassFor(item.getType());
      if (sourceClass != null && node.hasNonNull("source")) {
        item.setSource(codec.treeToValue(node.get("source"), sourceClass));
      }
      return item;
    }

    private static String textOrNull(JsonNode node, String property) {
      return node.hasNonNull(property) ? node.get(property).asText() : null;
    }
  }
}

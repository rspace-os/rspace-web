package com.researchspace.model.inventory.field;

import com.researchspace.model.field.rule.FieldDisplayRule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link FieldDisplayRule} as its JSON text.
 *
 * <p>A stored rule that no longer parses is read as no rule, so the field is shown and its record
 * still loads. Nothing is logged here: the converter cannot see which field it is reading, and the
 * parser's message may quote the stored text. {@link InventoryEntityField} reports the field's id
 * instead, when it is loaded. Rules are validated before they are saved, so this only guards
 * against data changed outside the application.
 *
 * <p>The unreadable text stays in the database until the field's row is next written. Hibernate
 * writes every column of a changed row, and this field's value is then null, so any later change to
 * the field (its value, name, or anything else) replaces the unreadable rule with NULL. Loading or
 * reading the field alone leaves it as it is.
 */
@Converter
public class FieldDisplayRuleConverter implements AttributeConverter<FieldDisplayRule, String> {

  @Override
  public String convertToDatabaseColumn(FieldDisplayRule rule) {
    return rule == null ? null : rule.toJson();
  }

  @Override
  public FieldDisplayRule convertToEntityAttribute(String json) {
    if (json == null) {
      return null;
    }
    try {
      return FieldDisplayRule.fromJson(json);
    } catch (IllegalArgumentException e) {
      // reported with the field's id by InventoryEntityField once loaded; see the class comment
      return null;
    }
  }
}

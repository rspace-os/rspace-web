package com.researchspace.model.inventory.field;

import com.researchspace.model.field.rule.FieldDisplayRule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * Stores a {@link FieldDisplayRule} as its JSON text.
 *
 * <p>A stored rule that no longer parses is read as no rule, with a warning, so the field is shown
 * and its record still loads. Rules are validated before they are saved, so this only guards
 * against data changed outside the application.
 */
@Converter
@Slf4j
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
      log.warn("Ignoring a stored display rule that cannot be read: {}", e.getMessage());
      return null;
    }
  }
}
